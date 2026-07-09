package com.example.llamaproxy.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/proxy")
@EnableScheduling
public class TargetServerController {

    private final String targetServerUrl;
    private final String restartScript;
    private final String installDir;
    private final String releaseRegex;
    private final RestClient restClient;
    private final com.example.llamaproxy.pipeline.NotificationService notificationService;
    private final AtomicBoolean serverHealthy = new AtomicBoolean(false);

    public TargetServerController(
            @Value("${target.server.url}") String targetServerUrl,
            @Value("${target.server.restart-script}") String restartScript,
            @Value("${llama.cpp.install.dir}") String installDir,
            @Value("${llama.cpp.release.regex}") String releaseRegex,
            com.example.llamaproxy.pipeline.NotificationService notificationService) {
        this.targetServerUrl = targetServerUrl;
        this.restartScript = restartScript;
        this.installDir = installDir;
        this.releaseRegex = releaseRegex;
        this.notificationService = notificationService;

        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
        org.springframework.http.client.JdkClientHttpRequestFactory factory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Scheduled(fixedRate = 5000)
    public void checkHealth() {
        try {
            // Strip /v1 to check health endpoint
            String baseUrl = targetServerUrl.endsWith("/v1") 
                ? targetServerUrl.substring(0, targetServerUrl.length() - 3) 
                : targetServerUrl;
                
            ResponseEntity<String> response = restClient.get()
                .uri(baseUrl + "/health?include_slots=true")
                .retrieve()
                .toEntity(String.class);
            serverHealthy.set(response.getStatusCode().is2xxSuccessful());
        } catch (Exception e) {
            serverHealthy.set(false);
        }
    }

    @GetMapping("/health")
    public boolean getHealth() {
        return serverHealthy.get();
    }

    @GetMapping("/target-url")
    public String getTargetUrl() {
        return targetServerUrl;
    }

    private void killProcessOnPort(int port) {
        try {
            Process process = new ProcessBuilder("cmd.exe", "/c", "netstat -ano | findstr :" + port).start();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("LISTENING")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length > 4) {
                        String pid = parts[parts.length - 1];
                        System.out.println("Killing process with PID: " + pid + " on port: " + port);
                        new ProcessBuilder("cmd.exe", "/c", "taskkill /F /PID " + pid).start().waitFor();
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            System.err.println("Failed to kill process on port " + port + ": " + e.getMessage());
        }
    }

    @PostMapping("/restart-target")
    public ResponseEntity<String> restartTarget() {
        try {
            // Extract port from targetServerUrl
            java.net.URL url = new java.net.URL(targetServerUrl);
            int port = url.getPort();
            if (port != -1) {
                killProcessOnPort(port);
            }
            
            // Wait a second for OS to release port
            Thread.sleep(1000);

            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", restartScript);
            java.io.File scriptFile = new java.io.File(restartScript);
            if (scriptFile.exists() && scriptFile.getParentFile() != null) {
                pb.directory(scriptFile.getParentFile());
            }
            pb.redirectErrorStream(true);
            pb.redirectOutput(new java.io.File("target-server.log"));
            pb.start();
            return ResponseEntity.ok("Restart command issued.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to execute restart script: " + e.getMessage());
        }
    }

    @PostMapping("/kill-target")
    public ResponseEntity<String> killTarget() {
        try {
            java.net.URL url = new java.net.URL(targetServerUrl);
            int port = url.getPort();
            if (port != -1) {
                killProcessOnPort(port);
                return ResponseEntity.ok("Kill command issued for port " + port);
            }
            return ResponseEntity.badRequest().body("Could not determine port from target URL.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to execute kill command: " + e.getMessage());
        }
    }

    @GetMapping(value = "/target-logs-stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamTargetLogs() {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(Long.MAX_VALUE);
        Thread.startVirtualThread(() -> {
            try {
                java.io.File logFile = new java.io.File("target-server.log");
                
                long lastKnownPosition = 0;
                if (logFile.exists()) {
                    long length = logFile.length();
                    lastKnownPosition = length > 50000 ? length - 50000 : 0;
                } else {
                    java.util.Map<String, String> payload = new java.util.HashMap<>();
                    payload.put("type", "INITIAL");
                    payload.put("data", "Log file not found. Have you restarted the server yet?\n");
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("log").data(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload)));
                }

                while (true) {
                    if (logFile.exists()) {
                        long length = logFile.length();
                        if (length < lastKnownPosition) {
                            // File was truncated or recreated
                            lastKnownPosition = 0;
                        }
                        if (length > lastKnownPosition) {
                            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(logFile, "r");
                            raf.seek(lastKnownPosition);
                            byte[] bytes = new byte[(int) (length - lastKnownPosition)];
                            raf.readFully(bytes);
                            raf.close();
                            lastKnownPosition = length;
                            
                            String newLogs = new String(bytes);
                            java.util.Map<String, String> payload = new java.util.HashMap<>();
                            payload.put("type", "APPEND");
                            payload.put("data", newLogs);
                            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("log").data(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload)));
                        }
                    }
                    Thread.sleep(500);
                }
            } catch (Exception e) {
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {}
            }
        });
        return emitter;
    }

    @PostMapping("/update-llama")
    public ResponseEntity<String> updateLlama() {
        try {
            // 1. Fetch latest release from GitHub
            String repo = "ggml-org/llama.cpp";
            String apiUrl = "https://api.github.com/repos/" + repo + "/releases/latest";

            String jsonResponse = restClient.get()
                    .uri(apiUrl)
                    .retrieve()
                    .body(String.class);

            if (jsonResponse == null) {
                return ResponseEntity.internalServerError().body("Failed to fetch release data from GitHub.");
            }

            com.fasterxml.jackson.databind.JsonNode releaseData = new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonResponse);

            // 2. Find asset matching regex
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(releaseRegex);
            String downloadUrl = null;
            String filename = null;

            com.fasterxml.jackson.databind.JsonNode assets = releaseData.get("assets");
            if (assets != null && assets.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode asset : assets) {
                    String assetName = asset.get("name").asText();
                    if (pattern.matcher(assetName).matches()) {
                        downloadUrl = asset.get("browser_download_url").asText();
                        filename = assetName;
                        break;
                    }
                }
            }

            if (downloadUrl == null) {
                return ResponseEntity.status(404).body("Error: Asset matching regex '" + releaseRegex + "' not found in latest release.");
            }

            // 3. Ensure install directory exists
            java.io.File installDirectory = new java.io.File(installDir);
            if (!installDirectory.exists() && !installDirectory.mkdirs()) {
                return ResponseEntity.internalServerError().body("Failed to create install directory: " + installDir);
            }

            // 4. Download file
            java.io.File downloadedZip = new java.io.File(installDirectory, filename);
            System.out.println("Downloading " + filename + " from " + downloadUrl + "...");
            
            byte[] zipBytes = restClient.get()
                    .uri(downloadUrl)
                    .retrieve()
                    .body(byte[].class);

            if (zipBytes == null) {
                return ResponseEntity.internalServerError().body("Failed to download file payload.");
            }

            java.nio.file.Files.write(downloadedZip.toPath(), zipBytes);
            System.out.println("Download complete. Extracting to " + installDirectory.getAbsolutePath() + "...");

            // 5. Unzip the file
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
                java.util.zip.ZipEntry zipEntry = zis.getNextEntry();
                while (zipEntry != null) {
                    java.io.File newFile = new java.io.File(installDirectory, zipEntry.getName());
                    // mitigate zip slip vulnerability
                    if (!newFile.getCanonicalPath().startsWith(installDirectory.getCanonicalPath() + java.io.File.separator)) {
                        throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
                    }
                    if (zipEntry.isDirectory()) {
                        if (!newFile.isDirectory() && !newFile.mkdirs()) {
                            throw new IOException("Failed to create directory " + newFile);
                        }
                    } else {
                        // fix for Windows-created archives
                        java.io.File parent = newFile.getParentFile();
                        if (!parent.isDirectory() && !parent.mkdirs()) {
                            throw new IOException("Failed to create directory " + parent);
                        }
                        
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(newFile)) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                    zipEntry = zis.getNextEntry();
                }
                zis.closeEntry();
            }

            // 6. Delete downloaded zip to clean up
            downloadedZip.delete();

            // 7. Save version to version.txt
            String latestTag = releaseData.get("tag_name").asText();
            java.io.File versionFile = new java.io.File(installDirectory, "version.txt");
            java.nio.file.Files.writeString(versionFile.toPath(), latestTag);

            return ResponseEntity.ok("Successfully updated llama.cpp to latest version (" + latestTag + ") in " + installDir);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error during update: " + e.getMessage());
        }
    }

    @Scheduled(initialDelay = 0, fixedRate = 3600000) // Run on startup and every hour
    @GetMapping("/check-update")
    public ResponseEntity<?> checkUpdate() {
        try {
            // Fetch latest release from GitHub
            String repo = "ggml-org/llama.cpp";
            String apiUrl = "https://api.github.com/repos/" + repo + "/releases/latest";

            String jsonResponse = restClient.get()
                    .uri(apiUrl)
                    .retrieve()
                    .body(String.class);

            if (jsonResponse == null) {
                return ResponseEntity.internalServerError().body(java.util.Map.of("error", "Failed to fetch release data"));
            }

            com.fasterxml.jackson.databind.JsonNode releaseData = new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonResponse);

            if (!releaseData.has("tag_name")) {
                return ResponseEntity.internalServerError().body(java.util.Map.of("error", "Failed to find tag_name in release data"));
            }

            String latestTag = releaseData.get("tag_name").asText();
            String currentTag = "unknown";

            java.io.File versionFile = new java.io.File(installDir, "version.txt");
            if (versionFile.exists()) {
                currentTag = java.nio.file.Files.readString(versionFile.toPath()).trim();
            }

            boolean updateAvailable = !latestTag.equals(currentTag);

            if (updateAvailable && !notificationService.hasUnreadNotification("updater")) {
                com.example.llamaproxy.pipeline.NotificationDTO n = new com.example.llamaproxy.pipeline.NotificationDTO();
                n.setSourcePlugin("updater");
                n.setTitle("Llama Update Available");
                n.setMessage("Version " + latestTag + " is available to download.");
                n.setLevel("info");
                
                String releaseUrl = "https://github.com/" + repo + "/releases/latest";
                n.setActions(java.util.List.of(
                    new com.example.llamaproxy.pipeline.NotificationDTO.NotificationAction("Update Now", "/api/proxy/update-llama", null, null, true),
                    new com.example.llamaproxy.pipeline.NotificationDTO.NotificationAction("Read Release Notes", null, releaseUrl, null, false)
                ));
                notificationService.addNotification(n);
            }

            return ResponseEntity.ok(java.util.Map.of(
                    "updateAvailable", updateAvailable,
                    "latestVersion", latestTag,
                    "currentVersion", currentTag
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(java.util.Map.of("error", e.getMessage()));
        }
    }
}

package io.github.dbarciela.aura.controller;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.github.dbarciela.aura.config.ProxySettings;
import io.github.dbarciela.aura.pipeline.NotificationDTO;
import io.github.dbarciela.aura.pipeline.NotificationService;

@RestController
@RequestMapping("/api/proxy")
@EnableScheduling
public class TargetServerController {

	private final String targetServerUrl;
	private final String restartScript;
	private final String installDir;
	private final String releaseRegex;
	private final RestClient restClient;
	private final NotificationService notificationService;
	private final ProxySettings proxySettings;
	private final AtomicBoolean serverHealthy = new AtomicBoolean(false);
	private static final Logger log = LoggerFactory.getLogger(TargetServerController.class);
	private final HttpClient healthHttpClient;
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public TargetServerController(@Value("${target.server.url}") String targetServerUrl,
			@Value("${target.server.restart-script}") String restartScript,
			@Value("${llama.cpp.install.dir}") String installDir,
			@Value("${llama.cpp.release.regex}") String releaseRegex,
			NotificationService notificationService,
			ProxySettings proxySettings) {
		this.targetServerUrl = targetServerUrl;
		this.restartScript = restartScript;
		this.installDir = installDir;
		this.releaseRegex = releaseRegex;
		this.notificationService = notificationService;
		this.proxySettings = proxySettings;

		HttpClient httpClient = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				httpClient);
		factory.setReadTimeout(3000); // 3 seconds timeout to prevent blocking scheduled tasks
		this.restClient = RestClient.builder().requestFactory(factory).build();
		
		this.healthHttpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(3))
				.build();
	}

	@Scheduled(fixedRate = 5000)
	public void checkHealth() {
		try {
			String baseUrl = targetServerUrl.endsWith("/v1")
					? targetServerUrl.substring(0, targetServerUrl.length() - 3)
					: targetServerUrl;
			
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/health?include_slots=true"))
					.timeout(Duration.ofSeconds(3))
					.GET()
					.build();
			
			HttpResponse<String> response = this.healthHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
			boolean healthy = response.statusCode() >= 200 && response.statusCode() < 300;
			serverHealthy.set(healthy);
			log.trace("[HealthCheck] Target {} is ONLINE. HTTP {}", baseUrl, response.statusCode());
		} catch (Exception e) {
			serverHealthy.set(false);
			log.debug("[HealthCheck] Failed to connect: {} - {}", e.getClass().getName(), e.getMessage());
		}
	}

	@GetMapping("/health")
	public boolean getHealth() {
		return serverHealthy.get();
	}

	@GetMapping("/test-health")
	public String testHealth() {
		try {
			String baseUrl = targetServerUrl.endsWith("/v1")
					? targetServerUrl.substring(0, targetServerUrl.length() - 3)
					: targetServerUrl;
			ResponseEntity<String> response = restClient.get().uri(baseUrl + "/health?include_slots=true").retrieve()
					.toEntity(String.class);
			return "OK: " + response.getStatusCode() + " - " + response.getBody();
		} catch (Exception e) {
			return "ERROR: " + e.getClass().getName() + " - " + e.getMessage();
		}
	}

	@GetMapping("/target-url")
	public String getTargetUrl() {
		return targetServerUrl;
	}

	@GetMapping("/restart-commands")
	public List<ProxySettings.RestartCommand> getRestartCommands() {
		return proxySettings.getRestartCommands();
	}

	@PutMapping("/restart-commands")
	public ResponseEntity<Void> updateRestartCommands(@RequestBody List<ProxySettings.RestartCommand> commands) {
		proxySettings.setRestartCommands(commands);
		return ResponseEntity.ok().build();
	}

	private void killProcessOnPort(int port) {
		try {
			Process process = new ProcessBuilder("cmd.exe", "/c", "netstat -ano | findstr :" + port).start();
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.contains("LISTENING")) {
					String[] parts = line.trim().split("\\s+");
					if (parts.length > 4) {
						String pid = parts[parts.length - 1];
						log.info("Killing process with PID: {} on port: {}", pid, port);
						new ProcessBuilder("cmd.exe", "/c", "taskkill /F /PID " + pid).start().waitFor();
					}
				}
			}
			process.waitFor();
		} catch (Exception e) {
			log.error("Failed to kill process on port {}: {}", port, e.getMessage());
		}
	}

	@PostMapping("/restart-target")
	public ResponseEntity<String> restartTarget(@RequestBody(required = false) Map<String, String> payload) {
		try {
			String commandToRun = null;
			if (payload != null) {
				if (payload.containsKey("command") && payload.get("command") != null && !payload.get("command").isBlank()) {
					commandToRun = payload.get("command");
				} else if (payload.containsKey("id") && payload.get("id") != null && !payload.get("id").isBlank()) {
					String id = payload.get("id");
					for (ProxySettings.RestartCommand rc : proxySettings.getRestartCommands()) {
						if (id.equals(rc.getId())) {
							commandToRun = rc.getCommand();
							break;
						}
					}
				}
			}

			if (commandToRun == null || commandToRun.isBlank()) {
				if (proxySettings.getRestartCommands() != null && !proxySettings.getRestartCommands().isEmpty()) {
					commandToRun = proxySettings.getRestartCommands().get(0).getCommand();
				} else {
					commandToRun = restartScript;
				}
			}

			// Extract port from targetServerUrl
			URL url = URI.create(targetServerUrl).toURL();
			int port = url.getPort();
			if (port != -1) {
				killProcessOnPort(port);
			}

			// Wait a second for OS to release port
			Thread.sleep(1000);

			ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", commandToRun);
			File scriptFile = new File(commandToRun);
			if (scriptFile.exists() && scriptFile.getParentFile() != null) {
				pb.directory(scriptFile.getParentFile());
			}
			pb.redirectErrorStream(true);
			pb.redirectOutput(new File("target-server.log"));
			pb.start();
			return ResponseEntity.ok("Restart command issued: " + commandToRun);
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body("Failed to execute restart script: " + e.getMessage());
		}
	}

	@PostMapping("/kill-target")
	public ResponseEntity<String> killTarget() {
		try {
			URL url = URI.create(targetServerUrl).toURL();
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

	@GetMapping(value = "/target-logs-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter streamTargetLogs() {
		SseEmitter emitter = new SseEmitter(
				Long.MAX_VALUE);
		Thread.startVirtualThread(() -> {
			try {
				File logFile = new File("target-server.log");

				long lastKnownPosition = 0;
				if (logFile.exists()) {
					long length = logFile.length();
					lastKnownPosition = length > 50000 ? length - 50000 : 0;
				} else {
					Map<String, String> payload = new HashMap<>();
					payload.put("type", "INITIAL");
					payload.put("data", "Log file not found. Have you restarted the server yet?\n");
					emitter.send(SseEmitter.event().name("log")
								.data(OBJECT_MAPPER.writeValueAsString(payload)));
				}

				while (true) {
					if (logFile.exists()) {
						long length = logFile.length();
						if (length < lastKnownPosition) {
							// File was truncated or recreated
							lastKnownPosition = 0;
						}
						if (length > lastKnownPosition) {
							RandomAccessFile raf = new RandomAccessFile(logFile, "r");
							raf.seek(lastKnownPosition);
							byte[] bytes = new byte[(int) (length - lastKnownPosition)];
							raf.readFully(bytes);
							raf.close();
							lastKnownPosition = length;

							String newLogs = new String(bytes);
							Map<String, String> payload = new HashMap<>();
							payload.put("type", "APPEND");
							payload.put("data", newLogs);
							emitter.send(SseEmitter.event()
										.name("log").data(OBJECT_MAPPER
											.writeValueAsString(payload)));
						}
					}
					Thread.sleep(500);
				}
			} catch (Exception e) {
				try {
					emitter.completeWithError(e);
				} catch (Exception ex) {
				}
			}
		});
		return emitter;
	}

	@GetMapping(value = "/update-llama-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter updateLlamaStream() {
		SseEmitter emitter = new SseEmitter(
				Long.MAX_VALUE);

		Thread.startVirtualThread(() -> {
			try {
				// Helper to emit progress
				BiConsumer<String, String> emitProgress = (step, status) -> {
					try {
						Map<String, String> payload = new HashMap<>();
						payload.put("step", step);
						payload.put("status", status);
						emitter.send(SseEmitter.event()
								.name("progress").data(OBJECT_MAPPER.writeValueAsString(payload)));
					} catch (Exception e) {
					}
				};

				// 1. Fetch latest release from GitHub FIRST (before killing server)
				emitProgress.accept("DOWNLOADING", "running");
				String repo = "ggml-org/llama.cpp";
				String apiUrl = "https://api.github.com/repos/" + repo + "/releases/latest";

				String jsonResponse;
				try {
					jsonResponse = restClient.get().uri(apiUrl).retrieve().body(String.class);
				} catch (HttpClientErrorException e) {
					if (e.getStatusCode().value() == 403 || e.getStatusCode().value() == 429) {
						throw new RuntimeException(
								"GitHub API rate limit exceeded. Cannot check for updates right now.");
					}
					throw e;
				}

				if (jsonResponse == null) {
					throw new RuntimeException("Failed to fetch release data from GitHub.");
				}

				JsonNode releaseData = OBJECT_MAPPER.readTree(jsonResponse);

				// 2. Find asset matching regex
				Pattern pattern = Pattern.compile(releaseRegex);
				String downloadUrl = null;
				String filename = null;

				JsonNode assets = releaseData.get("assets");
				if (assets != null && assets.isArray()) {
					for (JsonNode asset : assets) {
						String assetName = asset.get("name").asText();
						if (pattern.matcher(assetName).matches()) {
							downloadUrl = asset.get("browser_download_url").asText();
							filename = assetName;
							break;
						}
					}
				}

				if (downloadUrl == null) {
					throw new RuntimeException(
							"No asset matching regex '" + releaseRegex + "' found in latest release.");
				}

				// 3. Now that we have the URL, Kill the server
				emitProgress.accept("KILLING", "running");
				URL url = URI.create(targetServerUrl).toURL();
				int port = url.getPort();
				if (port != -1) {
					killProcessOnPort(port);
				}
				Thread.sleep(1000); // Give OS time to release port
				emitProgress.accept("KILLING", "done");

				// 3. Ensure install directory exists
				File installDirectory = new File(installDir);
				if (!installDirectory.exists() && !installDirectory.mkdirs()) {
					throw new RuntimeException("Failed to create install directory: " + installDir);
				}

				// 4. Download file
				File downloadedZip = new File(installDirectory, filename);
				byte[] zipBytes = restClient.get().uri(downloadUrl).retrieve().body(byte[].class);

				if (zipBytes == null) {
					throw new RuntimeException("Failed to download file payload.");
				}

				Files.write(downloadedZip.toPath(), zipBytes);
				emitProgress.accept("DOWNLOADING", "done");

				// 5. Unzip the file
				emitProgress.accept("UNZIPPING", "running");
				try (ZipInputStream zis = new ZipInputStream(
						new ByteArrayInputStream(zipBytes))) {
					ZipEntry zipEntry = zis.getNextEntry();
					while (zipEntry != null) {
						File newFile = new File(installDirectory, zipEntry.getName());
						if (!newFile.getCanonicalPath()
								.startsWith(installDirectory.getCanonicalPath() + File.separator)) {
							throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
						}
						if (zipEntry.isDirectory()) {
							if (!newFile.isDirectory() && !newFile.mkdirs()) {
								throw new IOException("Failed to create directory " + newFile);
							}
						} else {
							File parent = newFile.getParentFile();
							if (!parent.isDirectory() && !parent.mkdirs()) {
								throw new IOException("Failed to create directory " + parent);
							}
							try (FileOutputStream fos = new FileOutputStream(newFile)) {
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
				emitProgress.accept("UNZIPPING", "done");

				// 6. Delete downloaded zip to clean up
				downloadedZip.delete();

				// 7. Save version to version.txt
				emitProgress.accept("UPDATING_VERSION", "running");
				String latestTag = releaseData.get("tag_name").asText();
				File versionFile = new File(installDirectory, "version.txt");
				Files.writeString(versionFile.toPath(), latestTag);
				emitProgress.accept("UPDATING_VERSION", "done");

				// 8. Restart Server
				emitProgress.accept("RESTARTING", "running");
				ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", restartScript);
				File scriptFile = new File(restartScript);
				if (scriptFile.exists() && scriptFile.getParentFile() != null) {
					pb.directory(scriptFile.getParentFile());
				}
				pb.redirectErrorStream(true);
				pb.redirectOutput(new File("target-server.log"));
				pb.start();
				emitProgress.accept("RESTARTING", "done");

				// 9. Done
				emitProgress.accept("DONE", "done");
				emitter.complete();

			} catch (Exception e) {
				try {
					Map<String, String> payload = new HashMap<>();
					payload.put("step", "ERROR");
					payload.put("status", "error");
					payload.put("message", e.getMessage());
					emitter.send(SseEmitter.event()
							.name("progress").data(OBJECT_MAPPER.writeValueAsString(payload)));
					emitter.completeWithError(e);
				} catch (Exception ex) {
				}
			}
		});
		return emitter;
	}

	@Scheduled(initialDelay = 0, fixedRate = 3600000) // Run on startup and every hour
	@GetMapping("/check-update")
	public ResponseEntity<?> checkUpdate() {
		try {
			// Fetch latest release from GitHub
			String repo = "ggml-org/llama.cpp";
			String apiUrl = "https://api.github.com/repos/" + repo + "/releases/latest";

			String jsonResponse;
			try {
				jsonResponse = restClient.get().uri(apiUrl).retrieve().body(String.class);
			} catch (HttpClientErrorException e) {
				if (e.getStatusCode().value() == 403 || e.getStatusCode().value() == 429) {
					log.warn("GitHub API rate limit hit while checking for Llama.cpp updates. Skipping update check.");
					return ResponseEntity.status(429).body(Map.of("error", "GitHub API rate limit exceeded"));
				}
				throw e;
			}

			if (jsonResponse == null) {
				return ResponseEntity.internalServerError()
						.body(Map.of("error", "Failed to fetch release data"));
			}

			JsonNode releaseData = OBJECT_MAPPER
					.readTree(jsonResponse);

			if (!releaseData.has("tag_name")) {
				return ResponseEntity.internalServerError()
						.body(Map.of("error", "Failed to find tag_name in release data"));
			}

			String latestTag = releaseData.get("tag_name").asText();
			String currentTag = "unknown";

			File versionFile = new File(installDir, "version.txt");
			if (versionFile.exists()) {
				currentTag = Files.readString(versionFile.toPath()).trim();
			}

			boolean updateAvailable = !latestTag.equals(currentTag);

			if (updateAvailable && !notificationService.hasUnreadNotification("updater")) {
				NotificationDTO n = new NotificationDTO();
				n.setSourcePlugin("updater");
				n.setTitle("Llama Update Available");
				n.setMessage("Version " + latestTag + " is available to download.");
				n.setLevel("info");

				String releaseUrl = "https://github.com/" + repo + "/releases/latest";
				n.setActions(List.of(
						new NotificationDTO.NotificationAction("Update Now", null,
								null, null, "/api/proxy/update-llama-stream", true),
						new NotificationDTO.NotificationAction("Read Release Notes",
								null, releaseUrl, null, null, false)));
				notificationService.addNotification(n);
			}

			return ResponseEntity.ok(Map.of("updateAvailable", updateAvailable, "latestVersion", latestTag,
					"currentVersion", currentTag));

		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
		}
	}
}

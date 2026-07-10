package com.example.llamaproxy.pipeline;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@Service
public class HardwareMonitorService {

    private final LiveChatBroadcaster broadcaster;
    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hal;
    
    private boolean nvidiaSmiAvailable = true;

    public HardwareMonitorService(LiveChatBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
        this.systemInfo = new SystemInfo();
        this.hal = systemInfo.getHardware();
    }

    @Scheduled(fixedRate = 2000)
    public void broadcastHardwareStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // CPU
        CentralProcessor processor = hal.getProcessor();
        double[] loadAverage = processor.getSystemLoadAverage(1);
        double cpuLoad = processor.getSystemCpuLoad(1000);
        stats.put("cpuLoad", Math.round(cpuLoad * 100.0));

        // RAM
        GlobalMemory memory = hal.getMemory();
        long totalRam = memory.getTotal();
        long availableRam = memory.getAvailable();
        long usedRam = totalRam - availableRam;
        stats.put("ramUsedGb", String.format("%.1f", usedRam / 1e9));
        stats.put("ramTotalGb", String.format("%.1f", totalRam / 1e9));
        stats.put("ramPercent", Math.round(((double) usedRam / totalRam) * 100.0));

        // VRAM (nvidia-smi)
        if (nvidiaSmiAvailable) {
            try {
                Process process = new ProcessBuilder("nvidia-smi", "--query-gpu=memory.used,memory.total", "--format=csv,noheader,nounits")
                        .start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                long totalVramUsedMb = 0;
                long totalVramTotalMb = 0;
                boolean foundGpu = false;
                String line;
                
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        try {
                            totalVramUsedMb += Long.parseLong(parts[0].trim());
                            totalVramTotalMb += Long.parseLong(parts[1].trim());
                            foundGpu = true;
                        } catch (NumberFormatException ignored) {}
                    }
                }
                
                if (foundGpu) {
                    stats.put("vramUsedGb", String.format("%.1f", totalVramUsedMb / 1024.0));
                    stats.put("vramTotalGb", String.format("%.1f", totalVramTotalMb / 1024.0));
                    stats.put("vramPercent", Math.round(((double) totalVramUsedMb / totalVramTotalMb) * 100.0));
                }
                process.waitFor();
            } catch (Exception e) {
                // Not NVIDIA or nvidia-smi not in PATH
                nvidiaSmiAvailable = false;
                System.out.println("WARN: nvidia-smi not found or failed. VRAM metrics will not be displayed. Currently only NVIDIA GPUs are supported.");
            }
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(stats);
            broadcaster.broadcastHardware(json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

package io.github.dbarciela.aura.pipeline;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

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

	@Scheduled(fixedRateString = "${hardware.monitor.interval:5000}")
	public void broadcastHardwareStats() {
		Map<String, Object> stats = new HashMap<>();

		// CPU
		CentralProcessor processor = hal.getProcessor();
		double cpuLoad = processor.getSystemCpuLoad(1000);
		double cpuTemp = hal.getSensors().getCpuTemperature();
		stats.put("cpuLoad", Math.round(cpuLoad * 100.0));
		if (cpuTemp > 0) {
			stats.put("cpuTemp", Math.round(cpuTemp));
		}

		// RAM
		GlobalMemory memory = hal.getMemory();
		long totalRam = memory.getTotal();
		long availableRam = memory.getAvailable();
		long usedRam = totalRam - availableRam;
		stats.put("ramUsedGb", String.format("%.1f", usedRam / 1e9));
		stats.put("ramTotalGb", String.format("%.1f", totalRam / 1e9));
		stats.put("ramPercent", Math.round((double) usedRam / totalRam * 100.0));

		// VRAM (nvidia-smi)
		if (nvidiaSmiAvailable) {
			try {
				Process process = new ProcessBuilder("nvidia-smi",
						"--query-gpu=name,temperature.gpu,utilization.gpu,memory.used,memory.total",
						"--format=csv,noheader,nounits").start();
				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

				java.util.List<Map<String, Object>> gpus = new java.util.ArrayList<>();
				String line;

				while ((line = reader.readLine()) != null) {
					String[] parts = line.split(",");
					if (parts.length >= 5) {
						try {
							Map<String, Object> gpu = new HashMap<>();
							gpu.put("name", parts[0].trim());
							gpu.put("temp", Integer.parseInt(parts[1].trim()));
							gpu.put("utilization", Integer.parseInt(parts[2].trim()));

							long usedMb = Long.parseLong(parts[3].trim());
							long totalMb = Long.parseLong(parts[4].trim());

							gpu.put("vramUsedGb", String.format("%.1f", usedMb / 1024.0));
							gpu.put("vramTotalGb", String.format("%.1f", totalMb / 1024.0));
							gpu.put("vramPercent", Math.round((double) usedMb / totalMb * 100.0));

							gpus.add(gpu);
						} catch (NumberFormatException ignored) {
						}
					}
				}

				if (!gpus.isEmpty()) {
					stats.put("gpus", gpus);
				}
				process.waitFor();
			} catch (Exception e) {
				// Not NVIDIA or nvidia-smi not in PATH
				nvidiaSmiAvailable = false;
				System.out.println(
						"WARN: nvidia-smi not found or failed. VRAM metrics will not be displayed. Currently only NVIDIA GPUs are supported.");
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

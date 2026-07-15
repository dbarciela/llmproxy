package io.github.dbarciela.aura.pipeline;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.dbarciela.aura.config.ProxySettings;

@Service
public class MetricsMonitorService {

	private static final Logger log = LoggerFactory.getLogger(MetricsMonitorService.class);

	private final SseBroadcaster broadcaster;
	private final ProxySettings settings;
	private final RestTemplate restTemplate;
	private final ObjectMapper mapper;
	
	private boolean metricsEndpointAvailable = true;

	public MetricsMonitorService(SseBroadcaster broadcaster, ProxySettings settings) {
		this.broadcaster = broadcaster;
		this.settings = settings;
		this.restTemplate = new RestTemplate();
		this.mapper = new ObjectMapper();
	}

	@Scheduled(fixedRateString = "${slots.monitor.interval:5000}")
	public void pollMetrics() {
		if (!metricsEndpointAvailable) {
			return; // Do not spam the server if metrics are disabled
		}

		String webUiUrl = settings.getWebUiUrl();
		if (webUiUrl == null || webUiUrl.isEmpty()) {
			return;
		}

		String metricsUrl = webUiUrl.endsWith("/") ? webUiUrl + "metrics" : webUiUrl + "/metrics";

		try {
			String response = restTemplate.getForObject(metricsUrl, String.class);
			
			if (response != null) {
				// Parse Prometheus format to JSON
				Map<String, Double> metricsMap = new HashMap<>();
				String[] lines = response.split("\\r?\\n");
				
				for (String line : lines) {
					// Ignore comments and empty lines
					if (line.trim().isEmpty() || line.startsWith("#")) {
						continue;
					}
					
					// Format is "key value"
					String[] parts = line.split("\\s+");
					if (parts.length >= 2) {
						try {
							String key = parts[0].replace("llamacpp:", ""); // Clean prefix
							Double value = Double.parseDouble(parts[1]);
							metricsMap.put(key, value);
						} catch (NumberFormatException ignored) {}
					}
				}

				if (!metricsMap.isEmpty()) {
					String json = mapper.writeValueAsString(metricsMap);
					broadcaster.broadcastMetrics("metrics-id", json); // broadcastMetrics uses "id", "type", "data"
				}
			}
		} catch (HttpClientErrorException e) {
			if (e.getStatusCode().value() == 501) {
				// HTTP 501 means --metrics flag is missing in Llama.cpp
				log.warn("Llama.cpp metrics endpoint is disabled (HTTP 501). Start Llama.cpp with --metrics to enable Global Metrics UI. Polling disabled.");
				metricsEndpointAvailable = false;
				
				// Broadcast an error state so the UI knows it's disabled
				broadcaster.broadcastMetrics("metrics-id", "{\"error\": \"disabled\"}");
			} else {
				log.trace("Failed to fetch metrics from {}: {}", metricsUrl, e.getMessage());
			}
		} catch (Exception e) {
			// General connection errors
			log.trace("Failed to fetch metrics from {}: {}", metricsUrl, e.getMessage());
		}
	}
}

package io.github.dbarciela.aura.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import io.github.dbarciela.aura.config.ProxySettings;

@Service
public class SlotsMonitorService {

	private static final Logger log = LoggerFactory.getLogger(SlotsMonitorService.class);
	
	private final SseBroadcaster broadcaster;
	private final ProxySettings settings;
	private final RestTemplate restTemplate;

	public SlotsMonitorService(SseBroadcaster broadcaster, ProxySettings settings) {
		this.broadcaster = broadcaster;
		this.settings = settings;
		this.restTemplate = new RestTemplate();
	}

	@Scheduled(fixedRateString = "${slots.monitor.interval:5000}")
	public void pollSlots() {
		String webUiUrl = settings.getWebUiUrl();
		if (webUiUrl == null || webUiUrl.isEmpty()) {
			return;
		}

		String slotsUrl = webUiUrl.endsWith("/") ? webUiUrl + "slots" : webUiUrl + "/slots";

		try {
			// Fetch the raw JSON string from the Llama.cpp /slots endpoint
			String jsonResponse = restTemplate.getForObject(slotsUrl, String.class);
			
			if (jsonResponse != null) {
				broadcaster.broadcastSlots(jsonResponse);
			}
		} catch (Exception e) {
			// Silently ignore connection errors to avoid spamming the console if Llama is offline
			log.trace("Failed to fetch slots from {}: {}", slotsUrl, e.getMessage());
		}
	}
}

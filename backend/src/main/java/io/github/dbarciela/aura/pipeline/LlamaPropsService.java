package io.github.dbarciela.aura.pipeline;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class LlamaPropsService {

	private final RestClient restClient;
	private final String targetServerUrl;
	private final LiveChatBroadcaster broadcaster;

	private Integer cachedContextLimit = null;

	public LlamaPropsService(@Value("${target.server.url}") String targetServerUrl, LiveChatBroadcaster broadcaster) {
		this.targetServerUrl = targetServerUrl;
		this.broadcaster = broadcaster;
		this.restClient = RestClient.builder().build();
	}

	@Scheduled(fixedRate = 10000)
	public void fetchProps() {
		try {
			String baseUrl = targetServerUrl.endsWith("/v1")
					? targetServerUrl.substring(0, targetServerUrl.length() - 3)
					: targetServerUrl;

			// Assume llama.cpp /props endpoint
			@SuppressWarnings("unchecked")
			Map<String, Object> response = restClient.get().uri(baseUrl + "/props").retrieve().body(Map.class);

			if (response != null && response.containsKey("default_generation_settings")) {
				@SuppressWarnings("unchecked")
				Map<String, Object> settings = (Map<String, Object>) response.get("default_generation_settings");
				if (settings.containsKey("n_ctx")) {
					cachedContextLimit = (Integer) settings.get("n_ctx");
				}
			}
		} catch (Exception e) {
			// Ignore if /props not supported
		}

		if (cachedContextLimit != null) {
			broadcaster.broadcastContextLimit(cachedContextLimit);
		}
	}
}

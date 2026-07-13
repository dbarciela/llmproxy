package io.github.dbarciela.aura.pipeline;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LlamaPropsService {

	private static final Logger log = LoggerFactory.getLogger(LlamaPropsService.class);
	private final HttpClient httpClient;
	private final ObjectMapper mapper = new ObjectMapper();
	private final String targetServerUrl;
	private final LiveChatBroadcaster broadcaster;

	private Integer cachedContextLimit;

	public LlamaPropsService(@Value("${target.server.url}") String targetServerUrl, LiveChatBroadcaster broadcaster) {
		this.targetServerUrl = targetServerUrl;
		this.broadcaster = broadcaster;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(3))
				.build();
	}

	@Scheduled(fixedRate = 10000)
	public void fetchProps() {
		try {
			String baseUrl = targetServerUrl.endsWith("/v1")
					? targetServerUrl.substring(0, targetServerUrl.length() - 3)
					: targetServerUrl;

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/props"))
					.timeout(Duration.ofSeconds(3))
					.GET()
					.build();

			HttpResponse<String> responseStr = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			
			if (responseStr.statusCode() >= 200 && responseStr.statusCode() < 300) {
				JsonNode response = mapper.readTree(responseStr.body());
				if (response != null && response.has("default_generation_settings")) {
					JsonNode settings = response.get("default_generation_settings");
					if (settings.has("n_ctx")) {
						cachedContextLimit = settings.get("n_ctx").asInt();
					}
				}
			}
		} catch (Exception e) {
			log.trace("Failed to fetch /props, possibly not supported or offline: {}", e.getMessage());
		}

		if (cachedContextLimit != null) {
			broadcaster.broadcastContextLimit(cachedContextLimit);
		}
	}
}

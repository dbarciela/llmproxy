package io.github.dbarciela.aura.pipeline;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LlmTitleService {

	private final RestClient restClient;
	private final String targetServerUrl;
	private final ObjectMapper mapper = new ObjectMapper();

	public LlmTitleService(@Value("${target.server.url}") String targetServerUrl) {
		this.targetServerUrl = targetServerUrl;
		this.restClient = RestClient.builder().build();
	}

	public String generateTitle(String payload) {
		try {
			// Very naive way to extract first message content without fully parsing unknown
			// structure
			// In a real app we'd parse this correctly, but since we know it's OpenAI
			// format...
			String jsonPart = payload;
			if (jsonPart.startsWith("REQUEST:\n")) {
				int respIndex = jsonPart.indexOf("\nRESPONSE:\n");
				if (respIndex != -1) {
					jsonPart = jsonPart.substring("REQUEST:\n".length(), respIndex).trim();
				} else {
					jsonPart = jsonPart.substring("REQUEST:\n".length()).trim();
				}
			}
			JsonNode root = mapper.readTree(jsonPart);
			String firstMessage = "";
			if (root.has("messages") && root.get("messages").isArray() && root.get("messages").size() > 0) {
				StringBuilder combinedUserMessages = new StringBuilder();
				for (JsonNode msgNode : root.get("messages")) {
					String role = msgNode.path("role").asText();
					if ("assistant".equals(role)) {
						break;
					}
					if ("user".equals(role)) {
						combinedUserMessages.append(msgNode.path("content").asText()).append("\n\n");
					}
				}

				firstMessage = combinedUserMessages.toString();

				if (firstMessage.isEmpty()) {
					firstMessage = root.get("messages").get(0).path("content").asText(); // fallback
				}
				// Smart extraction: Look for <userRequest> or <user_input>
				java.util.regex.Matcher m = java.util.regex.Pattern
						.compile("<user(?:Request|_input|Input)>([\\s\\S]*?)</user(?:Request|_input|Input)>",
								java.util.regex.Pattern.CASE_INSENSITIVE)
						.matcher(firstMessage);
				if (m.find()) {
					StringBuilder smartExtracted = new StringBuilder();
					do {
						smartExtracted.append(m.group(1).trim()).append(" ");
					} while (m.find());
					firstMessage = smartExtracted.toString().trim();
				} else {
					// Remove heavy system context blocks completely (including their content)
					firstMessage = firstMessage.replaceAll(
							"(?is)<(environment_info|workspace_info|user_information|identity|plugins|messaging|conversation_transcript|artifacts|slash_commands|subagents)>.*?</\\1>",
							" ");
					// Strip all remaining XML tags to avoid sending huge workspace context
					firstMessage = firstMessage.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
				}
			} else if (root.has("prompt")) {
				firstMessage = root.get("prompt").asText();
				if (firstMessage.length() > 500) {
					firstMessage = firstMessage.substring(0, 500);
				}
			} else {
				return "Unknown Chat";
			}

			String systemPrompt = "You are a helpful assistant. Please read the following start of a conversation and generate a short, explanatory title for it (max 10 words). The title should describe the specific technical intent or question of the user. Do not use quotes or prefixes, output ONLY the title.";

			// Build OpenAI compatible request payload
			System.out.println("CLEANED MESSAGE FOR TITLE: " + firstMessage);

			// Build OpenAI compatible request payload
			String requestPayload = """
					{
					  "model": "proxy-auto-titler",
					  "messages": [
					    {"role": "system", "content": %s},
					    {"role": "user", "content": %s}
					  ],
					  "temperature": 0.7
					}
					""".formatted(mapper.writeValueAsString(systemPrompt),
					mapper.writeValueAsString("Conversation starts here:\n" + firstMessage));

			String chatEndpoint = targetServerUrl;
			if (!chatEndpoint.endsWith("/chat/completions")) {
				if (chatEndpoint.endsWith("/v1")) {
					chatEndpoint += "/chat/completions";
				} else if (chatEndpoint.endsWith("/v1/")) {
					chatEndpoint += "chat/completions";
				} else {
					chatEndpoint += "/v1/chat/completions";
				}
			}

			return restClient.post().uri(chatEndpoint).contentType(MediaType.APPLICATION_JSON)
					.body(requestPayload.getBytes(StandardCharsets.UTF_8)).exchange((clientRequest, clientResponse) -> {
						if (clientResponse.getStatusCode().is2xxSuccessful()) {
							JsonNode respNode = mapper.readTree(clientResponse.getBody());
							if (respNode.has("choices") && respNode.get("choices").isArray()
									&& respNode.get("choices").size() > 0) {
								JsonNode messageNode = respNode.get("choices").get(0).path("message");
								if (messageNode.has("content")) {
									String generated = messageNode.get("content").asText().trim();
									System.out.println("GENERATED TITLE: " + generated);
									return generated;
								}
							}
						}
						System.out.println("LLM API FAILED, STATUS: " + clientResponse.getStatusCode());
						return "Failed to generate title";
					});

		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("LLM API EXCEPTION: " + e.getMessage());
			return "Error: " + e.getMessage();
		}
	}
}

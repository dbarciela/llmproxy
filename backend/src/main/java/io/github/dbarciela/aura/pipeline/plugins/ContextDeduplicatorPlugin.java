package io.github.dbarciela.aura.pipeline.plugins;

import java.util.HashMap;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.dbarciela.aura.config.PluginSettingsManager;
import io.github.dbarciela.aura.pipeline.BufferingPlugin;
import io.github.dbarciela.aura.pipeline.LiveChatBroadcaster;
import io.github.dbarciela.aura.pipeline.RequestContext;
import io.github.dbarciela.aura.pipeline.ResponseContext;

@Component
@Order(10)
public class ContextDeduplicatorPlugin implements BufferingPlugin {

	private final PluginSettingsManager settingsManager;
	private final LiveChatBroadcaster broadcaster;
	private final ObjectMapper mapper = new ObjectMapper();


	// Global stats
	private int globalSavedChars = 0;
	private int globalTotalOriginalChars = 0;
	private final Map<String, String> globalDeduplicatedBlocks = new java.util.concurrent.ConcurrentHashMap<>();

	public Map<String, Object> getStats() {
		Map<String, Object> stats = new HashMap<>();
		stats.put("savedChars", globalSavedChars);
		stats.put("totalOriginalChars", globalTotalOriginalChars);
		stats.put("blocks", globalDeduplicatedBlocks);
		return stats;
	}

	public ContextDeduplicatorPlugin(PluginSettingsManager settingsManager, LiveChatBroadcaster broadcaster) {
		this.settingsManager = settingsManager;
		this.broadcaster = broadcaster;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DeduplicatorSettings {
		public boolean enabled = false;
		public int threshold = 500;
	}

	@Override
	public String getId() {
		return "context-deduplicator";
	}

	@Override
	public String getName() {
		return "Context Deduplicator";
	}

	@Override
	public String getDescription() {
		return "Automatically remove duplicated context in large conversations to save tokens and VRAM.";
	}

	@Override
	public Object getDefaultSettings() {
		return new DeduplicatorSettings();
	}

	@Override
	public String getUiTabName() {
		return "Deduplicator";
	}

	@Override
	public boolean hasUiToggle() {
		return true;
	}

	@Override
	public int getDefaultOrder() {
		return 10;
	}

	@Override
	public void processRequest(RequestContext context) {
		DeduplicatorSettings pluginSettings = settingsManager.getSettingsAs(getId(), DeduplicatorSettings.class);
		if (pluginSettings == null) {
			pluginSettings = new DeduplicatorSettings();
		}

		String payload = context.getPayload();
		if (payload == null || payload.isEmpty()) {
			return;
		}

		try {
			JsonNode rootNode = mapper.readTree(payload);
			if (!rootNode.has("messages") || !rootNode.get("messages").isArray()) {
				return;
			}

			ArrayNode messages = (ArrayNode) rootNode.get("messages");
			if (messages.size() < 2) {
				return; // Need at least history + current message
			}

			int totalSavedChars = 0;
			Map<String, String> deduplicatedBlocks = new HashMap<>();
			int blockCounter = 1;

			StringBuilder historyBuilder = new StringBuilder();

			// Start with the first message's content
			if (messages.size() > 0) {
				JsonNode firstMsg = messages.get(0);
				if (firstMsg.has("content")) {
					historyBuilder.append(firstMsg.get("content").asText()).append("\n\n");
				}
			}

			for (int msgIdx = 1; msgIdx < messages.size(); msgIdx++) {
				JsonNode currentMsg = messages.get(msgIdx);
				String history = historyBuilder.toString();

				if (currentMsg.has("content") && !history.isEmpty()
						&& !"system".equals(currentMsg.path("role").asText())) {
					String currentContent = currentMsg.get("content").asText();

					int threshold = pluginSettings.threshold;
					if (threshold < 50) {
						threshold = 50;
					}
					int W = Math.min(50, threshold / 2);

					StringBuilder newContent = new StringBuilder();
					int i = 0;
					int lastProcessed = 0;
					int savedChars = 0;

					while (i <= currentContent.length() - W) {
						String chunk = currentContent.substring(i, i + W);
						int histIdx = history.indexOf(chunk);
						boolean matchFound = false;

						while (histIdx != -1) {
							int startMsg = i;
							int startHist = histIdx;
							while (startMsg > lastProcessed && startHist > 0
									&& currentContent.charAt(startMsg - 1) == history.charAt(startHist - 1)) {
								startMsg--;
								startHist--;
							}

							int endMsg = i + W;
							int endHist = histIdx + W;
							while (endMsg < currentContent.length() && endHist < history.length()
									&& currentContent.charAt(endMsg) == history.charAt(endHist)) {
								endMsg++;
								endHist++;
							}

							int matchLen = endMsg - startMsg;
							if (matchLen >= threshold) {
								newContent.append(currentContent.substring(lastProcessed, startMsg));
								String matchedText = currentContent.substring(startMsg, endMsg);
								String prefix = matchedText.length() > 30
										? matchedText.substring(0, 30).replace("\n", " ")
										: matchedText;
								String suffix = matchedText.length() > 30
										? matchedText.substring(matchedText.length() - 30).replace("\n", " ")
										: "";
								String replacement = String.format(
										"\n\n[Duplicated context omitted: %d chars match earlier context. Starts with \"%s...\" and ends with \"...%s\"]\n\n",
										matchLen, prefix, suffix);

								newContent.append(replacement);
								String blockId = "Block-" + blockCounter++;
								String blockText = matchedText;
								deduplicatedBlocks.put(blockId, blockText);
								globalDeduplicatedBlocks.put(blockId, blockText);
								savedChars += matchLen;

								lastProcessed = endMsg;
								i = endMsg;
								matchFound = true;
								break;
							}

							// Try the next occurrence of the chunk in history
							histIdx = history.indexOf(chunk, histIdx + 1);
						}

						if (matchFound) {
							continue;
						}

						i += W;
					}

					if (lastProcessed < currentContent.length()) {
						newContent.append(currentContent.substring(lastProcessed));
					}

					if (savedChars > 0) {
						((ObjectNode) currentMsg).put("content", newContent.toString());
						totalSavedChars += savedChars;
					}
					// For the history building, we MUST append the original full content, otherwise
					// future messages that duplicate the full text won't find the match!
					historyBuilder.append(currentContent).append("\n\n");
				} else if (currentMsg.has("content")) {
					// Not a user message or doesn't have content to deduplicate, just append to
					// history
					historyBuilder.append(currentMsg.get("content").asText()).append("\n\n");
				}
			}

			if (totalSavedChars > 0) {
				context.setPayload(mapper.writeValueAsString(rootNode));

				globalSavedChars += totalSavedChars;
				globalTotalOriginalChars += payload.length();

				// Broadcast stats
				Map<String, Object> stats = new HashMap<>();
				stats.put("savedChars", totalSavedChars);
				stats.put("totalOriginalChars", payload.length());
				stats.put("blocks", deduplicatedBlocks);

				try {
					broadcaster.broadcastPluginEvent(getId(), "DEDUPLICATION_STATS", stats);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		} catch (Exception e) {
			e.printStackTrace(); // Invalid JSON, ignore
		}
	}

	@Override
	public void processResponse(ResponseContext context) {
		// No-op
	}
}

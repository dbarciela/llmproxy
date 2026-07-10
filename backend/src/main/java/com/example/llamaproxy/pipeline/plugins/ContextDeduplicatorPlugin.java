package com.example.llamaproxy.pipeline.plugins;

import com.example.llamaproxy.config.ProxySettings;
import com.example.llamaproxy.config.PluginSettingsManager;
import com.example.llamaproxy.pipeline.LiveChatBroadcaster;
import com.example.llamaproxy.pipeline.ProxyPlugin;
import com.example.llamaproxy.pipeline.RequestContext;
import com.example.llamaproxy.pipeline.ResponseContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Order(10)
public class ContextDeduplicatorPlugin implements ProxyPlugin {

    private final PluginSettingsManager settingsManager;
    private final LiveChatBroadcaster broadcaster;
    private final ObjectMapper mapper = new ObjectMapper();

    public ContextDeduplicatorPlugin(PluginSettingsManager settingsManager, LiveChatBroadcaster broadcaster) {
        this.settingsManager = settingsManager;
        this.broadcaster = broadcaster;
    }

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
    public void processRequest(RequestContext context) {
        DeduplicatorSettings pluginSettings = settingsManager.getSettingsAs(getId(), DeduplicatorSettings.class);
        if (pluginSettings == null || !pluginSettings.enabled) return;

        String payload = context.getPayload();
        if (payload == null || payload.isEmpty()) return;

        try {
            JsonNode rootNode = mapper.readTree(payload);
            if (!rootNode.has("messages") || !rootNode.get("messages").isArray()) {
                return;
            }

            ArrayNode messages = (ArrayNode) rootNode.get("messages");
            if (messages.size() < 2) return; // Need at least history + current message

            // Concatenate all previous user messages to form the history string
            StringBuilder historyBuilder = new StringBuilder();
            for (int i = 0; i < messages.size() - 1; i++) {
                JsonNode msg = messages.get(i);
                if (msg.has("role") && "user".equals(msg.get("role").asText()) && msg.has("content")) {
                    historyBuilder.append(msg.get("content").asText()).append("\n\n");
                }
            }
            String history = historyBuilder.toString();
            if (history.isEmpty()) return;

            // Process the current (last) message
            JsonNode lastMsg = messages.get(messages.size() - 1);
            if (lastMsg.has("role") && "user".equals(lastMsg.get("role").asText()) && lastMsg.has("content")) {
                String currentContent = lastMsg.get("content").asText();
                
                int threshold = pluginSettings.threshold;
                if (threshold < 50) threshold = 50; // Minimum sanity check
                
                int W = Math.min(50, threshold / 2); // Window size
                
                StringBuilder newContent = new StringBuilder();
                int i = 0;
                int lastProcessed = 0;
                int savedChars = 0;
                Map<String, String> deduplicatedBlocks = new HashMap<>();
                int blockCounter = 1;

                while (i <= currentContent.length() - W) {
                    String chunk = currentContent.substring(i, i + W);
                    int histIdx = history.indexOf(chunk);
                    
                    if (histIdx != -1) {
                        // Expand backwards
                        int startMsg = i;
                        int startHist = histIdx;
                        while (startMsg > lastProcessed && startHist > 0 && currentContent.charAt(startMsg - 1) == history.charAt(startHist - 1)) {
                            startMsg--;
                            startHist--;
                        }
                        
                        // Expand forwards
                        int endMsg = i + W;
                        int endHist = histIdx + W;
                        while (endMsg < currentContent.length() && endHist < history.length() && currentContent.charAt(endMsg) == history.charAt(endHist)) {
                            endMsg++;
                            endHist++;
                        }
                        
                        int matchLen = endMsg - startMsg;
                        if (matchLen >= threshold) {
                            // We found a large enough match!
                            // Append the text before this match
                            newContent.append(currentContent.substring(lastProcessed, startMsg));
                            
                            String matchedText = currentContent.substring(startMsg, endMsg);
                            String prefix = matchedText.length() > 30 ? matchedText.substring(0, 30).replace("\n", " ") : matchedText;
                            String suffix = matchedText.length() > 30 ? matchedText.substring(matchedText.length() - 30).replace("\n", " ") : "";
                            
                            String replacement = String.format("\n\n[Duplicated context omitted: %d chars match earlier context. Starts with \"%s...\" and ends with \"...%s\"]\n\n", 
                                matchLen, prefix, suffix);
                                
                            newContent.append(replacement);
                            
                            String blockId = "Block-" + blockCounter++;
                            deduplicatedBlocks.put(blockId, matchedText);
                            savedChars += matchLen;
                            
                            lastProcessed = endMsg;
                            i = endMsg; // Skip past the matched block
                            continue; 
                        }
                    }
                    
                    i += W; // Step by W to search fast
                }
                
                // Append remaining characters
                if (lastProcessed < currentContent.length()) {
                    newContent.append(currentContent.substring(lastProcessed));
                }

                if (savedChars > 0) {
                    ((ObjectNode) lastMsg).put("content", newContent.toString());
                    context.setPayload(mapper.writeValueAsString(rootNode));
                    
                    // Broadcast stats
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("savedChars", savedChars);
                    stats.put("blocks", deduplicatedBlocks);
                    
                    try {
                        String statsJson = mapper.writeValueAsString(stats);
                        broadcaster.broadcastPluginEvent(getId(), "DEDUPLICATION_STATS", stats);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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

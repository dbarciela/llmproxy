package com.example.llamaproxy.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PluginSettingsManager {

    private final Map<String, JsonNode> pluginSettings = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonNode getSettings(String pluginId) {
        return pluginSettings.get(pluginId);
    }
    
    public <T> T getSettingsAs(String pluginId, Class<T> clazz) {
        JsonNode node = pluginSettings.get(pluginId);
        if (node == null) return null;
        try {
            return mapper.treeToValue(node, clazz);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void updateSettings(String pluginId, JsonNode newSettings) {
        pluginSettings.put(pluginId, newSettings);
    }
    
    public void registerDefaultSettings(String pluginId, Object defaultSettings) {
        if (!pluginSettings.containsKey(pluginId)) {
            pluginSettings.put(pluginId, mapper.valueToTree(defaultSettings));
        }
    }
    
    public Map<String, JsonNode> getAllSettings() {
        return pluginSettings;
    }
}

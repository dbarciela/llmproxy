package io.github.dbarciela.aura.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class PluginSettingsManager {

	private final Map<String, JsonNode> pluginSettings = new ConcurrentHashMap<>();
	private final ObjectMapper mapper = new ObjectMapper();

	public JsonNode getSettings(String pluginId) {
		return pluginSettings.get(pluginId);
	}

	public <T> T getSettingsAs(String pluginId, Class<T> clazz) {
		JsonNode node = pluginSettings.get(pluginId);
		if (node == null) {
			return null;
		}
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

	public boolean isPluginEnabled(String pluginId) {
		JsonNode node = pluginSettings.get(pluginId);
		if (node != null && node.has("enabled")) {
			return node.get("enabled").asBoolean(true);
		}
		return true; // Default to true if no settings or no enabled flag
	}
}

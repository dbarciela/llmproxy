package io.github.dbarciela.aura.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.dbarciela.aura.config.PluginSettingsManager;
import io.github.dbarciela.aura.config.ProxySettings;
import io.github.dbarciela.aura.pipeline.ProxyPipeline;
import io.github.dbarciela.aura.pipeline.ProxyPlugin;

@RestController
@RequestMapping("/api/proxy")
public class SettingsController {

	private final ProxySettings settings;
	private final PluginSettingsManager pluginSettingsManager;
	private final ProxyPipeline proxyPipeline;

	public SettingsController(ProxySettings settings, PluginSettingsManager pluginSettingsManager,
			ProxyPipeline proxyPipeline) {
		this.settings = settings;
		this.pluginSettingsManager = pluginSettingsManager;
		this.proxyPipeline = proxyPipeline;
	}

	@GetMapping("/settings")
	public Map<String, Object> getSettings() {
		Map<String, Object> combined = new HashMap<>();
		combined.put("defaultTab", settings.getDefaultTab());
		combined.put("loggingEnabled", settings.isLoggingEnabled());
		combined.put("webUiUrl", settings.getWebUiUrl());

		// Return flat structure for backwards compatibility for a moment, or nested
		// plugins map
		combined.put("plugins", pluginSettingsManager.getAllSettings());

		return combined;
	}

	@PutMapping("/settings")
	public void updateSettings(@RequestBody Map<String, Object> newSettingsMap) {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode newSettings = mapper.valueToTree(newSettingsMap);
		if (newSettings.has("defaultTab")) {
			settings.setDefaultTab(newSettings.get("defaultTab").asText());
		}
		if (newSettings.has("loggingEnabled")) {
			settings.setLoggingEnabled(newSettings.get("loggingEnabled").asBoolean());
		}
	}

	@GetMapping("/plugins")
	public Map<String, JsonNode> getPluginSettings() {
		return pluginSettingsManager.getAllSettings();
	}

	@PutMapping("/plugins/{pluginId}/settings")
	public void updatePluginSettings(@PathVariable String pluginId, @RequestBody Map<String, Object> newSettingsMap) {
		ObjectMapper mapper = new ObjectMapper();
		pluginSettingsManager.updateSettings(pluginId, mapper.valueToTree(newSettingsMap));
	}

	@GetMapping("/plugins/metadata")
	public List<Map<String, Object>> getPluginMetadata() {
		List<Map<String, Object>> metadataList = new ArrayList<>();
		for (ProxyPlugin plugin : proxyPipeline.getPlugins()) {
			Map<String, Object> metadata = new HashMap<>();
			metadata.put("id", plugin.getId());
			metadata.put("name", plugin.getName());
			metadata.put("uiTabName", plugin.getUiTabName());
			metadata.put("hasUiToggle", plugin.hasUiToggle());
			metadata.put("isBuffering", plugin.isBuffering());
			metadata.put("isAsync", plugin.isAsync());
			metadata.put("description", plugin.getDescription());
			metadataList.add(metadata);
		}
		return metadataList;
	}

	@PutMapping("/plugins/order")
	public void updatePluginOrder(@RequestBody List<String> orderedIds) {
		proxyPipeline.setPluginOrder(orderedIds);
	}
}

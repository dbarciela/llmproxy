package io.github.dbarciela.aura.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.dbarciela.aura.config.PluginSettingsManager;
import io.github.dbarciela.aura.config.ProxySettings;

@RestController
@RequestMapping("/api/proxy")
public class SettingsController {

	private final ProxySettings settings;
	private final PluginSettingsManager pluginSettingsManager;
	private final io.github.dbarciela.aura.pipeline.ProxyPipeline proxyPipeline;

	public SettingsController(ProxySettings settings, PluginSettingsManager pluginSettingsManager,
			io.github.dbarciela.aura.pipeline.ProxyPipeline proxyPipeline) {
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
		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
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
		com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		pluginSettingsManager.updateSettings(pluginId, mapper.valueToTree(newSettingsMap));
	}

	@GetMapping("/plugins/metadata")
	public java.util.List<Map<String, Object>> getPluginMetadata() {
		java.util.List<Map<String, Object>> metadataList = new java.util.ArrayList<>();
		for (io.github.dbarciela.aura.pipeline.ProxyPlugin plugin : proxyPipeline.getPlugins()) {
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
	public void updatePluginOrder(@RequestBody java.util.List<String> orderedIds) {
		proxyPipeline.setPluginOrder(orderedIds);
	}
}

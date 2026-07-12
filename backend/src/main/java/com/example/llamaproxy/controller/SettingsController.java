package com.example.llamaproxy.controller;

import com.example.llamaproxy.config.ProxySettings;
import com.example.llamaproxy.config.PluginSettingsManager;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/proxy")
public class SettingsController {

    private final ProxySettings settings;
    private final PluginSettingsManager pluginSettingsManager;
    private final com.example.llamaproxy.pipeline.ProxyPipeline proxyPipeline;

    public SettingsController(ProxySettings settings, PluginSettingsManager pluginSettingsManager, com.example.llamaproxy.pipeline.ProxyPipeline proxyPipeline) {
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
        
        // Return flat structure for backwards compatibility for a moment, or nested plugins map
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
        for (com.example.llamaproxy.pipeline.ProxyPlugin plugin : proxyPipeline.getPlugins()) {
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

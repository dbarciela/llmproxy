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

    public SettingsController(ProxySettings settings, PluginSettingsManager pluginSettingsManager) {
        this.settings = settings;
        this.pluginSettingsManager = pluginSettingsManager;
    }

    @GetMapping("/settings")
    public Map<String, Object> getSettings() {
        Map<String, Object> combined = new HashMap<>();
        combined.put("interceptRequests", settings.isInterceptRequests());
        combined.put("interceptResponses", settings.isInterceptResponses());
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
        if (newSettings.has("interceptRequests")) {
            settings.setInterceptRequests(newSettings.get("interceptRequests").asBoolean());
        }
        if (newSettings.has("interceptResponses")) {
            settings.setInterceptResponses(newSettings.get("interceptResponses").asBoolean());
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
}

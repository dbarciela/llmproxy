package com.example.llamaproxy.controller;

import com.example.llamaproxy.config.ProxySettings;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/proxy")
public class SettingsController {

    private final ProxySettings settings;

    public SettingsController(ProxySettings settings) {
        this.settings = settings;
    }

    @GetMapping("/settings")
    public ProxySettings getSettings() {
        return settings;
    }

    @PutMapping("/settings")
    public ProxySettings updateSettings(@RequestBody ProxySettings newSettings) {
        settings.setInterceptRequests(newSettings.isInterceptRequests());
        settings.setInterceptResponses(newSettings.isInterceptResponses());
        settings.setLoggingEnabled(newSettings.isLoggingEnabled());
        if (newSettings.getInterceptRegex() != null) {
            settings.setInterceptRegex(newSettings.getInterceptRegex());
        } else {
            settings.setInterceptRegex("");
        }
        return settings;
    }
}

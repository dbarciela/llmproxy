package com.example.llamaproxy.controller;

import com.example.llamaproxy.pipeline.plugins.ContextDeduplicatorPlugin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/proxy/plugins/context-deduplicator")
public class ContextDeduplicatorController {

    private final ContextDeduplicatorPlugin plugin;

    public ContextDeduplicatorController(ContextDeduplicatorPlugin plugin) {
        this.plugin = plugin;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return plugin.getStats();
    }
}

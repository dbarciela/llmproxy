package com.example.llamaproxy.pipeline;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ProxyPipeline {
    
    private final List<ProxyPlugin> plugins;
    private final com.example.llamaproxy.config.PluginSettingsManager pluginSettingsManager;

    public ProxyPipeline(List<ProxyPlugin> plugins, com.example.llamaproxy.config.PluginSettingsManager pluginSettingsManager) {
        this.plugins = plugins;
        this.pluginSettingsManager = pluginSettingsManager;
        
        for (ProxyPlugin plugin : plugins) {
            pluginSettingsManager.registerDefaultSettings(plugin.getId(), plugin.getDefaultSettings());
        }
    }

    public void processRequest(RequestContext context) {
        for (ProxyPlugin plugin : plugins) {
            if (context.isDropped()) {
                break;
            }
            plugin.processRequest(context);
        }
    }

    public void processResponse(ResponseContext context) {
        // For response, we generally run plugins in the same order, or reverse order.
        // We'll run them in the same order for now.
        for (ProxyPlugin plugin : plugins) {
            plugin.processResponse(context);
        }
    }
}

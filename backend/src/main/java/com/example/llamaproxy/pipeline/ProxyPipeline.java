package com.example.llamaproxy.pipeline;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ProxyPipeline {
    
    private List<ProxyPlugin> plugins;
    private final com.example.llamaproxy.config.PluginSettingsManager pluginSettingsManager;

    public ProxyPipeline(List<ProxyPlugin> plugins, com.example.llamaproxy.config.PluginSettingsManager pluginSettingsManager) {
        // Create a mutable copy of the injected list, which is initially sorted by @Order
        this.plugins = new java.util.ArrayList<>(plugins);
        this.pluginSettingsManager = pluginSettingsManager;
        
        for (ProxyPlugin plugin : this.plugins) {
            pluginSettingsManager.registerDefaultSettings(plugin.getId(), plugin.getDefaultSettings());
        }
    }

    public List<ProxyPlugin> getPlugins() {
        return plugins;
    }

    public void setPluginOrder(List<String> orderedIds) {
        plugins.sort((p1, p2) -> {
            int idx1 = orderedIds.indexOf(p1.getId());
            int idx2 = orderedIds.indexOf(p2.getId());
            // If not found in the order list, keep them at the end or maintain relative order
            if (idx1 == -1) idx1 = Integer.MAX_VALUE;
            if (idx2 == -1) idx2 = Integer.MAX_VALUE;
            
            if (idx1 != idx2) {
                return Integer.compare(idx1, idx2);
            }
            // Fallback to default order
            return Integer.compare(p1.getDefaultOrder(), p2.getDefaultOrder());
        });
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

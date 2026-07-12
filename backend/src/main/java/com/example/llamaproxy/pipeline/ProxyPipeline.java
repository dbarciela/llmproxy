package com.example.llamaproxy.pipeline;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ProxyPipeline {
    
    private List<ProxyPlugin> plugins;
    private final com.example.llamaproxy.config.PluginSettingsManager pluginSettingsManager;
    private final java.util.concurrent.BlockingQueue<Runnable> asyncQueue = new java.util.concurrent.LinkedBlockingQueue<>();

    public ProxyPipeline(List<ProxyPlugin> plugins, com.example.llamaproxy.config.PluginSettingsManager pluginSettingsManager) {
        // Create a mutable copy of the injected list, which is initially sorted by @Order
        this.plugins = new java.util.ArrayList<>(plugins);
        this.pluginSettingsManager = pluginSettingsManager;
        
        for (ProxyPlugin plugin : this.plugins) {
            pluginSettingsManager.registerDefaultSettings(plugin.getId(), plugin.getDefaultSettings());
        }

        Thread.startVirtualThread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Runnable task = asyncQueue.take();
                    try {
                        task.run();
                    } catch (Exception e) {
                        e.printStackTrace(); // Prevent one bad plugin from crashing the worker
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
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
            if (plugin.isAsync()) {
                asyncQueue.offer(() -> plugin.processRequest(context));
            } else {
                plugin.processRequest(context);
            }
        }
    }

    public void processResponse(ResponseContext context) {
        for (ProxyPlugin plugin : plugins) {
            if (plugin.isAsync()) {
                asyncQueue.offer(() -> plugin.processResponse(context));
            } else {
                plugin.processResponse(context);
            }
        }
    }

    public void processChunk(String reqId, String chunk) {
        for (ProxyPlugin plugin : plugins) {
            if (plugin.isAsync()) {
                asyncQueue.offer(() -> plugin.processChunk(reqId, chunk));
            }
        }
    }

    public boolean requiresResponseBuffering() {
        com.example.llamaproxy.pipeline.plugins.ManualEditorPlugin.ManualEditorSettings manualSettings = 
            pluginSettingsManager.getSettingsAs("manual-editor", com.example.llamaproxy.pipeline.plugins.ManualEditorPlugin.ManualEditorSettings.class);
        if (manualSettings != null && manualSettings.enabled) {
            return true;
        }
        return false;
    }
}

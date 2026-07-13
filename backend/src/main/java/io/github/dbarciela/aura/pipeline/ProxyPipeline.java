package io.github.dbarciela.aura.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.stereotype.Component;

import io.github.dbarciela.aura.config.PluginSettingsManager;
import io.github.dbarciela.aura.pipeline.plugins.ManualEditorPlugin;

@Component
public class ProxyPipeline {

	private List<ProxyPlugin> plugins;
	private final PluginSettingsManager pluginSettingsManager;
	private final BlockingQueue<Runnable> asyncQueue = new LinkedBlockingQueue<>();

	public ProxyPipeline(List<ProxyPlugin> plugins,
			PluginSettingsManager pluginSettingsManager) {
		// Create a mutable copy of the injected list, which is initially sorted by
		// @Order
		this.plugins = new ArrayList<>(plugins);
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
			// If not found in the order list, keep them at the end or maintain relative
			// order
			if (idx1 == -1) {
				idx1 = Integer.MAX_VALUE;
			}
			if (idx2 == -1) {
				idx2 = Integer.MAX_VALUE;
			}

			if (idx1 != idx2) {
				return Integer.compare(idx1, idx2);
			}
			// Fallback to default order
			return Integer.compare(p1.getDefaultOrder(), p2.getDefaultOrder());
		});
	}

	private boolean shouldProcess(ProxyPlugin plugin) {
		if (!plugin.hasUiToggle() || plugin.runsWhenDisabled()) {
			return true;
		}
		return pluginSettingsManager.isPluginEnabled(plugin.getId());
	}

	public void processRequest(RequestContext context) {
		for (ProxyPlugin plugin : plugins) {
			if (context.isDropped()) {
				break;
			}
			if (!shouldProcess(plugin)) {
				continue;
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
			if (!shouldProcess(plugin)) {
				continue;
			}
			if (plugin.isAsync()) {
				asyncQueue.offer(() -> plugin.processResponse(context));
			} else {
				plugin.processResponse(context);
			}
		}
	}

	public void processChunk(String reqId, String chunk) {
		for (ProxyPlugin plugin : plugins) {
			if (!shouldProcess(plugin)) {
				continue;
			}
			if (plugin.isAsync()) {
				asyncQueue.offer(() -> plugin.processChunk(reqId, chunk));
			}
		}
	}

	public boolean requiresResponseBuffering() {
		ManualEditorPlugin.ManualEditorSettings manualSettings = pluginSettingsManager
				.getSettingsAs("manual-editor",
						ManualEditorPlugin.ManualEditorSettings.class);
		if (manualSettings != null && manualSettings.enabled) {
			return true;
		}
		return false;
	}
}

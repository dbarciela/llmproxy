package io.github.dbarciela.aura.pipeline;

public interface ProxyPlugin {
	/**
	 * Process the request before it is sent to the target server. Can mutate the
	 * request payload, or block the thread (e.g., for manual editing). If the
	 * plugin marks the context as dropped, the pipeline will abort.
	 */
	void processRequest(RequestContext context);

	/**
	 * Unique ID of the plugin (e.g. "context-deduplicator")
	 */
	String getId();

	/**
	 * Human-readable name of the plugin
	 */
	String getName();

	/**
	 * Brief description of what the plugin does
	 */
	String getDescription();

	/**
	 * Default settings for this plugin (will be serialized to JSON)
	 */
	Object getDefaultSettings();

	/**
	 * The name to display in the UI tab (e.g. "Deduplicator")
	 */
	default String getUiTabName() {
		return getName();
	}

	/**
	 * Whether this plugin has a global toggle in the UI.
	 */
	default boolean hasUiToggle() {
		return false;
	}

	/**
	 * If true, the pipeline will deliver events to this plugin even if its UI
	 * toggle is off. If false (default), the pipeline will automatically check
	 * PluginSettingsManager and skip it if disabled.
	 */
	default boolean runsWhenDisabled() {
		return false;
	}

	/**
	 * The default order of execution in the pipeline.
	 */
	default int getDefaultOrder() {
		return 100;
	}

	/**
	 * Indicates if this plugin requires buffering the entire payload in memory
	 * (String). Streaming plugins (return false) will process data on the fly
	 * (InputStream/OutputStream).
	 */
	default boolean isBuffering() {
		return true;
	}

	/**
	 * Indicates if this plugin is asynchronous (doesn't block the proxy pipeline).
	 * Async plugins receive events in a background queue.
	 */
	default boolean isAsync() {
		return false;
	}

	/**
	 * Process a real-time chunk during streaming. Primarily used by AsyncPlugins
	 * (e.g. LiveChat).
	 */
	default void processChunk(String reqId, String chunk) {
	}

	/**
	 * Process the response after it is received from the target server.
	 */
	void processResponse(ResponseContext context);
}

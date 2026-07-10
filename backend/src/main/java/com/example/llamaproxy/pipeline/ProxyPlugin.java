package com.example.llamaproxy.pipeline;

public interface ProxyPlugin {
    /**
     * Process the request before it is sent to the target server.
     * Can mutate the request payload, or block the thread (e.g., for manual editing).
     * If the plugin marks the context as dropped, the pipeline will abort.
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
     * Process the response after it is received from the target server.
     */
    void processResponse(ResponseContext context);
}

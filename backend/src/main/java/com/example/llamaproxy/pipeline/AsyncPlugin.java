package com.example.llamaproxy.pipeline;

/**
 * A plugin that processes requests and responses out-of-band in a background thread.
 * Its output does not affect the actual payload sent to the LLM or the client.
 */
public interface AsyncPlugin extends ProxyPlugin {
    
    @Override
    default boolean isBuffering() {
        return false; // Async plugins don't buffer on the main hot path
    }

    @Override
    default boolean isAsync() {
        return true;
    }
}

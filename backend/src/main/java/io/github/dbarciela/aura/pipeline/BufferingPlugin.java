package io.github.dbarciela.aura.pipeline;

/**
 * A plugin that requires buffering the entire request/response payload into a String
 * before processing. This can have a large memory footprint for big payloads.
 */
public interface BufferingPlugin extends ProxyPlugin {
    @Override
    default boolean isBuffering() {
        return true;
    }
}

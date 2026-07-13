package io.github.dbarciela.aura.pipeline;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * A plugin that processes requests/responses as a stream, without holding the entire
 * payload in memory. Ideal for low-memory footprint processing.
 */
public interface StreamingPlugin extends ProxyPlugin {
    @Override
    default boolean isBuffering() {
        return false;
    }

    /**
     * Process the request payload as a stream.
     */
    void processRequestStream(InputStream in, OutputStream out, RequestContext context) throws IOException;

    /**
     * Process the response payload as a stream.
     */
    void processResponseStream(InputStream in, OutputStream out, ResponseContext context) throws IOException;

    @Override
    default void processRequest(RequestContext context) {
        throw new UnsupportedOperationException("StreamingPlugin must use processRequestStream");
    }

    @Override
    default void processResponse(ResponseContext context) {
        throw new UnsupportedOperationException("StreamingPlugin must use processResponseStream");
    }
}

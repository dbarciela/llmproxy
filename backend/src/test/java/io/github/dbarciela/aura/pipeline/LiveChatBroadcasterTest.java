package io.github.dbarciela.aura.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LiveChatBroadcasterTest {

    private LiveChatBroadcaster broadcaster;

    @BeforeEach
    public void setup() {
        broadcaster = new LiveChatBroadcaster();
    }

    @Test
    public void testSubscribe() {
        SseEmitter emitter = broadcaster.subscribe();
        assertNotNull(emitter);
    }

    @Test
    public void testBroadcastRequest_NoSubscribers() {
        // Should not throw exceptions if there are no subscribers
        broadcaster.broadcastRequest("id", "payload");
    }

    @Test
    public void testBroadcastChunk_NoSubscribers() {
        broadcaster.broadcastChunk("id", "chunk");
    }
    
    @Test
    public void testBroadcastHardware_NoSubscribers() {
        broadcaster.broadcastHardware("{}");
    }
}

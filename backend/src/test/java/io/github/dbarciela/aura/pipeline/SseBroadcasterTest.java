package io.github.dbarciela.aura.pipeline;

import static org.junit.jupiter.api.Assertions.assertNotNull;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseBroadcasterTest {

	private SseBroadcaster broadcaster;

	@BeforeEach
	public void setup() {
		broadcaster = new SseBroadcaster();
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

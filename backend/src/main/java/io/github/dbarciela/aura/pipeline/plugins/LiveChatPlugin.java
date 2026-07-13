package io.github.dbarciela.aura.pipeline.plugins;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.github.dbarciela.aura.pipeline.AsyncPlugin;
import io.github.dbarciela.aura.pipeline.LiveChatBroadcaster;
import io.github.dbarciela.aura.pipeline.RequestContext;
import io.github.dbarciela.aura.pipeline.ResponseContext;

@Component
@Order(100)
public class LiveChatPlugin implements AsyncPlugin {

	public static class LiveChatSettings {
		public boolean enabled = true;
	}

	private final LiveChatBroadcaster broadcaster;

	public LiveChatPlugin(LiveChatBroadcaster broadcaster) {
		this.broadcaster = broadcaster;
	}

	@Override
	public String getId() {
		return "live-chat-plugin";
	}

	@Override
	public String getName() {
		return "Live Chat Interface";
	}

	@Override
	public String getDescription() {
		return "Broadcasts SSE events to the UI Live Chat.";
	}

	@Override
	public Object getDefaultSettings() {
		return new LiveChatSettings();
	}

	@Override
	public String getUiTabName() {
		return "Live Chat";
	}

	@Override
	public boolean hasUiToggle() {
		return true;
	}

	@Override
	public int getDefaultOrder() {
		return 100;
	}

	@Override
	public void processRequest(RequestContext context) {
		broadcaster.broadcastRequest(context.getId(), context.getPayload());
	}

	@Override
	public void processResponse(ResponseContext context) {
		RequestContext reqCtx = context.getRequestContext();
		if (!reqCtx.isDropped()) {
			broadcaster.broadcastDone(reqCtx.getId(), context.getPayload());
		} else {
			broadcaster.broadcastDone(reqCtx.getId(), "");
		}
	}

	@Override
	public void processChunk(String reqId, String chunk) {
		broadcaster.broadcastChunk(reqId, chunk);
	}
}

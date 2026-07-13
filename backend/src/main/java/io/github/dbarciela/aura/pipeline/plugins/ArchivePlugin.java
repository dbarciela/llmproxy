package io.github.dbarciela.aura.pipeline.plugins;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.github.dbarciela.aura.config.ProxySettings;
import io.github.dbarciela.aura.db.SessionHistoryRepository;
import io.github.dbarciela.aura.pipeline.AsyncPlugin;
import io.github.dbarciela.aura.pipeline.RequestContext;
import io.github.dbarciela.aura.pipeline.ResponseContext;

@Component
@Order(50)
public class ArchivePlugin implements AsyncPlugin {

	public static class ArchiveSettings {
		public boolean enabled = true;
	}

	@Override
	public String getId() {
		return "archive";
	}

	@Override
	public String getName() {
		return "Network Logs Archive";
	}

	@Override
	public String getDescription() {
		return "Archives all requests and responses.";
	}

	@Override
	public Object getDefaultSettings() {
		return new ArchiveSettings();
	}

	@Override
	public String getUiTabName() {
		return "Network Logs";
	}

	@Override
	public boolean hasUiToggle() {
		return true;
	}

	@Override
	public int getDefaultOrder() {
		return 50;
	}

	private final ProxySettings settings;
	private final SessionHistoryRepository repository;
	private final io.github.dbarciela.aura.pipeline.NotificationService notificationService;

	public ArchivePlugin(ProxySettings settings, SessionHistoryRepository repository,
			io.github.dbarciela.aura.pipeline.NotificationService notificationService) {
		this.settings = settings;
		this.repository = repository;
		this.notificationService = notificationService;
	}

	@Override
	public void processRequest(RequestContext context) {
		// Pass-through
	}

	@Override
	public void processResponse(ResponseContext context) {
		if (!settings.isLoggingEnabled()) {
			return;
		}

		RequestContext reqCtx = context.getRequestContext();
		String id = reqCtx.getId();
		String endpoint = reqCtx.getUri();
		int statusCode = context.getStatusCode();

		// Construct the full payload containing both request and response for
		// searchability
		String fullPayload = "REQUEST:\n" + reqCtx.getPayload() + "\n\nRESPONSE:\n" + context.getPayload();

		// Save asynchronously (handled by ProxyPipeline)
		repository.save(id, endpoint, statusCode, fullPayload);

		if (!"archive".equals(notificationService.getActiveTab()) && !notificationService.hasUnreadNotification("archive")) {
			io.github.dbarciela.aura.pipeline.NotificationDTO n = new io.github.dbarciela.aura.pipeline.NotificationDTO();
			n.setSourcePlugin("archive");
			n.setTitle("New Session Logged");
			n.setMessage("A new chat session was just archived.");
			n.setLevel("info");
			n.setActions(java.util.List.of(new io.github.dbarciela.aura.pipeline.NotificationDTO.NotificationAction(
					"View Log", null, null, "archive")));
			notificationService.addNotification(n);
		}
	}
}

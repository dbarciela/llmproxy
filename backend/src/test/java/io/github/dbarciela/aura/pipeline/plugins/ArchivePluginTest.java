package io.github.dbarciela.aura.pipeline.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import io.github.dbarciela.aura.config.ProxySettings;
import io.github.dbarciela.aura.db.SessionHistoryRepository;
import io.github.dbarciela.aura.pipeline.NotificationService;
import io.github.dbarciela.aura.pipeline.RequestContext;
import io.github.dbarciela.aura.pipeline.ResponseContext;

public class ArchivePluginTest {

	@Mock
	private ProxySettings settings;

	@Mock
	private SessionHistoryRepository repository;

	@Mock
	private NotificationService notificationService;

	private ArchivePlugin plugin;

	@BeforeEach
	public void setup() {
		MockitoAnnotations.openMocks(this);
		plugin = new ArchivePlugin(settings, repository, notificationService);
	}

	@Test
	public void testProcessResponse_LoggingDisabled() {
		when(settings.isLoggingEnabled()).thenReturn(false);

		RequestContext req = new RequestContext(HttpMethod.POST, "/v1/chat", new HttpHeaders(), "req-payload");
		ResponseContext res = new ResponseContext(req, 200, new HttpHeaders(), "res-payload");

		plugin.processResponse(res);

		verify(repository, never()).save(any(), any(), anyInt(), any());
	}

	@Test
	public void testProcessResponse_LoggingEnabled() throws InterruptedException {
		when(settings.isLoggingEnabled()).thenReturn(true);
		when(notificationService.getActiveTab()).thenReturn("other-tab");
		when(notificationService.hasUnreadNotification("archive")).thenReturn(false);

		RequestContext req = new RequestContext(HttpMethod.POST, "/v1/chat", new HttpHeaders(), "req-payload");
		ResponseContext res = new ResponseContext(req, 200, new HttpHeaders(), "res-payload");

		plugin.processResponse(res);

		// Wait for virtual thread to execute
		Thread.sleep(100);

		ArgumentCaptor<String> fullPayloadCaptor = ArgumentCaptor.forClass(String.class);
		verify(repository).save(eq(req.getId()), eq("/v1/chat"), eq(200), fullPayloadCaptor.capture());

		String capturedPayload = fullPayloadCaptor.getValue();
		assertEquals("REQUEST:\nreq-payload\n\nRESPONSE:\nres-payload", capturedPayload);

		verify(notificationService).addNotification(any());
	}
}

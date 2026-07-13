package io.github.dbarciela.aura.pipeline.plugins;

import io.github.dbarciela.aura.config.PluginSettingsManager;
import io.github.dbarciela.aura.pipeline.NotificationService;
import io.github.dbarciela.aura.pipeline.RequestContext;
import io.github.dbarciela.aura.pipeline.ResponseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ManualEditorPluginTest {

    @Mock
    private PluginSettingsManager settingsManager;

    @Mock
    private NotificationService notificationService;

    private ManualEditorPlugin plugin;
    private ManualEditorPlugin.ManualEditorSettings settings;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        plugin = new ManualEditorPlugin(settingsManager, notificationService);
        settings = new ManualEditorPlugin.ManualEditorSettings();
        when(settingsManager.getSettingsAs(eq("manual-editor"), eq(ManualEditorPlugin.ManualEditorSettings.class)))
                .thenReturn(settings);
    }

    @Test
    public void testProcessRequest_NotIntercepting() {
        settings.enabled = false;
        RequestContext req = new RequestContext(HttpMethod.POST, "/v1/chat", new HttpHeaders(), "payload");

        plugin.processRequest(req);

        assertEquals(0, plugin.getQueue().size());
        assertFalse(req.isDropped());
    }

    @Test
    public void testProcessRequest_InterceptAll() throws InterruptedException {
        settings.enabled = true;
        RequestContext req = new RequestContext(HttpMethod.POST, "/v1/chat", new HttpHeaders(), "payload");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> plugin.processRequest(req));

        // Wait for thread to hit the latch
        Thread.sleep(100);

        assertEquals(1, plugin.getQueue().size());
        assertEquals(req.getId() + "-req", plugin.getQueue().get(0).getId());

        // Release manually
        plugin.release(req.getId() + "-req", "new-payload");

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        assertEquals("new-payload", req.getPayload());
        assertEquals(0, plugin.getQueue().size());
    }

    @Test
    public void testProcessResponse_InterceptInvalidJson() throws InterruptedException {
        settings.enabled = true;
        settings.interceptInvalidJson = true;

        RequestContext req = new RequestContext(HttpMethod.POST, "/v1/chat", new HttpHeaders(), "req-payload");
        ResponseContext res = new ResponseContext(req, 200, new HttpHeaders(), "invalid { json");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> plugin.processResponse(res));

        Thread.sleep(100);

        assertEquals(1, plugin.getQueue().size());
        
        plugin.drop(req.getId() + "-res");

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);

        assertTrue(req.isDropped());
    }

    @Test
    public void testProcessRequest_RegexMatch() throws InterruptedException {
        settings.enabled = true;
        settings.interceptRegexRules = List.of("bad-word");

        RequestContext req = new RequestContext(HttpMethod.POST, "/v1/chat", new HttpHeaders(), "this has a bad-word in it");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> plugin.processRequest(req));

        Thread.sleep(100);

        assertEquals(1, plugin.getQueue().size());
        plugin.release(req.getId() + "-req", "fixed payload");

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    public void testProcessRequest_RegexNoMatch() {
        settings.enabled = true;
        settings.interceptRegexRules = List.of("bad-word");

        RequestContext req = new RequestContext(HttpMethod.POST, "/v1/chat", new HttpHeaders(), "this is clean");

        plugin.processRequest(req);

        // Should not block or add to queue
        assertEquals(0, plugin.getQueue().size());
    }
}

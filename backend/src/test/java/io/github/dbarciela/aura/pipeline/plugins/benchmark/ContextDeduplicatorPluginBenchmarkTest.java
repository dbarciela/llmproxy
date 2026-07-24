package io.github.dbarciela.aura.pipeline.plugins.benchmark;

import io.github.dbarciela.aura.pipeline.plugins.ContextDeduplicatorPlugin;
import io.github.dbarciela.aura.config.PluginSettingsManager;
import io.github.dbarciela.aura.pipeline.SseBroadcaster;
import io.github.dbarciela.aura.pipeline.RequestContext;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

public class ContextDeduplicatorPluginBenchmarkTest {

    @Test
    public void testBenchmark() {
        PluginSettingsManager settingsManager = mock(PluginSettingsManager.class);
        SseBroadcaster broadcaster = mock(SseBroadcaster.class);

        ContextDeduplicatorPlugin.DeduplicatorSettings settings = new ContextDeduplicatorPlugin.DeduplicatorSettings();
        settings.enabled = true;
        settings.threshold = 50;
        when(settingsManager.getSettingsAs("context-deduplicator",
                ContextDeduplicatorPlugin.DeduplicatorSettings.class)).thenReturn(settings);

        ContextDeduplicatorPlugin plugin = new ContextDeduplicatorPlugin(settingsManager, broadcaster);

        // create a large payload
        StringBuilder sb = new StringBuilder();
        sb.append("{\"messages\":[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"role\":\"user\",\"content\":\"some message content that is relatively long enough to represent real chat history message " + i + " " + "A".repeat(100) + "\"}");
        }
        sb.append("]}");

        String payload = sb.toString();

        long start = System.nanoTime();
        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            RequestContext requestContext = new RequestContext(HttpMethod.POST, "http://localhost",
                new HttpHeaders(), payload);
            plugin.processRequest(requestContext);
        }
        long end = System.nanoTime();
        System.out.println("Benchmark time: " + (end - start) / 1_000_000.0 + " ms");
    }
}

package com.example.llamaproxy.pipeline;

import com.example.llamaproxy.config.PluginSettingsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.mockito.Mockito.*;

public class ProxyPipelineTest {

    private PluginSettingsManager settingsManager;
    private ProxyPlugin plugin1;
    private ProxyPlugin plugin2;

    @BeforeEach
    public void setup() {
        settingsManager = mock(PluginSettingsManager.class);
        plugin1 = mock(ProxyPlugin.class);
        plugin2 = mock(ProxyPlugin.class);

        when(plugin1.getId()).thenReturn("plugin-1");
        when(plugin1.getDefaultSettings()).thenReturn("settings-1");

        when(plugin2.getId()).thenReturn("plugin-2");
        when(plugin2.getDefaultSettings()).thenReturn("settings-2");
    }

    @Test
    public void testPipeline_RegistersSettings() {
        new ProxyPipeline(List.of(plugin1, plugin2), settingsManager);

        verify(settingsManager).registerDefaultSettings("plugin-1", "settings-1");
        verify(settingsManager).registerDefaultSettings("plugin-2", "settings-2");
    }

    @Test
    public void testProcessRequest_SequentialExecution() {
        ProxyPipeline pipeline = new ProxyPipeline(List.of(plugin1, plugin2), settingsManager);

        RequestContext req = new RequestContext(HttpMethod.POST, "/v1", new HttpHeaders(), "payload");
        pipeline.processRequest(req);

        InOrder inOrder = inOrder(plugin1, plugin2);
        inOrder.verify(plugin1).processRequest(req);
        inOrder.verify(plugin2).processRequest(req);
    }

    @Test
    public void testProcessRequest_HaltsIfDropped() {
        ProxyPipeline pipeline = new ProxyPipeline(List.of(plugin1, plugin2), settingsManager);

        RequestContext req = new RequestContext(HttpMethod.POST, "/v1", new HttpHeaders(), "payload");

        // Make plugin1 drop the request
        doAnswer(invocation -> {
            RequestContext ctx = invocation.getArgument(0);
            ctx.setDropped(true);
            return null;
        }).when(plugin1).processRequest(any(RequestContext.class));

        pipeline.processRequest(req);

        verify(plugin1).processRequest(req);
        verify(plugin2, never()).processRequest(req);
    }

    @Test
    public void testProcessResponse_SequentialExecution() {
        ProxyPipeline pipeline = new ProxyPipeline(List.of(plugin1, plugin2), settingsManager);

        RequestContext req = new RequestContext(HttpMethod.POST, "/v1", new HttpHeaders(), "payload");
        ResponseContext res = new ResponseContext(req, 200, new HttpHeaders(), "res-payload");

        pipeline.processResponse(res);

        InOrder inOrder = inOrder(plugin1, plugin2);
        inOrder.verify(plugin1).processResponse(res);
        inOrder.verify(plugin2).processResponse(res);
    }
}

package io.github.dbarciela.aura.pipeline.plugins;

import io.github.dbarciela.aura.pipeline.RequestContext;
import io.github.dbarciela.aura.pipeline.ResponseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FormatFixerPluginTest {

    private FormatFixerPlugin plugin;

    @BeforeEach
    public void setup() {
        plugin = new FormatFixerPlugin();
    }

    @Test
    public void testProcessRequest_PassThrough() {
        RequestContext req = new RequestContext(HttpMethod.POST, "/v1/chat", new HttpHeaders(), "req-payload");
        plugin.processRequest(req);
        assertEquals("req-payload", req.getPayload(), "Payload should not be modified");
    }

    @Test
    public void testProcessResponse_PassThrough() {
        RequestContext req = new RequestContext(HttpMethod.POST, "/v1/chat", new HttpHeaders(), "req-payload");
        ResponseContext res = new ResponseContext(req, 200, new HttpHeaders(), "res-payload");
        plugin.processResponse(res);
        assertEquals("res-payload", res.getPayload(), "Payload should not be modified");
    }
}

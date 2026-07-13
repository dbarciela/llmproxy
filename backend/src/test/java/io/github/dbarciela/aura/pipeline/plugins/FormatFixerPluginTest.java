package io.github.dbarciela.aura.pipeline.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import io.github.dbarciela.aura.pipeline.RequestContext;
import io.github.dbarciela.aura.pipeline.ResponseContext;

public class FormatFixerPluginTest {

	private FormatFixerPlugin plugin;

	@BeforeEach
	public void setup() {
		plugin = new FormatFixerPlugin();
	}

	@Test
	public void testProcessRequest_PassThrough() throws IOException {
		RequestContext req = new RequestContext(HttpMethod.POST, "/v1/chat", new HttpHeaders(), null);
		ByteArrayInputStream in = new ByteArrayInputStream("req-payload".getBytes(StandardCharsets.UTF_8));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		
		plugin.processRequestStream(in, out, req);
		assertEquals("req-payload", out.toString(StandardCharsets.UTF_8), "Payload should not be modified");
	}

	@Test
	public void testProcessResponse_PassThrough() throws IOException {
		RequestContext req = new RequestContext(HttpMethod.POST, "/v1/chat", new HttpHeaders(), null);
		ResponseContext res = new ResponseContext(req, 200, new HttpHeaders(), null);
		ByteArrayInputStream in = new ByteArrayInputStream("res-payload".getBytes(StandardCharsets.UTF_8));
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		plugin.processResponseStream(in, out, res);
		assertEquals("res-payload", out.toString(StandardCharsets.UTF_8), "Payload should not be modified");
	}
}

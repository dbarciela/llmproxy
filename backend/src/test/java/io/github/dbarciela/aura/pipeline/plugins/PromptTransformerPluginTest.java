package io.github.dbarciela.aura.pipeline.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import io.github.dbarciela.aura.config.PluginSettingsManager;
import io.github.dbarciela.aura.pipeline.RequestContext;
import io.github.dbarciela.aura.pipeline.ResponseContext;

public class PromptTransformerPluginTest {

	@Mock
	private PluginSettingsManager settingsManager;

	private PromptTransformerPlugin plugin;
	private PromptTransformerPlugin.TransformerSettings settings;

	@BeforeEach
	public void setup() {
		MockitoAnnotations.openMocks(this);
		plugin = new PromptTransformerPlugin(settingsManager);
		settings = new PromptTransformerPlugin.TransformerSettings();
		when(settingsManager.getSettingsAs(eq("prompt-transformer"),
				eq(PromptTransformerPlugin.TransformerSettings.class))).thenReturn(settings);
	}

	@Test
	public void testPromptReplace_NoRules() {
		RequestContext context = new RequestContext(HttpMethod.POST, "/v1/chat/completions", new HttpHeaders(),
				"Hello World");
		plugin.processRequest(context);
		assertEquals("Hello World", context.getPayload(), "Payload should remain unchanged");
	}

	@Test
	public void testPromptReplace_BasicRegex() {
		settings.promptReplaceRules = List.of(new PromptTransformerPlugin.PromptReplaceRule("World", "Universe"));
		RequestContext context = new RequestContext(HttpMethod.POST, "/v1/chat/completions", new HttpHeaders(),
				"Hello World");
		plugin.processRequest(context);
		assertEquals("Hello Universe", context.getPayload(), "Regex should replace World with Universe");
	}

	@Test
	public void testPromptReplace_AdvancedRegex() {
		settings.promptReplaceRules = List.of(new PromptTransformerPlugin.PromptReplaceRule("\\b[A-Z]{3}\\b", "XXX"));
		RequestContext context = new RequestContext(HttpMethod.POST, "/v1/chat/completions", new HttpHeaders(),
				"My code is ABC and DEF.");
		plugin.processRequest(context);
		assertEquals("My code is XXX and XXX.", context.getPayload());
	}

	@Test
	public void testResponseReplace_BasicRegex() {
		settings.responseReplaceRules = List.of(new PromptTransformerPlugin.PromptReplaceRule("badword", "****"));
		RequestContext req = new RequestContext(HttpMethod.POST, "/v1/chat/completions", new HttpHeaders(), "req");
		ResponseContext context = new ResponseContext(req, 200, new HttpHeaders(), "This is a badword.");
		plugin.processResponse(context);
		assertEquals("This is a ****.", context.getPayload());
	}

	@Test
	public void testReplace_NullPayload() {
		settings.promptReplaceRules = List.of(new PromptTransformerPlugin.PromptReplaceRule("World", "Universe"));
		RequestContext context = new RequestContext(HttpMethod.POST, "/v1/chat/completions", new HttpHeaders(), null);
		plugin.processRequest(context);
		assertEquals(null, context.getPayload(), "Should handle null payload gracefully");
	}
}

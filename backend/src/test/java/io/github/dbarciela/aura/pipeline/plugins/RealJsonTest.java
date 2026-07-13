package io.github.dbarciela.aura.pipeline.plugins;

import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import io.github.dbarciela.aura.config.PluginSettingsManager;
import io.github.dbarciela.aura.pipeline.LiveChatBroadcaster;
import io.github.dbarciela.aura.pipeline.RequestContext;

@ExtendWith(MockitoExtension.class)
public class RealJsonTest {

	@Mock
	private PluginSettingsManager settingsManager;

	@Mock
	private LiveChatBroadcaster broadcaster;

	@Test
	public void testGains() throws Exception {
		ContextDeduplicatorPlugin plugin = new ContextDeduplicatorPlugin(settingsManager, broadcaster);

		ContextDeduplicatorPlugin.DeduplicatorSettings settings = new ContextDeduplicatorPlugin.DeduplicatorSettings();
		settings.enabled = true;
		settings.threshold = 250; // REDUCED TO 250
		when(settingsManager.getSettingsAs("context-deduplicator",
				ContextDeduplicatorPlugin.DeduplicatorSettings.class)).thenReturn(settings);

		String[] files = { "session-013e86d7-44d5-446d-a6c7-f3a7f3f47958.json",
				"session-281f8fea-b662-4db7-82b9-065b2816e320.json",
				"session-8ae51dd3-7e0c-42b7-9cd1-acb4425db805.json" };

		for (String file : files) {
			String path = "c:/ai/workspace/llmproxy/backend/" + file;
			List<String> lines = Files.readAllLines(Paths.get(path));
			String payload = lines.get(1); // the payload is on line 2

			RequestContext context = new RequestContext(HttpMethod.POST, "http://localhost", new HttpHeaders(),
					payload);

			int originalSize = payload.length();
			plugin.processRequest(context);
			int newSize = context.getPayload().length();

			int savedChars = originalSize - newSize;
			double percentage = (double) savedChars / originalSize * 100;

			System.out.println("TEST_RESULT_START");
			System.out.println("File: " + file);
			System.out.println("Original Size: " + originalSize);
			System.out.println("New Size: " + newSize);
			System.out.println("Saved: " + savedChars + " chars (" + String.format("%.2f", percentage) + "%)");
			System.out.println("TEST_RESULT_END");
		}
	}
}

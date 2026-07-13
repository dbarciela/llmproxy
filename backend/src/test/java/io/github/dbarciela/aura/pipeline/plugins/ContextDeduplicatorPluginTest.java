package io.github.dbarciela.aura.pipeline.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
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
class ContextDeduplicatorPluginTest {

	@Mock
	private PluginSettingsManager settingsManager;

	@Mock
	private LiveChatBroadcaster broadcaster;

	private ContextDeduplicatorPlugin plugin;
	private RequestContext requestContext;

	@BeforeEach
	void setUp() {
		plugin = new ContextDeduplicatorPlugin(settingsManager, broadcaster);
		requestContext = new RequestContext(HttpMethod.POST, "http://localhost",
				new HttpHeaders(), "{}");
	}

	private void mockSettings(boolean enabled, int threshold) {
		ContextDeduplicatorPlugin.DeduplicatorSettings settings = new ContextDeduplicatorPlugin.DeduplicatorSettings();
		settings.enabled = enabled;
		settings.threshold = threshold;
		when(settingsManager.getSettingsAs("context-deduplicator",
				ContextDeduplicatorPlugin.DeduplicatorSettings.class)).thenReturn(settings);
	}

	@Test
	void testPluginDisabled() {
		mockSettings(false, 500);
		String originalPayload = "{\"messages\":[{\"role\":\"user\",\"content\":\"some text\"}]}";
		requestContext.setPayload(originalPayload);

		plugin.processRequest(requestContext);

		assertEquals(originalPayload, requestContext.getPayload(), "Payload should not change when plugin is disabled");
		verify(broadcaster, never()).broadcastPluginEvent(any(), any(), any());
	}

	@Test
	void testNoMessagesArray() {
		mockSettings(true, 500);
		String originalPayload = "{\"prompt\":\"some text\"}";
		requestContext.setPayload(originalPayload);

		plugin.processRequest(requestContext);

		assertEquals(originalPayload, requestContext.getPayload(),
				"Payload should not change if there are no messages");
	}

	@Test
	void testInsufficientHistory() {
		mockSettings(true, 500);
		String originalPayload = "{\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}";
		requestContext.setPayload(originalPayload);

		plugin.processRequest(requestContext);

		assertEquals(originalPayload, requestContext.getPayload(), "Payload should not change if history is empty");
	}

	@Test
	void testNoDeduplicationBelowThreshold() {
		mockSettings(true, 100);

		// Match is exactly 30 characters (smaller than threshold 100)
		String history = "This is a small match that will not be deduplicated.";
		String current = "This is a small match that will not be deduplicated.";

		String originalPayload = String.format(
				"{\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}, {\"role\":\"user\",\"content\":\"%s\"}]}",
				history, current);
		requestContext.setPayload(originalPayload);

		plugin.processRequest(requestContext);

		assertEquals(originalPayload, requestContext.getPayload(),
				"Payload should not change if duplicated text is below threshold");
	}

	@Test
	void testDeduplicationAboveThreshold() {
		mockSettings(true, 50); // Threshold 50 chars

		// Exact 82 chars
		String duplicatedChunk = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+=-[]{}|;";

		String history = "Previous context: " + duplicatedChunk + " some other stuff";
		String current = "Current prompt includes: " + duplicatedChunk + " and something else.";

		String originalPayload = String.format(
				"{\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}, {\"role\":\"user\",\"content\":\"%s\"}]}",
				history, current);
		requestContext.setPayload(originalPayload);

		plugin.processRequest(requestContext);

		String newPayload = requestContext.getPayload();
		assertNotEquals(originalPayload, newPayload, "Payload should be modified");

		// Verify it contains the replacement token
		assertTrue(newPayload.contains("85 chars"), "Expected 85 chars in: " + newPayload);

		// Check that broadcaster was called
		verify(broadcaster).broadcastPluginEvent(eq("context-deduplicator"), eq("DEDUPLICATION_STATS"), any());
	}

	@Test
	void testMinimumThresholdLimitEdgeCase() {
		// Plugin should enforce a minimum threshold of 50 even if settings say 10
		mockSettings(true, 10);

		// Chunk is 40 chars long
		String duplicatedChunk = "1234567890123456789012345678901234567890";

		String history = "Context " + duplicatedChunk;
		String current = "Prompt " + duplicatedChunk;

		String originalPayload = String.format(
				"{\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}, {\"role\":\"user\",\"content\":\"%s\"}]}",
				history, current);
		requestContext.setPayload(originalPayload);

		plugin.processRequest(requestContext);

		// Since chunk is 40, and enforced threshold is 50, it shouldn't modify it!
		assertEquals(originalPayload, requestContext.getPayload());
	}

	@Test
	void testMultipleBlocksInOneMessage() {
		mockSettings(true, 50);

		String block1 = "This is the first highly duplicated block that should trigger a replacement because it is over fifty characters long.";
		String block2 = "This is the second highly duplicated block that should also trigger a replacement because it is over fifty characters long too.";

		String history = block1 + " [some random noise] " + block2;
		String current = "User says: " + block1 + " then adds: " + block2 + " and finishes.";

		String originalPayload = String.format(
				"{\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}, {\"role\":\"user\",\"content\":\"%s\"}]}",
				history, current);
		requestContext.setPayload(originalPayload);

		plugin.processRequest(requestContext);

		String newPayload = requestContext.getPayload();
		assertTrue(newPayload.contains("115 chars") || newPayload.contains("118 chars"),
				"First block not replaced: " + newPayload);
		assertTrue(newPayload.contains("125 chars") || newPayload.contains("128 chars"),
				"Second block not replaced: " + newPayload);
	}

	@Test
	void testOverlapEdgeCase() {
		mockSettings(true, 50);

		// Testing expanding forwards and backwards smoothly over identical characters
		// Sequence of repeated characters
		String block = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // 100
																																// As

		String history = "Past " + block;
		String current = "Now " + block;

		String originalPayload = String.format(
				"{\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}, {\"role\":\"user\",\"content\":\"%s\"}]}",
				history, current);
		requestContext.setPayload(originalPayload);

		plugin.processRequest(requestContext);

		String newPayload = requestContext.getPayload();
		assertTrue(newPayload.contains("79 chars") || newPayload.contains("100 chars"),
				"Overlap test failed: " + newPayload);
	}

	@Test
	void testComplexMixedInformationDeduplication() {
		mockSettings(true, 50);

		// Simulated large context payload with code blocks, JSON, and hidden
		// duplication
		String duplicateCodeBlock = "public void process(Data input) {\n    if (input == null) return;\n    input.sanitize();\n    repository.save(input);\n    logger.info(\"Data processed successfully\");\n}";
		String duplicateJsonData = "{\"id\": 12345, \"name\": \"Complex Test Case\", \"metadata\": {\"created\": \"2026-07-10\", \"status\": \"active\", \"tags\": [\"test\", \"complex\", \"deduplication\"]}}";

		String historyMsg1 = "Here is the code block I am working on:\n```java\n" + duplicateCodeBlock
				+ "\n```\nWhat do you think?";
		String historyMsg2 = "That looks good. You might also want to check the JSON output.";
		String historyMsg3 = "Yes, the JSON output is:\n```json\n" + duplicateJsonData + "\n```";

		// The current message will resend both the code block and the json data mixed
		// with other noise
		String currentMsg = "I re-ran the tests. For context, the code was:\n" + duplicateCodeBlock
				+ "\nAnd it returned this JSON again: \n" + duplicateJsonData
				+ "\nCan you tell me why the status is active?";

		// Construct the full JSON payload
		String originalPayload = String.format(
				"{\"messages\":[" + "{\"role\":\"user\",\"content\":\"%s\"},"
						+ "{\"role\":\"assistant\",\"content\":\"%s\"}," + "{\"role\":\"user\",\"content\":\"%s\"},"
						+ "{\"role\":\"user\",\"content\":\"%s\"}" + "]}",
				historyMsg1.replace("\"", "\\\"").replace("\n", "\\n"),
				historyMsg2.replace("\"", "\\\"").replace("\n", "\\n"),
				historyMsg3.replace("\"", "\\\"").replace("\n", "\\n"),
				currentMsg.replace("\"", "\\\"").replace("\n", "\\n"));

		requestContext.setPayload(originalPayload);
		plugin.processRequest(requestContext);

		String newPayload = requestContext.getPayload();
		assertNotEquals(originalPayload, newPayload, "Payload should be modified due to duplications");

		// Assert that both the code block and JSON data were caught and replaced with
		// markers
		assertTrue(newPayload.contains("Duplicated context omitted"), "Should contain deduplication markers");

		// The exact contents shouldn't be fully present in the final prompt if
		// deduplicated
		assertFalse(newPayload.substring(newPayload.lastIndexOf("I re-ran the tests")).contains("tags"),
				"JSON block should be removed from current prompt");
	}
}

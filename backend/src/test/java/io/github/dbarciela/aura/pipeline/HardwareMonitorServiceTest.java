package io.github.dbarciela.aura.pipeline;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class HardwareMonitorServiceTest {

	@Mock
	private LiveChatBroadcaster broadcaster;

	private HardwareMonitorService service;

	@BeforeEach
	public void setup() {
		MockitoAnnotations.openMocks(this);
		// OSHI initializes real hardware stats under the hood
		service = new HardwareMonitorService(broadcaster);
	}

	@Test
	public void testBroadcastHardwareStats() {
		// Assert it does not throw any exceptions (like NullPointerException) when
		// gathering hardware stats
		assertDoesNotThrow(() -> service.broadcastHardwareStats());

		// Verify it attempts to broadcast the JSON string
		verify(broadcaster).broadcastHardware(anyString());
	}
}

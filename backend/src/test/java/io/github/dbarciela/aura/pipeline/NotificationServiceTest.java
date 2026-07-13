package io.github.dbarciela.aura.pipeline;

import io.github.dbarciela.aura.db.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class NotificationServiceTest {

    @Mock
    private LiveChatBroadcaster broadcaster;

    @Mock
    private NotificationRepository repository;

    private NotificationService service;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        service = new NotificationService(broadcaster, repository);
    }

    @Test
    public void testGetAndSetActiveTab() {
        assertEquals("intercept", service.getActiveTab());
        service.setActiveTab("archive");
        assertEquals("archive", service.getActiveTab());
    }

    @Test
    public void testHasUnreadNotification() {
        when(repository.existsBySourcePlugin("test-plugin")).thenReturn(true);
        assertTrue(service.hasUnreadNotification("test-plugin"));
    }

    @Test
    public void testAddNotification() {
        NotificationDTO dto = new NotificationDTO();
        dto.setSourcePlugin("plugin-1");
        dto.setTitle("Test Title");

        service.addNotification(dto);

        verify(repository).save(dto);
        verify(broadcaster).broadcastNotificationData(anyString());
        assertNotNull(dto.getId(), "ID should be generated if null");
    }

    @Test
    public void testGetUnreadNotifications() {
        NotificationDTO dto = new NotificationDTO();
        when(repository.findAll()).thenReturn(List.of(dto));

        List<NotificationDTO> unread = service.getUnreadNotifications();
        assertEquals(1, unread.size());
    }

    @Test
    public void testMarkAsRead() {
        service.markAsRead("id-123");
        verify(repository).deleteById("id-123");
    }
}

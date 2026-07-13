package io.github.dbarciela.aura.pipeline;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.dbarciela.aura.db.NotificationRepository;

@Service
public class NotificationService {

	private final LiveChatBroadcaster broadcaster;
	private final NotificationRepository repository;
	private String activeTab = "intercept";

	public NotificationService(LiveChatBroadcaster broadcaster, NotificationRepository repository) {
		this.broadcaster = broadcaster;
		this.repository = repository;
	}

	public String getActiveTab() {
		return activeTab;
	}

	public void setActiveTab(String activeTab) {
		this.activeTab = activeTab;
	}

	public boolean hasUnreadNotification(String sourcePlugin) {
		return repository.existsBySourcePlugin(sourcePlugin);
	}

	public void addNotification(NotificationDTO notification) {
		if (notification.getId() == null) {
			notification.setId(UUID.randomUUID().toString());
		}

		repository.save(notification);

		try {
			ObjectMapper mapper = new ObjectMapper();
			String json = mapper.writeValueAsString(notification);
			// Send the literal JSON string through SSE
			broadcaster.broadcastNotificationData(json);
		} catch (Exception e) {
			// ignore
		}
	}

	public List<NotificationDTO> getUnreadNotifications() {
		return repository.findAll();
	}

	public void markAsRead(String id) {
		repository.deleteById(id);
	}
}

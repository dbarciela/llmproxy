package io.github.dbarciela.aura.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.dbarciela.aura.pipeline.NotificationDTO;
import io.github.dbarciela.aura.pipeline.NotificationService;

@RestController
@RequestMapping("/api/proxy")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping("/notifications")
	public List<NotificationDTO> getUnreadNotifications() {
		return notificationService.getUnreadNotifications();
	}

	@PostMapping("/notifications/{id}/read")
	public void markAsRead(@PathVariable String id) {
		notificationService.markAsRead(id);
	}

	@PostMapping("/ui/active-tab")
	public void setActiveTab(@RequestBody Map<String, String> payload) {
		String tab = payload.get("tab");
		if (tab != null) {
			notificationService.setActiveTab(tab);
		}
	}
}

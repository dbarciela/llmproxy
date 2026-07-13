package io.github.dbarciela.aura.controller;

import io.github.dbarciela.aura.pipeline.NotificationDTO;
import io.github.dbarciela.aura.pipeline.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

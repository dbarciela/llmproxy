package com.example.llamaproxy.pipeline.plugins;

import com.example.llamaproxy.config.ProxySettings;
import com.example.llamaproxy.db.SessionHistoryRepository;
import com.example.llamaproxy.pipeline.ProxyPlugin;
import com.example.llamaproxy.pipeline.RequestContext;
import com.example.llamaproxy.pipeline.ResponseContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Order(50)
public class ArchivePlugin implements ProxyPlugin {

    public static class ArchiveSettings {
        public boolean enabled = true;
    }

    @Override
    public String getId() { return "archive"; }

    @Override
    public String getName() { return "Network Logs Archive"; }

    @Override
    public String getDescription() { return "Archives all requests and responses."; }

    @Override
    public Object getDefaultSettings() { return new ArchiveSettings(); }

    @Override
    public String getUiTabName() { return "Network Logs"; }

    @Override
    public boolean hasUiToggle() { return true; }

    @Override
    public int getDefaultOrder() { return 50; }

    private final ProxySettings settings;
    private final SessionHistoryRepository repository;
    private final com.example.llamaproxy.pipeline.NotificationService notificationService;
    private final ExecutorService executorService;

    public ArchivePlugin(ProxySettings settings, SessionHistoryRepository repository, com.example.llamaproxy.pipeline.NotificationService notificationService) {
        this.settings = settings;
        this.repository = repository;
        this.notificationService = notificationService;
        // Use a Virtual Thread per task executor for async saving
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public void processRequest(RequestContext context) {
        // Pass-through
    }

    @Override
    public void processResponse(ResponseContext context) {
        if (!settings.isLoggingEnabled()) {
            return;
        }

        RequestContext reqCtx = context.getRequestContext();
        String id = reqCtx.getId();
        String endpoint = reqCtx.getUri();
        int statusCode = context.getStatusCode();
        
        // Construct the full payload containing both request and response for searchability
        String fullPayload = "REQUEST:\n" + reqCtx.getPayload() + "\n\nRESPONSE:\n" + context.getPayload();

        // Save asynchronously
        executorService.submit(() -> {
            repository.save(id, endpoint, statusCode, fullPayload);
            
            if (!"archive".equals(notificationService.getActiveTab())) {
                if (!notificationService.hasUnreadNotification("archive")) {
                    com.example.llamaproxy.pipeline.NotificationDTO n = new com.example.llamaproxy.pipeline.NotificationDTO();
                    n.setSourcePlugin("archive");
                    n.setTitle("New Session Logged");
                    n.setMessage("A new chat session was just archived.");
                    n.setLevel("info");
                    n.setActions(java.util.List.of(
                        new com.example.llamaproxy.pipeline.NotificationDTO.NotificationAction("View Log", null, null, "archive")
                    ));
                    notificationService.addNotification(n);
                }
            }
        });
    }
}

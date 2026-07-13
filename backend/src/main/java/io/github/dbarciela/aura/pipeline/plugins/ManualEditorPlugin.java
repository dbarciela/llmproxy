package io.github.dbarciela.aura.pipeline.plugins;

import io.github.dbarciela.aura.config.ProxySettings;
import io.github.dbarciela.aura.config.PluginSettingsManager;
import io.github.dbarciela.aura.pipeline.BufferingPlugin;
import io.github.dbarciela.aura.pipeline.RequestContext;
import io.github.dbarciela.aura.pipeline.ResponseContext;
import io.github.dbarciela.aura.pipeline.QueueItemDTO;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

@Component
@Order(40)
public class ManualEditorPlugin implements BufferingPlugin {

    private final PluginSettingsManager settingsManager;
    private final io.github.dbarciela.aura.pipeline.NotificationService notificationService;
    private final ConcurrentHashMap<String, Object> queue = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CountDownLatch> latches = new ConcurrentHashMap<>();
    private final List<String> order = new ArrayList<>(); // To maintain custom order if needed

    public ManualEditorPlugin(PluginSettingsManager settingsManager, io.github.dbarciela.aura.pipeline.NotificationService notificationService) {
        this.settingsManager = settingsManager;
        this.notificationService = notificationService;
    }

    private void notifyIfUnread() {
        if (!"intercept".equals(notificationService.getActiveTab())) {
            if (!notificationService.hasUnreadNotification("interceptor")) {
                io.github.dbarciela.aura.pipeline.NotificationDTO n = new io.github.dbarciela.aura.pipeline.NotificationDTO();
                n.setSourcePlugin("interceptor");
                n.setTitle("Request Intercepted");
                n.setMessage("A request or response has been paused and is waiting for your manual review.");
                n.setLevel("warning");
                n.setActions(List.of(new io.github.dbarciela.aura.pipeline.NotificationDTO.NotificationAction("Review Request", null, null, "intercept")));
                notificationService.addNotification(n);
            }
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class ManualEditorSettings {
        public boolean enabled = false;
        public boolean interceptInvalidJson = false;
        public List<String> interceptRegexRules = new ArrayList<>();
    }

    @Override
    public String getId() {
        return "manual-editor";
    }

    @Override
    public String getName() {
        return "Interceptor";
    }

    @Override
    public String getDescription() {
        return "Intercept and manually edit requests/responses before they reach the LLM or client.";
    }

    @Override
    public Object getDefaultSettings() {
        return new ManualEditorSettings();
    }

    @Override
    public String getUiTabName() { return "Interceptor"; }

    @Override
    public boolean hasUiToggle() { return true; }

    @Override
    public int getDefaultOrder() { return 40; }

    @Override
    public void processRequest(RequestContext context) {
        ManualEditorSettings pluginSettings = settingsManager.getSettingsAs(getId(), ManualEditorSettings.class);
        if (pluginSettings == null) pluginSettings = new ManualEditorSettings();

        if (!pluginSettings.enabled) {
            return;
        }
        
        boolean matchesRegex = false;
        List<String> regexRules = pluginSettings.interceptRegexRules;
        if (regexRules != null && !regexRules.isEmpty()) {
            for (String regex : regexRules) {
                if (regex != null && !regex.trim().isEmpty()) {
                    try {
                        if (context.getPayload() != null && java.util.regex.Pattern.compile(regex).matcher(context.getPayload()).find()) {
                            matchesRegex = true;
                            break;
                        }
                    } catch (Exception e) {
                        // Invalid regex, skip this one
                    }
                }
            }
        }
        
        if (!matchesRegex && regexRules != null && !regexRules.isEmpty()) {
            // Only skip if rules exist but none matched. If no rules exist, we intercept EVERYTHING if interceptRequests is true.
            // Wait, previously if regex was not null and not empty, it skipped if no match. 
            // If regex was empty, it intercepted everything.
            return;
        }

        String id = context.getId() + "-req";
        queue.put(id, context);
        notifyIfUnread();
        CountDownLatch latch = new CountDownLatch(1);
        latches.put(id, latch);
        synchronized (order) {
            order.add(id);
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            context.setDropped(true);
        } finally {
            queue.remove(id);
            latches.remove(id);
            synchronized (order) {
                order.remove(id);
            }
        }
    }

    @Override
    public void processResponse(ResponseContext context) {
        ManualEditorSettings pluginSettings = settingsManager.getSettingsAs(getId(), ManualEditorSettings.class);
        if (pluginSettings == null) pluginSettings = new ManualEditorSettings();

        if (!pluginSettings.enabled) {
            return;
        }

        boolean shouldIntercept = false;

        // 1. Invalid JSON check
        if (pluginSettings.interceptInvalidJson && context.getPayload() != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.readTree(context.getPayload());
            } catch (Exception e) {
                // Not valid JSON
                shouldIntercept = true;
            }
        }

        // 2. Regex check
        if (!shouldIntercept) {
            List<String> regexRules = pluginSettings.interceptRegexRules;
            if (regexRules != null && !regexRules.isEmpty()) {
                boolean matchesRegex = false;
                for (String regex : regexRules) {
                    if (regex != null && !regex.trim().isEmpty()) {
                        try {
                            if (context.getPayload() != null && java.util.regex.Pattern.compile(regex).matcher(context.getPayload()).find()) {
                                matchesRegex = true;
                                break;
                            }
                        } catch (Exception e) {
                            // Invalid regex, skip
                        }
                    }
                }
                shouldIntercept = matchesRegex;
            } else {
                // If no regex rules and we want to intercept responses, then we intercept ALL responses
                shouldIntercept = true;
            }
        }

        if (!shouldIntercept) {
            return;
        }

        String id = context.getRequestContext().getId() + "-res";
        queue.put(id, context);
        notifyIfUnread();
        CountDownLatch latch = new CountDownLatch(1);
        latches.put(id, latch);
        synchronized (order) {
            order.add(id);
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            context.getRequestContext().setDropped(true);
        } finally {
            queue.remove(id);
            latches.remove(id);
            synchronized (order) {
                order.remove(id);
            }
        }
    }

    // --- Methods for the UI Control API ---

    public List<QueueItemDTO> getQueue() {
        List<QueueItemDTO> currentQueue = new ArrayList<>();
        synchronized (order) {
            for (String id : order) {
                Object ctx = queue.get(id);
                if (ctx instanceof RequestContext) {
                    RequestContext rCtx = (RequestContext) ctx;
                    currentQueue.add(new QueueItemDTO(id, rCtx.getMethod(), rCtx.getUri(), rCtx.getPayload(), "REQ"));
                } else if (ctx instanceof ResponseContext) {
                    ResponseContext resCtx = (ResponseContext) ctx;
                    RequestContext rCtx = resCtx.getRequestContext();
                    currentQueue.add(new QueueItemDTO(id, rCtx.getMethod(), rCtx.getUri(), resCtx.getPayload(), "RES"));
                }
            }
        }
        return currentQueue;
    }

    public void release(String id, String updatedPayload) {
        Object ctx = queue.get(id);
        if (ctx instanceof RequestContext) {
            ((RequestContext) ctx).setPayload(updatedPayload);
        } else if (ctx instanceof ResponseContext) {
            ((ResponseContext) ctx).setPayload(updatedPayload);
        }
        CountDownLatch latch = latches.get(id);
        if (latch != null) {
            latch.countDown();
        }
    }

    public void drop(String id) {
        Object ctx = queue.get(id);
        if (ctx instanceof RequestContext) {
            ((RequestContext) ctx).setDropped(true);
        } else if (ctx instanceof ResponseContext) {
            ((ResponseContext) ctx).getRequestContext().setDropped(true);
        }
        CountDownLatch latch = latches.get(id);
        if (latch != null) {
            latch.countDown();
        }
    }

    public void reorder(List<String> newOrder) {
        synchronized (order) {
            order.clear();
            order.addAll(newOrder);
        }
    }
}

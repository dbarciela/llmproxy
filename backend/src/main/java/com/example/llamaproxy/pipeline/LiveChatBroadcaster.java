package com.example.llamaproxy.pipeline;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class LiveChatBroadcaster {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // infinite timeout
        emitters.add(emitter);
        
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        
        return emitter;
    }

    public void broadcastRequest(String id, String payload) {
        broadcast(id, "REQUEST", payload);
    }

    public void broadcastChunk(String id, String chunk) {
        broadcast(id, "CHUNK", chunk);
    }

    public void broadcastDone(String id, String payload) {
        broadcast(id, "DONE", payload);
    }

    public void broadcastNotificationData(String jsonPayload) {
        if (emitters.isEmpty()) return;
        
        String eventMessage = String.format("{\"id\":\"%s\",\"type\":\"%s\",\"data\":%s}", 
            java.util.UUID.randomUUID().toString(), "NOTIFICATION", jsonPayload);
        
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("live-chat").data(eventMessage));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    public void broadcastHardware(String jsonPayload) {
        if (emitters.isEmpty()) return;
        String eventMessage = String.format("{\"id\":\"%s\",\"type\":\"%s\",\"data\":%s}", 
            java.util.UUID.randomUUID().toString(), "HARDWARE", jsonPayload);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("live-chat").data(eventMessage));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    public void broadcastContextLimit(int limit) {
        if (emitters.isEmpty()) return;
        String eventMessage = String.format("{\"id\":\"%s\",\"type\":\"%s\",\"data\":%d}", 
            java.util.UUID.randomUUID().toString(), "CONTEXT_LIMIT", limit);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("live-chat").data(eventMessage));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    public void broadcastMetrics(String id, String metricsJson) {
        if (emitters.isEmpty()) return;
        String eventMessage = String.format("{\"id\":\"%s\",\"type\":\"%s\",\"data\":%s}", 
            id, "METRICS", metricsJson);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("live-chat").data(eventMessage));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
    public void broadcastPluginEvent(String pluginId, String eventType, Object payload) {
        if (emitters.isEmpty()) return;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonPayload = mapper.writeValueAsString(payload);
            String eventMessage = String.format("{\"pluginId\":\"%s\",\"type\":\"%s\",\"data\":%s}", 
                pluginId, eventType, jsonPayload);
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("live-chat").data(eventMessage));
                } catch (IOException e) {
                    emitters.remove(emitter);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void broadcast(String id, String type, String data) {
        if (emitters.isEmpty()) return;
        
        String safeData = escapeJson(data);
        String eventMessage = String.format("{\"id\":\"%s\",\"type\":\"%s\",\"data\":\"%s\"}", id, type, safeData);
        
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("live-chat").data(eventMessage));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}

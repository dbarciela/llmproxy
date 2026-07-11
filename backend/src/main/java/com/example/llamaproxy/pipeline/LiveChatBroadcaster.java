package com.example.llamaproxy.pipeline;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class LiveChatBroadcaster {

    private static final ObjectMapper mapper = new ObjectMapper();

    // Mapeamento de Emitters para as suas respetivas filas e threads
    private final Map<SseEmitter, ClientWorker> clients = new ConcurrentHashMap<>();

    private record ClientWorker(BlockingQueue<String> queue, Thread thread) {}

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        BlockingQueue<String> queue = new LinkedBlockingQueue<>(); // Fila ilimitada
        
        // Inicia UMA única Virtual Thread dedicada exclusivamente a este cliente
        Thread workerThread = Thread.startVirtualThread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    // O take() bloqueia a Virtual Thread (sem custo de CPU) até haver um chunk novo
                    String eventMessage = queue.take(); 
                    emitter.send(SseEmitter.event().name("live-chat").data(eventMessage));
                }
            } catch (InterruptedException | IOException e) {
                removeClient(emitter);
            }
        });

        clients.put(emitter, new ClientWorker(queue, workerThread));

        emitter.onCompletion(() -> removeClient(emitter));
        emitter.onTimeout(() -> removeClient(emitter));
        emitter.onError(e -> removeClient(emitter));
        
        return emitter;
    }

    private void removeClient(SseEmitter emitter) {
        ClientWorker worker = clients.remove(emitter);
        if (worker != null) {
            worker.thread().interrupt(); // Encerra a Virtual Thread se o cliente desligar
        }
    }

    private void pushEventToAll(String eventMessage) {
        if (clients.isEmpty()) return;
        for (ClientWorker worker : clients.values()) {
            worker.queue().offer(eventMessage);
        }
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
        String eventMessage = String.format("{\"id\":\"%s\",\"type\":\"%s\",\"data\":%s}", 
            java.util.UUID.randomUUID().toString(), "NOTIFICATION", jsonPayload);
        pushEventToAll(eventMessage);
    }

    public void broadcastHardware(String jsonPayload) {
        String eventMessage = String.format("{\"id\":\"%s\",\"type\":\"%s\",\"data\":%s}", 
            java.util.UUID.randomUUID().toString(), "HARDWARE", jsonPayload);
        pushEventToAll(eventMessage);
    }

    public void broadcastContextLimit(int limit) {
        String eventMessage = String.format("{\"id\":\"%s\",\"type\":\"%s\",\"data\":%d}", 
            java.util.UUID.randomUUID().toString(), "CONTEXT_LIMIT", limit);
        pushEventToAll(eventMessage);
    }

    public void broadcastMetrics(String id, String metricsJson) {
        String eventMessage = String.format("{\"id\":\"%s\",\"type\":\"%s\",\"data\":%s}", 
            id, "METRICS", metricsJson);
        pushEventToAll(eventMessage);
    }
    
    public void broadcastPluginEvent(String pluginId, String eventType, Object payload) {
        try {
            String jsonPayload = mapper.writeValueAsString(payload);
            String eventMessage = String.format("{\"pluginId\":\"%s\",\"type\":\"%s\",\"data\":%s}", 
                pluginId, eventType, jsonPayload);
            pushEventToAll(eventMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcast(String id, String type, String data) {
        try {
            String safeData = mapper.writeValueAsString(data);
            String eventMessage = String.format("{\"id\":\"%s\",\"type\":\"%s\",\"data\":%s}", id, type, safeData);
            pushEventToAll(eventMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

package io.github.dbarciela.aura.pipeline;

public class QueueItemDTO {
    private String id;
    private String method;
    private String uri;
    private String payload;
    private String phase; // "REQUEST" or "RESPONSE"

    public QueueItemDTO(String id, String method, String uri, String payload, String phase) {
        this.id = id;
        this.method = method;
        this.uri = uri;
        this.payload = payload;
        this.phase = phase;
    }

    public String getId() { return id; }
    public String getMethod() { return method; }
    public String getUri() { return uri; }
    public String getPayload() { return payload; }
    public String getPhase() { return phase; }
}

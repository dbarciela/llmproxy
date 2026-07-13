package io.github.dbarciela.aura.pipeline;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.UUID;

public class RequestContext {
    private final String id;
    private final String method;
    private final String uri;
    private final HttpHeaders headers;
    private String payload;
    private boolean dropped;

    public RequestContext(HttpMethod method, String uri, HttpHeaders headers, String payload) {
        this.id = UUID.randomUUID().toString();
        this.method = method.name();
        this.uri = uri;
        this.headers = headers;
        this.payload = payload;
        this.dropped = false;
    }

    public String getId() { return id; }
    public String getMethod() { return method; }
    public String getUri() { return uri; }
    public HttpHeaders getHeaders() { return headers; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public boolean isDropped() { return dropped; }
    public void setDropped(boolean dropped) { this.dropped = dropped; }
}

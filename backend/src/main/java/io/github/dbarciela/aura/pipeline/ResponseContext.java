package io.github.dbarciela.aura.pipeline;

import org.springframework.http.HttpHeaders;

public class ResponseContext {
	private final RequestContext requestContext;
	private final int statusCode;
	private final HttpHeaders headers;
	private String payload;

	public ResponseContext(RequestContext requestContext, int statusCode, HttpHeaders headers, String payload) {
		this.requestContext = requestContext;
		this.statusCode = statusCode;
		this.headers = headers;
		this.payload = payload;
	}

	public RequestContext getRequestContext() {
		return requestContext;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public HttpHeaders getHeaders() {
		return headers;
	}

	public String getPayload() {
		return payload;
	}

	public void setPayload(String payload) {
		this.payload = payload;
	}
}

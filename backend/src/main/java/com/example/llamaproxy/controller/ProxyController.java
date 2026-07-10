package com.example.llamaproxy.controller;

import com.example.llamaproxy.pipeline.ProxyPipeline;
import com.example.llamaproxy.pipeline.RequestContext;
import com.example.llamaproxy.pipeline.ResponseContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

@RestController
public class ProxyController {

    private final ProxyPipeline pipeline;
    private final RestClient restClient;
    private final String targetServerUrl;
    private final com.example.llamaproxy.config.ProxySettings settings;
    private final com.example.llamaproxy.pipeline.LiveChatBroadcaster liveChatBroadcaster;

    public ProxyController(ProxyPipeline pipeline, com.example.llamaproxy.config.ProxySettings settings, com.example.llamaproxy.pipeline.LiveChatBroadcaster liveChatBroadcaster, @Value("${target.server.url}") String targetServerUrl) {
        this.pipeline = pipeline;
        this.settings = settings;
        this.liveChatBroadcaster = liveChatBroadcaster;
        this.targetServerUrl = targetServerUrl;
        
        // Use JdkClientHttpRequestFactory to support Virtual Threads natively
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(600000); // 10 minutes for long generations from reasoning models
        
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @RequestMapping("/v1/**")
    public void proxyRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String uri = request.getRequestURI();
        if (request.getQueryString() != null) {
            uri += "?" + request.getQueryString();
        }
        
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.add(headerName, request.getHeader(headerName));
        }

        // Read payload
        String payload = "";
        if (method != HttpMethod.GET && method != HttpMethod.HEAD) {
            payload = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        }

        RequestContext reqCtx = new RequestContext(method, uri, headers, payload);
        liveChatBroadcaster.broadcastRequest(reqCtx.getId(), payload);
        
        // 1. Run Request Pipeline
        pipeline.processRequest(reqCtx);
        
        if (reqCtx.isDropped()) {
            response.setStatus(499); // Client Closed Request
            return;
        }

        // Determine actual target URI by stripping the local proxy base
        String targetUri = targetServerUrl.endsWith("/v1") && uri.startsWith("/v1") ? 
            targetServerUrl.substring(0, targetServerUrl.length() - 3) + uri :
            targetServerUrl + uri;

        // 2. Forward to target server
        restClient.method(method)
            .uri(targetUri)
            .headers(httpHeaders -> {
                httpHeaders.addAll(reqCtx.getHeaders());
                httpHeaders.remove(HttpHeaders.HOST);
                httpHeaders.remove(HttpHeaders.CONTENT_LENGTH);
            })
            .body(reqCtx.getPayload().getBytes(StandardCharsets.UTF_8))
            .exchange((clientRequest, clientResponse) -> {
                long startTime = System.currentTimeMillis();
                long[] ttft = new long[]{-1};
                
                // Set response status
                response.setStatus(clientResponse.getStatusCode().value());
                HttpHeaders respHeaders = clientResponse.getHeaders();
                
                if (!settings.isInterceptResponses()) {
                    // STREAMING MODE
                    respHeaders.forEach((key, values) -> {
                        if (!key.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)) {
                            for (String value : values) {
                                response.addHeader(key, value);
                            }
                        }
                    });

                    ByteArrayOutputStream aggregatedBody = new ByteArrayOutputStream();
                    try (InputStream is = clientResponse.getBody()) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            if (ttft[0] == -1) {
                                ttft[0] = System.currentTimeMillis() - startTime;
                            }
                            aggregatedBody.write(buffer, 0, bytesRead);
                            
                            // Broadcast live chunk
                            String chunk = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                            liveChatBroadcaster.broadcastChunk(reqCtx.getId(), chunk);
                            
                            try {
                                response.getOutputStream().write(buffer, 0, bytesRead);
                                response.getOutputStream().flush();
                            } catch (IOException e) {
                                break;
                            }
                        }
                    }
                    String finalResponsePayload = aggregatedBody.toString(StandardCharsets.UTF_8);
                    
                    extractAndBroadcastMetrics(reqCtx.getId(), finalResponsePayload, startTime, ttft[0]);
                    
                    liveChatBroadcaster.broadcastDone(reqCtx.getId(), finalResponsePayload);
                    
                    ResponseContext resCtx = new ResponseContext(reqCtx, clientResponse.getStatusCode().value(), respHeaders, finalResponsePayload);
                    pipeline.processResponse(resCtx);

                } else {
                    // INTERCEPT MODE
                    ByteArrayOutputStream aggregatedBody = new ByteArrayOutputStream();
                    try (InputStream is = clientResponse.getBody()) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            if (ttft[0] == -1) {
                                ttft[0] = System.currentTimeMillis() - startTime;
                            }
                            aggregatedBody.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    String finalResponsePayload = aggregatedBody.toString(StandardCharsets.UTF_8);
                    extractAndBroadcastMetrics(reqCtx.getId(), finalResponsePayload, startTime, ttft[0]);

                    ResponseContext resCtx = new ResponseContext(reqCtx, clientResponse.getStatusCode().value(), respHeaders, finalResponsePayload);
                    
                    // 3. Run Response Pipeline (this will block for manual editing)
                    pipeline.processResponse(resCtx);

                    if (!reqCtx.isDropped()) {
                        byte[] finalBytes = resCtx.getPayload().getBytes(StandardCharsets.UTF_8);
                        
                        liveChatBroadcaster.broadcastChunk(reqCtx.getId(), resCtx.getPayload());
                        liveChatBroadcaster.broadcastDone(reqCtx.getId(), resCtx.getPayload());
                        
                        // Set headers, overriding Content-Length with the new accurate length
                        respHeaders.forEach((key, values) -> {
                            if (!key.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING) && !key.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
                                for (String value : values) {
                                    response.addHeader(key, value);
                                }
                            }
                        });
                        response.setContentLength(finalBytes.length);

                        try {
                            response.getOutputStream().write(finalBytes);
                            response.getOutputStream().flush();
                        } catch (IOException e) {
                            // Client disconnected
                        }
                    } else {
                        liveChatBroadcaster.broadcastDone(reqCtx.getId(), "");
                    }
                }
                
                return null;
            });
    }

    private void extractAndBroadcastMetrics(String reqId, String payload, long startTime, long ttft) {
        try {
            long totalTime = System.currentTimeMillis() - startTime;
            if (ttft == -1) ttft = totalTime;
            
            int promptTokens = 0;
            int completionTokens = 0;
            int totalTokens = 0;
            
            // Try to find "usage" block in payload
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"usage\"\\s*:\\s*(\\{[^}]+\\})");
            java.util.regex.Matcher m = p.matcher(payload);
            String usageJson = null;
            while (m.find()) {
                usageJson = m.group(1); // last one
            }
            
            if (usageJson != null) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.Map usage = mapper.readValue(usageJson, java.util.Map.class);
                    if (usage.containsKey("prompt_tokens")) promptTokens = ((Number) usage.get("prompt_tokens")).intValue();
                    if (usage.containsKey("completion_tokens")) completionTokens = ((Number) usage.get("completion_tokens")).intValue();
                    if (usage.containsKey("total_tokens")) totalTokens = ((Number) usage.get("total_tokens")).intValue();
                } catch (Exception e) {
                    // Ignore malformed usage block in stream
                }
            }

            double tokensPerSec = 0;
            long generationTime = totalTime - ttft;
            if (generationTime > 0 && completionTokens > 0) {
                tokensPerSec = completionTokens / (generationTime / 1000.0);
            }

            String metricsJson = String.format("{\"ttft\":%d, \"totalTime\":%d, \"promptTokens\":%d, \"completionTokens\":%d, \"totalTokens\":%d, \"tokensPerSec\":%.2f}",
                ttft, totalTime, promptTokens, completionTokens, totalTokens, tokensPerSec);
                
            liveChatBroadcaster.broadcastMetrics(reqId, metricsJson);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

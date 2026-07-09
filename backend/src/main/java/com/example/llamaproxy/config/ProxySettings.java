package com.example.llamaproxy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProxySettings {
    private boolean interceptRequests = false;
    private boolean interceptResponses = false;
    private boolean loggingEnabled = false;
    private String interceptRegex = "";

    @Value("${target.webui.url:}")
    private String webUiUrl;

    public boolean isInterceptRequests() {
        return interceptRequests;
    }

    public void setInterceptRequests(boolean interceptRequests) {
        this.interceptRequests = interceptRequests;
    }

    public boolean isInterceptResponses() {
        return interceptResponses;
    }

    public void setInterceptResponses(boolean interceptResponses) {
        this.interceptResponses = interceptResponses;
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public void setLoggingEnabled(boolean loggingEnabled) {
        this.loggingEnabled = loggingEnabled;
    }

    public String getInterceptRegex() {
        return interceptRegex;
    }

    public void setInterceptRegex(String interceptRegex) {
        this.interceptRegex = interceptRegex;
    }

    public String getWebUiUrl() {
        return webUiUrl;
    }

    public void setWebUiUrl(String webUiUrl) {
        this.webUiUrl = webUiUrl;
    }
}

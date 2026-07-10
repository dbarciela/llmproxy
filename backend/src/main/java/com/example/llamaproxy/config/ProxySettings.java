package com.example.llamaproxy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ProxySettings {
    private boolean interceptRequests = false;
    private boolean interceptResponses = false;
    private boolean loggingEnabled = false;
    // Core Settings
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

    @Value("${target.webui.url:}")
    private String webUiUrl;

    public String getWebUiUrl() {
        return webUiUrl;
    }

    public void setWebUiUrl(String webUiUrl) {
        this.webUiUrl = webUiUrl;
    }
}

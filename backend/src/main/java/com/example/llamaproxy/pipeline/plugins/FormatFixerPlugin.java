package com.example.llamaproxy.pipeline.plugins;

import com.example.llamaproxy.pipeline.ProxyPlugin;
import com.example.llamaproxy.pipeline.RequestContext;
import com.example.llamaproxy.pipeline.ResponseContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class FormatFixerPlugin implements ProxyPlugin {

    public static class FormatFixerSettings {
        public boolean enabled = true;
    }

    @Override
    public String getId() { return "format-fixer"; }

    @Override
    public String getName() { return "JSON Auto-Fixer"; }

    @Override
    public String getDescription() { return "Automatically attempts to fix common JSON formatting errors returned by the LLM."; }

    @Override
    public Object getDefaultSettings() { return new FormatFixerSettings(); }

    @Override
    public void processRequest(RequestContext context) {
        // Pass-through implementation for now.
        // Future-proofing for automated JSON schema fixes.
    }

    @Override
    public void processResponse(ResponseContext context) {
        // Pass-through
    }
}

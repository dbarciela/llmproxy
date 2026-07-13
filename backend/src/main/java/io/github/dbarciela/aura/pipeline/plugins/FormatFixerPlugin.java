package io.github.dbarciela.aura.pipeline.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.github.dbarciela.aura.pipeline.RequestContext;
import io.github.dbarciela.aura.pipeline.ResponseContext;
import io.github.dbarciela.aura.pipeline.StreamingPlugin;

@Component
@Order(20)
public class FormatFixerPlugin implements StreamingPlugin {

	public static class FormatFixerSettings {
		public boolean enabled = true;
	}

	@Override
	public String getId() {
		return "format-fixer";
	}

	@Override
	public String getName() {
		return "JSON Auto-Fixer";
	}

	@Override
	public String getDescription() {
		return "Automatically attempts to fix common JSON formatting errors returned by the LLM.";
	}

	@Override
	public Object getDefaultSettings() {
		return new FormatFixerSettings();
	}

	@Override
	public String getUiTabName() {
		return "Format Fixer";
	}

	@Override
	public boolean hasUiToggle() {
		return false;
	}

	@Override
	public int getDefaultOrder() {
		return 20;
	}

	@Override
	public void processRequestStream(InputStream in, OutputStream out, RequestContext context) throws IOException {
		// Pass-through implementation for now.
		// Future-proofing for automated JSON schema fixes on streams.
		in.transferTo(out);
	}

	@Override
	public void processResponseStream(InputStream in, OutputStream out, ResponseContext context) throws IOException {
		// Pass-through
		in.transferTo(out);
	}
}

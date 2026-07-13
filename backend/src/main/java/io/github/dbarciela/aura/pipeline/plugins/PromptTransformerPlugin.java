package io.github.dbarciela.aura.pipeline.plugins;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.github.dbarciela.aura.config.PluginSettingsManager;
import io.github.dbarciela.aura.pipeline.ProxyPlugin;
import io.github.dbarciela.aura.pipeline.RequestContext;
import io.github.dbarciela.aura.pipeline.ResponseContext;

@Component
@Order(30)
public class PromptTransformerPlugin implements ProxyPlugin {

	private final PluginSettingsManager settingsManager;

	public PromptTransformerPlugin(PluginSettingsManager settingsManager) {
		this.settingsManager = settingsManager;
	}

	public static class PromptReplaceRule {
		public String regex;
		public String with;

		public PromptReplaceRule() {
		}

		public PromptReplaceRule(String regex, String with) {
			this.regex = regex;
			this.with = with;
		}

		public String getRegex() {
			return regex;
		}

		public void setRegex(String regex) {
			this.regex = regex;
		}

		public String getWith() {
			return with;
		}

		public void setWith(String with) {
			this.with = with;
		}
	}

	public static class TransformerSettings {
		public java.util.List<PromptReplaceRule> promptReplaceRules = new java.util.ArrayList<>();
		public java.util.List<PromptReplaceRule> responseReplaceRules = new java.util.ArrayList<>();
	}

	@Override
	public String getId() {
		return "prompt-transformer";
	}

	@Override
	public String getName() {
		return "Regex Matcher";
	}

	@Override
	public String getDescription() {
		return "Modify prompts and responses dynamically using Regex rules.";
	}

	@Override
	public Object getDefaultSettings() {
		return new TransformerSettings();
	}

	@Override
	public String getUiTabName() {
		return "Transformer";
	}

	@Override
	public boolean hasUiToggle() {
		return true;
	}

	@Override
	public int getDefaultOrder() {
		return 30;
	}

	@Override
	public void processRequest(RequestContext context) {
		TransformerSettings pluginSettings = settingsManager.getSettingsAs(getId(), TransformerSettings.class);
		if (pluginSettings == null || pluginSettings.promptReplaceRules.isEmpty()) {
			return;
		}

		String currentPayload = context.getPayload();
		if (currentPayload == null || currentPayload.isEmpty()) {
			return;
		}

		for (PromptReplaceRule rule : pluginSettings.promptReplaceRules) {
			String regex = rule.getRegex();
			String replacement = rule.getWith();

			if (regex != null && !regex.isEmpty()) {
				try {
					Pattern pattern = Pattern.compile(regex);
					Matcher matcher = pattern.matcher(currentPayload);

					if (matcher.find()) {
						currentPayload = matcher.replaceAll(replacement == null ? "" : replacement);
					}
				} catch (Exception e) {
					// Ignore regex compilation errors or replacement errors during runtime
				}
			}
		}
		context.setPayload(currentPayload);
	}

	@Override
	public void processResponse(ResponseContext context) {
		TransformerSettings pluginSettings = settingsManager.getSettingsAs(getId(), TransformerSettings.class);
		if (pluginSettings == null || pluginSettings.responseReplaceRules.isEmpty()) {
			return;
		}

		String currentPayload = context.getPayload();
		if (currentPayload == null || currentPayload.isEmpty()) {
			return;
		}

		for (PromptReplaceRule rule : pluginSettings.responseReplaceRules) {
			String regex = rule.getRegex();
			String replacement = rule.getWith();

			if (regex != null && !regex.isEmpty()) {
				try {
					Pattern pattern = Pattern.compile(regex);
					Matcher matcher = pattern.matcher(currentPayload);

					if (matcher.find()) {
						currentPayload = matcher.replaceAll(replacement == null ? "" : replacement);
					}
				} catch (Exception e) {
					// Ignore regex compilation errors or replacement errors during runtime
				}
			}
		}
		context.setPayload(currentPayload);
	}
}

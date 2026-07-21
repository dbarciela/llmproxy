package io.github.dbarciela.aura.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ProxySettings {
	private boolean loggingEnabled;
	private String defaultTab = "live-chat-plugin";

	@Value("${target.server.url:}")
	private String targetServerUrl;

	@Value("${target.webui.url:}")
	private String webUiUrl;

	@Value("${aura.schema.url:/openai-schema.json}")
	private String schemaUrl;

	@Value("${target.server.restart-commands:}")
	private String rawRestartCommands;

	@Value("${target.server.restart-script:}")
	private String rawRestartScript;

	public static class RestartCommand {
		private String id;
		private String name;
		private String command;

		public RestartCommand() {}

		public RestartCommand(String id, String name, String command) {
			this.id = id;
			this.name = name;
			this.command = command;
		}

		public String getId() { return id; }
		public void setId(String id) { this.id = id; }
		public String getName() { return name; }
		public void setName(String name) { this.name = name; }
		public String getCommand() { return command; }
		public void setCommand(String command) { this.command = command; }
	}

	private List<RestartCommand> restartCommands = new ArrayList<>();

	@PostConstruct
	public void initRestartCommands() {
		if (rawRestartCommands != null && !rawRestartCommands.isBlank()) {
			String[] parts = rawRestartCommands.split(",");
			int count = 1;
			for (String part : parts) {
				String cmd = part.trim();
				if (!cmd.isEmpty()) {
					String name = deriveName(cmd, count++);
					restartCommands.add(new RestartCommand("cmd-" + UUID.randomUUID().toString().substring(0, 8), name, cmd));
				}
			}
		} else if (rawRestartScript != null && !rawRestartScript.isBlank()) {
			String cmd = rawRestartScript.trim();
			String name = deriveName(cmd, 1);
			restartCommands.add(new RestartCommand("cmd-" + UUID.randomUUID().toString().substring(0, 8), name, cmd));
		}
	}

	private String deriveName(String cmd, int index) {
		File f = new File(cmd);
		if (f.getName() != null && !f.getName().isBlank()) {
			return f.getName();
		}
		return "Command " + index;
	}

	public List<RestartCommand> getRestartCommands() {
		return restartCommands;
	}

	public void setRestartCommands(List<RestartCommand> restartCommands) {
		this.restartCommands = restartCommands;
	}

	public String getDefaultTab() {
		return defaultTab;
	}

	public void setDefaultTab(String defaultTab) {
		this.defaultTab = defaultTab;
	}

	public boolean isLoggingEnabled() {
		return loggingEnabled;
	}

	public void setLoggingEnabled(boolean loggingEnabled) {
		this.loggingEnabled = loggingEnabled;
	}

	public String getWebUiUrl() {
		return webUiUrl;
	}

	public void setWebUiUrl(String webUiUrl) {
		this.webUiUrl = webUiUrl;
	}

	public String getSchemaUrl() {
		return schemaUrl;
	}

	public void setSchemaUrl(String schemaUrl) {
		this.schemaUrl = schemaUrl;
	}
}

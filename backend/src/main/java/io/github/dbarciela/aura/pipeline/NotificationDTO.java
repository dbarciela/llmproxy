package io.github.dbarciela.aura.pipeline;

import java.util.List;

public class NotificationDTO {
	private String id;
	private String sourcePlugin;
	private String title;
	private String message;
	private String level;
	private long createdAt;
	private List<NotificationAction> actions;

	public NotificationDTO() {
		this.createdAt = System.currentTimeMillis();
	}

	public NotificationDTO(String id, String sourcePlugin, String title, String message, String level,
			List<NotificationAction> actions) {
		this.id = id;
		this.sourcePlugin = sourcePlugin;
		this.title = title;
		this.message = message;
		this.level = level;
		this.createdAt = System.currentTimeMillis();
		this.actions = actions;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSourcePlugin() {
		return sourcePlugin;
	}

	public void setSourcePlugin(String sourcePlugin) {
		this.sourcePlugin = sourcePlugin;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getLevel() {
		return level;
	}

	public void setLevel(String level) {
		this.level = level;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(long createdAt) {
		this.createdAt = createdAt;
	}

	public List<NotificationAction> getActions() {
		return actions;
	}

	public void setActions(List<NotificationAction> actions) {
		this.actions = actions;
	}

	public static class NotificationAction {
		private String label;
		private String api;
		private String url;
		private String tab;
		private String streamUrl;
		private boolean autoDismiss = true;

		public NotificationAction() {
		}

		public NotificationAction(String label, String api, String url, String tab) {
			this.label = label;
			this.api = api;
			this.url = url;
			this.tab = tab;
		}

		public NotificationAction(String label, String api, String url, String tab, boolean autoDismiss) {
			this.label = label;
			this.api = api;
			this.url = url;
			this.tab = tab;
			this.autoDismiss = autoDismiss;
		}

		public NotificationAction(String label, String api, String url, String tab, String streamUrl,
				boolean autoDismiss) {
			this.label = label;
			this.api = api;
			this.url = url;
			this.tab = tab;
			this.streamUrl = streamUrl;
			this.autoDismiss = autoDismiss;
		}

		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		public String getApi() {
			return api;
		}

		public void setApi(String api) {
			this.api = api;
		}

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public String getTab() {
			return tab;
		}

		public void setTab(String tab) {
			this.tab = tab;
		}

		public String getStreamUrl() {
			return streamUrl;
		}

		public void setStreamUrl(String streamUrl) {
			this.streamUrl = streamUrl;
		}

		public boolean isAutoDismiss() {
			return autoDismiss;
		}

		public void setAutoDismiss(boolean autoDismiss) {
			this.autoDismiss = autoDismiss;
		}
	}
}

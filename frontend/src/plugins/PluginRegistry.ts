import React from 'react';

export interface PluginUI {
  id: string;
  order: number;
  name?: string; // Optional: If backend provides a name, we can use it, but UI can override
  icon?: React.ComponentType<{ className?: string }>;
  component: React.ComponentType<{ settings: any, updateSettings: (newSettings: any) => void }>;
  renderTabAction?: (settings: any, updateSettings: (newSettings: any) => void) => React.ReactNode;
}

export const pluginRegistry: Record<string, PluginUI> = {};

export function registerPlugin(plugin: PluginUI) {
  pluginRegistry[plugin.id] = plugin;
}

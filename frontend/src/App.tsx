import { useState, useEffect } from 'react';
import { ArchiveBrowser } from './components/ArchiveBrowser';
import { LogViewerModal } from './components/LogViewerModal';
import { NotificationArea } from './components/NotificationArea';
import { ProgressModal } from './components/ProgressModal';
import { useBackgroundTasks } from './hooks/useBackgroundTasks';
import { pluginComponents } from './plugins';
import { ConfigurationScreen } from './components/ConfigurationScreen';
import { Activity, ServerCrash, RefreshCw, Settings, Database } from 'lucide-react';
import { HardwareWidget } from './components/HardwareWidget';
import { SlotsWidget } from './components/SlotsWidget';
import { NetworkIndicator } from './components/NetworkIndicator';
import { CommandPalette } from './components/CommandPalette';
import { registerCommand, registerSearchProvider } from './plugins/PluginRegistry';
import { Toaster, toast } from 'sonner';

export default function App() {
  const [isLoggingEnabled, setIsLoggingEnabled] = useState(false);
  const [serverHealthy, setServerHealthy] = useState(false);
  const [activeTab, setActiveTab] = useState<string>('');
  const [defaultTab, setDefaultTab] = useState<string>('live-chat-plugin');
  const [isLogsOpen, setIsLogsOpen] = useState(false);

  const { tasks, startTask, minimizeTask, openTask, closeTask } = useBackgroundTasks();

  const [pluginSettings, setPluginSettings] = useState<Record<string, any>>({});
  const [hiddenTabs, setHiddenTabs] = useState<string[]>(() => {
    try {
      return JSON.parse(localStorage.getItem('hiddenTabs') || '[]');
    } catch {
      return [];
    }
  });

  const toggleTabHidden = (id: string) => {
    setHiddenTabs(prev => {
      const newTabs = prev.includes(id) ? prev.filter(t => t !== id) : [...prev, id];
      localStorage.setItem('hiddenTabs', JSON.stringify(newTabs));
      return newTabs;
    });
  };

  const [webUiUrl, setWebUiUrl] = useState<string>('');
  const [targetUrl, setTargetUrl] = useState<string>('');
  const [globalPlugins, setGlobalPlugins] = useState<any[]>([]);

  const fetchMetadata = () => {
    fetch('/api/proxy/plugins/metadata', { cache: 'no-store' })
      .then(r => r.json())
      .then(setGlobalPlugins)
      .catch(console.error);
  };

  useEffect(() => {
    fetchMetadata();
    Promise.all([
      fetch('/api/proxy/settings', { cache: 'no-store' }).then(res => res.json()),
      fetch('/api/proxy/target-url', { cache: 'no-store' }).then(res => res.text())
    ]).then(([settingsData, url]) => {
      setIsLoggingEnabled(settingsData.loggingEnabled);
      setDefaultTab(settingsData.defaultTab || 'live-chat-plugin');
      setActiveTab(settingsData.defaultTab || 'live-chat-plugin');
      setPluginSettings(settingsData.plugins || {});
      setTargetUrl(url);
      setWebUiUrl(settingsData.webUiUrl || url.replace('/v1', ''));
    }).catch(err => console.error("Error fetching initialization data:", err));
  }, []);

  useEffect(() => {
    const activePlugin = globalPlugins.find(p => p.id === activeTab);
    document.title = `Aura - ${activePlugin ? activePlugin.uiTabName : 'Home'}`;

    fetch('/api/proxy/ui/active-tab', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tab: activeTab })
    }).catch(() => { });
  }, [activeTab]);

  useEffect(() => {
    const interval = setInterval(() => {
      fetch('/api/proxy/health')
        .then(res => res.text())
        .then(data => setServerHealthy(data === 'true'))
        .catch(() => setServerHealthy(false));
    }, 5000);
    return () => clearInterval(interval);
  }, []);

  const updateCoreSettings = (logging: boolean) => {
    setIsLoggingEnabled(logging);
    fetch('/api/proxy/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        loggingEnabled: logging
      })
    }).catch(err => console.error("Error updating settings:", err));
  };

  const updatePluginSettings = (pluginId: string, newSettings: any) => {
    setPluginSettings(prev => ({ ...prev, [pluginId]: newSettings }));
    fetch(`/api/proxy/plugins/${pluginId}/settings`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(newSettings)
    }).catch(err => console.error("Error updating plugin settings:", err));
  };

  const restartServer = () => {
    if (window.confirm("Are you sure you want to restart the Llama server?")) {
      setIsLogsOpen(true);
      fetch('/api/proxy/restart-target', { method: 'POST' })
        .then(res => {
          if (!res.ok) toast.error('Failed to restart server');
          else toast.success('Server restart initiated');
        })
        .catch(err => {
          console.error("Error restarting server:", err);
          toast.error('Failed to restart server');
        });
    }
  };

  useEffect(() => {
    // Register core commands
    registerCommand({
      id: 'core.settings',
      title: 'Open Settings',
      section: 'Navigation',
      icon: <Settings className="w-4 h-4" />,
      perform: () => setActiveTab('settings')
    });
    registerCommand({
      id: 'core.restart',
      title: 'Restart Llama Server',
      section: 'System',
      icon: <RefreshCw className="w-4 h-4" />,
      perform: () => restartServer()
    });
    registerCommand({
      id: 'core.toggleLogs',
      title: 'Toggle Network Logs',
      section: 'System',
      icon: <Activity className="w-4 h-4" />,
      perform: () => updateCoreSettings(!isLoggingEnabled)
    });

    registerSearchProvider({
      id: 'archive-search',
      search: async (query: string) => {
        try {
          const res = await fetch(`/api/proxy/archive?query=${encodeURIComponent(query)}`);
          if (!res.ok) return [];
          const data = await res.json();
          return data.map((d: any) => ({
            id: `archive_${d.id}`,
            title: d.improved_title || d.id,
            subtitle: new Date(d.timestamp).toLocaleString(),
            icon: <Database className="w-4 h-4 text-green-400" />,
            section: 'History (Archive)',
            perform: () => {
              setActiveTab('archive');
              setTimeout(() => {
                window.dispatchEvent(new CustomEvent('open-archive-session', { detail: d.id }));
              }, 50);
            }
          }));
        } catch {
          return [];
        }
      }
    });

    globalPlugins.forEach(p => {
      registerCommand({
        id: `nav.${p.id}`,
        title: `Go to ${p.uiTabName}`,
        section: 'Navigation',
        perform: () => setActiveTab(p.id)
      });
    });
  }, [isLoggingEnabled, setActiveTab, globalPlugins]);

  const allTabs = [
    ...globalPlugins.map((p, idx) => {

      if (p.id === 'archive') {
        return {
          id: p.id,
          name: p.uiTabName,
          order: idx * 10,
          icon: undefined,
          renderAction: p.hasUiToggle ? () => (
            <button
              onClick={() => updateCoreSettings(!isLoggingEnabled)}
              className={`ml-1 w-8 h-8 flex items-center justify-center rounded-full transition-colors cursor-pointer ${isLoggingEnabled ? 'bg-green-500/20 text-green-400 hover:bg-green-500/30' : 'bg-gray-700/50 text-gray-400 hover:bg-gray-700'}`}
              title={isLoggingEnabled ? "Disable Network Logging" : "Enable Network Logging"}
            >
              <div className={`w-2.5 h-2.5 rounded-full ${isLoggingEnabled ? 'bg-green-400 shadow-[0_0_8px_rgba(74,222,128,0.8)]' : 'bg-gray-500'}`}></div>
            </button>
          ) : undefined,
          renderContent: () => (
            <div className="flex-1 overflow-hidden">
              <ArchiveBrowser />
            </div>
          )
        };
      }

      const PluginComponent = pluginComponents[p.id];
      return {
        id: p.id,
        name: p.uiTabName,
        order: idx * 10,
        icon: undefined,
        renderAction: p.hasUiToggle ? () => {
          const enabled = pluginSettings[p.id]?.enabled || false;
          return (
            <button
              onClick={(e) => { e.stopPropagation(); updatePluginSettings(p.id, { ...pluginSettings[p.id], enabled: !enabled }); }}
              className={`ml-1 w-8 h-8 flex items-center justify-center rounded-full transition-colors cursor-pointer ${enabled ? 'bg-blue-500/20 text-blue-400 hover:bg-blue-500/30' : 'bg-gray-700/50 text-gray-400 hover:bg-gray-700'}`}
              title={enabled ? `Disable ${p.name}` : `Enable ${p.name}`}
            >
              <div className={`w-2.5 h-2.5 rounded-full ${enabled ? 'bg-blue-400 shadow-[0_0_8px_rgba(96,165,250,0.8)]' : 'bg-gray-500'}`}></div>
            </button>
          );
        } : undefined,
        renderContent: PluginComponent ? () => (
          <div className="flex-1 flex overflow-hidden">
            <PluginComponent settings={pluginSettings[p.id] || {}} updateSettings={(s: any) => updatePluginSettings(p.id, s)} />
          </div>
        ) : undefined
      };
    })
  ].sort((a, b) => a.order - b.order);

  return (
    <div className="flex flex-col h-screen bg-gray-950 text-gray-100 font-sans">
      {/* Top Toolbar */}
      <header className="flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-800 shadow-md z-50 relative">
        <div className="flex items-center space-x-6">
          <h1
            onClick={() => setActiveTab('settings')}
            className="text-2xl font-bold bg-gradient-to-r from-blue-400 to-purple-500 bg-clip-text text-transparent cursor-pointer hover:opacity-80 transition-opacity"
            title="Open Global Configuration"
          >
            Aura
          </h1>

          <div className="flex space-x-2 bg-gray-800 p-1 rounded-lg">
            {allTabs.filter(tab => !hiddenTabs.includes(tab.id)).map(tab => (
              <div key={tab.id} className="flex items-center space-x-1 pl-2">
                <button
                  onClick={() => setActiveTab(tab.id)}
                  className={`px-4 py-2 rounded-md transition-colors flex items-center space-x-2 ${activeTab === tab.id ? 'bg-blue-600 text-white shadow-sm' : 'text-gray-400 hover:text-gray-200'}`}
                >
                  {(tab as any).icon && (
                    <div className="w-4 h-4">
                      {/* Icon rendering if exists */}
                    </div>
                  )}
                  <span>{tab.name}</span>
                </button>
                {(tab as any).renderAction && (tab as any).renderAction()}
              </div>
            ))}
          </div>
        </div>

        <div className="flex items-center space-x-6">
          <div className="flex items-center space-x-3">
            <div className="h-8 w-px bg-gray-700 mx-2"></div>
            <NotificationArea
              onChangeTab={setActiveTab}
              onStartStream={(url) => startTask(url, "Updating Llama.cpp")}
              tasks={tasks}
              onOpenTask={openTask}
            />
          </div>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 overflow-hidden flex relative">
        <CommandPalette />
        {activeTab === 'settings' ? (
          <ConfigurationScreen
            globalPlugins={globalPlugins}
            updatePluginOrder={(newOrder: string[]) => {
              fetch('/api/proxy/plugins/order', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(newOrder)
              }).then(() => fetchMetadata());
            }}
            hiddenTabs={hiddenTabs}
            toggleTabHidden={toggleTabHidden}
            defaultTab={defaultTab}
            updateDefaultTab={(tabId) => {
              setDefaultTab(tabId);
              fetch('/api/proxy/settings', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ defaultTab: tabId })
              });
            }}
          />
        ) : (
          allTabs.find(t => t.id === activeTab)?.renderContent?.() || (
            <div className="flex-1 flex items-center justify-center text-gray-500">
              This plugin runs in the background and has no dedicated interface.
            </div>
          )
        )}
      </main>

      <LogViewerModal
        isOpen={isLogsOpen}
        onClose={() => setIsLogsOpen(false)}
        serverHealthy={serverHealthy}
        targetUrl={targetUrl}
        webUiUrl={webUiUrl}
      />

      <ProgressModal
        task={tasks.find(t => !t.isMinimized) || null}
        onClose={closeTask}
        onMinimize={minimizeTask}
      />

      {/* Status Bar */}
      <footer className="h-6 bg-gray-900 border-t border-gray-800 flex items-center px-4 justify-between z-50">
        <div className="flex items-center space-x-4">
          <HardwareWidget />
          <SlotsWidget />
        </div>
        <div className="flex items-center space-x-4">
          <NetworkIndicator />
          <div className="flex items-center space-x-3">
            <button
              onClick={() => setIsLogsOpen(true)}
              title="Click to view target server logs"
              className="flex items-center space-x-1 hover:text-gray-300 transition-colors cursor-pointer"
            >
              {serverHealthy ? <Activity className="w-3 h-3 text-green-400" /> : <ServerCrash className="w-3 h-3 text-red-400" />}
              <span className={`text-[10px] font-medium uppercase tracking-widest ${serverHealthy ? 'text-green-400' : 'text-red-400'}`}>
                {serverHealthy ? 'Online' : 'Offline'}
              </span>
            </button>
            <button
              onClick={restartServer}
              title="Restart Server"
              className="text-gray-500 hover:text-gray-300 transition-colors flex items-center"
            >
              <RefreshCw className="w-3 h-3" />
            </button>
          </div>
        </div>
      </footer>
      <Toaster theme="dark" position="bottom-right" />
    </div>
  );
}

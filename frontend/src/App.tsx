import { useState, useEffect } from 'react';
import { QueuePanel } from './components/QueuePanel';
import { InspectorPanel } from './components/InspectorPanel';
import { ArchiveBrowser } from './components/ArchiveBrowser';
import LiveChatPanel from './components/LiveChatPanel';
import { LogViewerModal } from './components/LogViewerModal';
import { NotificationArea } from './components/NotificationArea';
import { ProgressModal } from './components/ProgressModal';
import { useBackgroundTasks } from './hooks/useBackgroundTasks';
import { pluginComponents } from './plugins';
import { ConfigurationScreen } from './components/ConfigurationScreen';
import { Activity, ServerCrash } from 'lucide-react';
import { HardwareWidget } from './components/HardwareWidget';

export default function App() {
  const [isInterceptRequests, setIsInterceptRequests] = useState(false);
  const [isInterceptResponses, setIsInterceptResponses] = useState(false);
  const [isLoggingEnabled, setIsLoggingEnabled] = useState(false);
  const [serverHealthy, setServerHealthy] = useState(false);
  const [selectedRequestId, setSelectedRequestId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<string>('live');
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
    fetch('/api/proxy/plugins/metadata')
      .then(r => r.json())
      .then(setGlobalPlugins)
      .catch(console.error);
  };

  useEffect(() => {
    fetchMetadata();
    Promise.all([
      fetch('/api/proxy/settings').then(res => res.json()),
      fetch('/api/proxy/target-url').then(res => res.text())
    ]).then(([settingsData, url]) => {
      setIsInterceptRequests(settingsData.interceptRequests);
      setIsInterceptResponses(settingsData.interceptResponses);
      setIsLoggingEnabled(settingsData.loggingEnabled);
      setPluginSettings(settingsData.plugins || {});
      setTargetUrl(url);
      setWebUiUrl(settingsData.webUiUrl || url.replace('/v1', ''));
    }).catch(err => console.error("Error fetching initialization data:", err));
  }, []);

  useEffect(() => {
    const tabNames: Record<string, string> = {
      'intercept': 'Interceptor',
      'live': 'Live Chat',
      'archive': 'Network Logs'
    };
    document.title = `LlamaProxy - ${tabNames[activeTab] || 'Home'}`;

    fetch('/api/proxy/ui/active-tab', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tab: activeTab })
    }).catch(() => {});
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

  const updateCoreSettings = (req: boolean, res: boolean, logging: boolean) => {
    setIsInterceptRequests(req);
    setIsInterceptResponses(res);
    setIsLoggingEnabled(logging);
    fetch('/api/proxy/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 
        interceptRequests: req, 
        interceptResponses: res, 
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
          if (!res.ok) alert('Failed to restart server');
        })
        .catch(err => console.error("Error restarting server:", err));
    }
  };

  const allTabs = [
    ...globalPlugins.map((p, idx) => {
      if (p.id === 'manual-editor') {
        return {
          id: p.id,
          name: p.uiTabName,
          order: idx * 10,
          icon: undefined,
          renderAction: p.hasUiToggle ? () => {
            const isAnyInterceptEnabled = isInterceptRequests || isInterceptResponses;
            return (
              <button 
                onClick={() => {
                  if (!isAnyInterceptEnabled) {
                    updateCoreSettings(true, true, isLoggingEnabled);
                  } else {
                    updateCoreSettings(false, false, isLoggingEnabled);
                  }
                }}
                className={`ml-1 w-8 h-8 flex items-center justify-center rounded-full transition-colors cursor-pointer ${isAnyInterceptEnabled ? 'bg-orange-500/20 text-orange-400 hover:bg-orange-500/30' : 'bg-gray-700/50 text-gray-400 hover:bg-gray-700'}`}
                title={isAnyInterceptEnabled ? "Disable All Interceptions" : "Enable All Interceptions"}
              >
                <div className={`w-2.5 h-2.5 rounded-full ${isAnyInterceptEnabled ? 'bg-orange-400 shadow-[0_0_8px_rgba(249,115,22,0.8)]' : 'bg-gray-500'}`}></div>
              </button>
            );
          } : undefined,
          renderContent: () => (
            <>
              <div className="w-80 h-full">
                <QueuePanel 
                  selectedRequestId={selectedRequestId} 
                  onSelectRequest={setSelectedRequestId}
                  isInterceptRequests={isInterceptRequests}
                  isInterceptResponses={isInterceptResponses}
                  interceptInvalidJson={pluginSettings['manual-editor']?.interceptInvalidJson || false}
                  interceptRegexRules={pluginSettings['manual-editor']?.interceptRegexRules || []}
                  onUpdateSettings={(req, res, log, invalid, intR) => {
                    updateCoreSettings(req, res, log);
                    updatePluginSettings('manual-editor', { ...pluginSettings['manual-editor'], interceptInvalidJson: invalid, interceptRegexRules: intR });
                  }}
                />
              </div>
              <div className="flex-1 bg-gray-950 flex flex-col">
                <InspectorPanel requestId={selectedRequestId} onProcessed={() => setSelectedRequestId(null)} />
              </div>
            </>
          )
        };
      }
      
      if (p.id === 'archive') {
        return {
          id: p.id,
          name: p.uiTabName,
          order: idx * 10,
          icon: undefined,
          renderAction: p.hasUiToggle ? () => (
            <button 
              onClick={() => updateCoreSettings(isInterceptRequests, isInterceptResponses, !isLoggingEnabled)}
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
    }),
    {
      id: 'live',
      name: 'Live Chat',
      order: 1000,
      icon: undefined,
      renderAction: undefined,
      renderContent: () => <LiveChatPanel />
    }
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
            LlamaProxy
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
          <HardwareWidget />

          <div className="h-8 w-px bg-gray-700 mx-2"></div>

          <div className="flex items-center space-x-3">
            <button 
              onClick={() => setIsLogsOpen(true)}
              title="Click to view target server logs"
              className="flex items-center space-x-2 bg-gray-800 px-3 py-1.5 rounded-full hover:bg-gray-700 transition-colors cursor-pointer border border-gray-700"
            >
              {serverHealthy ? <Activity className="w-4 h-4 text-green-400" /> : <ServerCrash className="w-4 h-4 text-red-400" />}
              <span className={`text-sm font-medium ${serverHealthy ? 'text-green-400' : 'text-red-400'}`}>
                {serverHealthy ? 'Llama Online' : 'Llama Offline'}
              </span>
            </button>
            <div className="flex items-center space-x-3">
              <button 
                onClick={restartServer}
                className="px-3 py-1.5 bg-gray-800 hover:bg-gray-700 text-gray-200 text-sm font-medium rounded-lg transition-colors border border-gray-700 flex items-center space-x-2"
              >
                <Activity className="w-4 h-4" />
                <span>Restart Server</span>
              </button>
            </div>
            
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
      <main className="flex-1 overflow-hidden flex">
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
    </div>
  );
}

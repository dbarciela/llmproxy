import { useState, useEffect } from 'react';
import { QueuePanel } from './components/QueuePanel';
import { InspectorPanel } from './components/InspectorPanel';
import { ArchiveBrowser } from './components/ArchiveBrowser';
import LiveChatPanel from './components/LiveChatPanel';
import { LogViewerModal } from './components/LogViewerModal';
import { NotificationArea } from './components/NotificationArea';
import { ProgressModal } from './components/ProgressModal';
import { useBackgroundTasks } from './hooks/useBackgroundTasks';
import { enabledPlugins } from './plugins';
import { Activity, ServerCrash } from 'lucide-react';
import { HardwareWidget } from './components/HardwareWidget';

export default function App() {
  const [isInterceptRequests, setIsInterceptRequests] = useState(false);
  const [isInterceptResponses, setIsInterceptResponses] = useState(false);
  const [isLoggingEnabled, setIsLoggingEnabled] = useState(false);
  const [serverHealthy, setServerHealthy] = useState(false);
  const [selectedRequestId, setSelectedRequestId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<string>('intercept');
  const [isLogsOpen, setIsLogsOpen] = useState(false);
  
  const { tasks, startTask, minimizeTask, openTask, closeTask } = useBackgroundTasks();

  const [pluginSettings, setPluginSettings] = useState<Record<string, any>>({});

  const [webUiUrl, setWebUiUrl] = useState<string>('');
  const [targetUrl, setTargetUrl] = useState<string>('');

  useEffect(() => {
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
    ...enabledPlugins.map(p => ({
      id: p.id,
      name: p.name || p.id,
      order: p.order || 99,
      icon: p.icon,
      renderAction: p.renderTabAction 
        ? () => p.renderTabAction!(pluginSettings[p.id] || {}, (newSettings) => updatePluginSettings(p.id, newSettings)) 
        : undefined,
      renderContent: () => {
        const PluginComponent = p.component;
        return (
          <div className="flex-1 flex overflow-hidden">
            <PluginComponent settings={pluginSettings[p.id] || {}} updateSettings={(s: any) => updatePluginSettings(p.id, s)} />
          </div>
        );
      }
    })),
    {
      id: 'intercept',
      name: 'Interceptor',
      order: 40,
      icon: undefined,
      renderAction: () => {
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
            className={`ml-1 w-8 h-8 flex items-center justify-center rounded-full transition-colors cursor-pointer ${isAnyInterceptEnabled ? 'bg-blue-500/20 text-blue-400 hover:bg-blue-500/30' : 'bg-gray-700/50 text-gray-400 hover:bg-gray-700'}`}
            title={isAnyInterceptEnabled ? "Disable All Interceptions" : "Enable All Interceptions"}
          >
            <div className={`w-2.5 h-2.5 rounded-full ${isAnyInterceptEnabled ? 'bg-blue-400 shadow-[0_0_8px_rgba(96,165,250,0.8)]' : 'bg-gray-500'}`}></div>
          </button>
        );
      },
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
              promptReplaceRules={pluginSettings['prompt-transformer']?.promptReplaceRules || []}
              responseReplaceRules={pluginSettings['prompt-transformer']?.responseReplaceRules || []}
              onUpdateSettings={(req, res, log, invalid, intR, pR, rR) => {
                updateCoreSettings(req, res, log);
                updatePluginSettings('manual-editor', { ...pluginSettings['manual-editor'], interceptInvalidJson: invalid, interceptRegexRules: intR });
                updatePluginSettings('prompt-transformer', { ...pluginSettings['prompt-transformer'], promptReplaceRules: pR, responseReplaceRules: rR });
              }}
            />
          </div>
          <div className="flex-1 bg-gray-950 flex flex-col">
            <InspectorPanel requestId={selectedRequestId} onProcessed={() => setSelectedRequestId(null)} />
          </div>
        </>
      )
    },
    {
      id: 'archive',
      name: 'Network Logs',
      order: 50,
      icon: undefined,
      renderAction: () => (
        <button 
          onClick={() => updateCoreSettings(isInterceptRequests, isInterceptResponses, !isLoggingEnabled)}
          className={`ml-1 w-8 h-8 flex items-center justify-center rounded-full transition-colors cursor-pointer ${isLoggingEnabled ? 'bg-purple-500/20 text-purple-400 hover:bg-purple-500/30' : 'bg-gray-700/50 text-gray-400 hover:bg-gray-700'}`}
          title={isLoggingEnabled ? "Disable Network Logging" : "Enable Network Logging"}
        >
          <div className={`w-2.5 h-2.5 rounded-full ${isLoggingEnabled ? 'bg-purple-400 shadow-[0_0_8px_rgba(168,85,247,0.8)]' : 'bg-gray-500'}`}></div>
        </button>
      ),
      renderContent: () => (
        <div className="flex-1 overflow-hidden">
          <ArchiveBrowser />
        </div>
      )
    },
    {
      id: 'live',
      name: 'Live Chat',
      order: 100,
      icon: undefined,
      renderContent: () => <LiveChatPanel />
    }
  ].sort((a, b) => a.order - b.order);

  return (
    <div className="flex flex-col h-screen bg-gray-950 text-gray-100 font-sans">
      {/* Top Toolbar */}
      <header className="flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-800 shadow-md z-50 relative">
        <div className="flex items-center space-x-6">
          <h1 className="text-2xl font-bold bg-gradient-to-r from-blue-400 to-purple-500 bg-clip-text text-transparent">LlamaProxy</h1>
          
          <div className="flex space-x-2 bg-gray-800 p-1 rounded-lg">
            {allTabs.map(tab => (
              <div key={tab.id} className="flex items-center space-x-1 pl-2">
                <button 
                  onClick={() => setActiveTab(tab.id)}
                  className={`px-4 py-2 rounded-md transition-colors flex items-center space-x-2 ${activeTab === tab.id ? 'bg-blue-600 text-white shadow-sm' : 'text-gray-400 hover:text-gray-200'}`}
                >
                  {tab.icon && <tab.icon className="w-4 h-4" />}
                  <span>{tab.name}</span>
                </button>
                {tab.renderAction && tab.renderAction()}
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
        {allTabs.find(t => t.id === activeTab)?.renderContent()}
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

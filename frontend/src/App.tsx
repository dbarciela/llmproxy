import { useState, useEffect } from 'react';
import { QueuePanel } from './components/QueuePanel';
import { InspectorPanel } from './components/InspectorPanel';
import { ArchiveBrowser } from './components/ArchiveBrowser';
import LiveChatPanel from './components/LiveChatPanel';
import { LogViewerModal } from './components/LogViewerModal';
import { NotificationArea } from './components/NotificationArea';
import { ProgressModal } from './components/ProgressModal';
import { useBackgroundTasks } from './hooks/useBackgroundTasks';
import { Activity, ServerCrash } from 'lucide-react';

export default function App() {
  const [isInterceptRequests, setIsInterceptRequests] = useState(false);
  const [isInterceptResponses, setIsInterceptResponses] = useState(false);
  const [isLoggingEnabled, setIsLoggingEnabled] = useState(false);
  const [serverHealthy, setServerHealthy] = useState(false);
  const [selectedRequestId, setSelectedRequestId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'intercept' | 'live' | 'archive'>('intercept');
  const [isLogsOpen, setIsLogsOpen] = useState(false);
  
  const { tasks, startTask, minimizeTask, openTask, closeTask } = useBackgroundTasks();

  const [interceptRegex, setInterceptRegex] = useState<string>('');
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
      setInterceptRegex(settingsData.interceptRegex || '');
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

  const updateSettings = (req: boolean, res: boolean, logging: boolean, regex: string) => {
    setIsInterceptRequests(req);
    setIsInterceptResponses(res);
    setIsLoggingEnabled(logging);
    setInterceptRegex(regex);
    fetch('/api/proxy/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ interceptRequests: req, interceptResponses: res, loggingEnabled: logging, interceptRegex: regex })
    }).catch(err => console.error("Error updating settings:", err));
  };

  const restartServer = () => {
    if (window.confirm("Are you sure you want to restart the Llama server?")) {
      fetch('/api/proxy/restart-target', { method: 'POST' })
        .then(res => {
          if (!res.ok) alert('Failed to restart server');
        })
        .catch(err => console.error("Error restarting server:", err));
    }
  };

  return (
    <div className="flex flex-col h-screen bg-gray-950 text-gray-100 font-sans">
      {/* Top Toolbar */}
      <header className="flex items-center justify-between px-6 py-4 bg-gray-900 border-b border-gray-800 shadow-md z-50 relative">
        <div className="flex items-center space-x-6">
          <h1 className="text-2xl font-bold bg-gradient-to-r from-blue-400 to-purple-500 bg-clip-text text-transparent">LlamaProxy</h1>
          
          <div className="flex space-x-2 bg-gray-800 p-1 rounded-lg">
            <button 
              onClick={() => setActiveTab('intercept')}
              className={`px-4 py-2 rounded-md transition-colors ${activeTab === 'intercept' ? 'bg-blue-600 text-white shadow-sm' : 'text-gray-400 hover:text-gray-200'}`}
            >
              Interceptor
            </button>
            <button 
              onClick={() => setActiveTab('live')}
              className={`px-4 py-2 rounded-md transition-colors ${activeTab === 'live' ? 'bg-blue-600 text-white shadow-sm' : 'text-gray-400 hover:text-gray-200'}`}
            >
              Live Chat
            </button>
            <button 
              onClick={() => setActiveTab('archive')}
              className={`px-4 py-2 rounded-md transition-colors ${activeTab === 'archive' ? 'bg-blue-600 text-white shadow-sm' : 'text-gray-400 hover:text-gray-200'}`}
            >
              Network Logs
            </button>
          </div>
        </div>
        
        <div className="flex items-center space-x-6">
          <label className="flex items-center space-x-2 cursor-pointer">
            <span className="text-sm font-medium">Network Logging:</span>
            <button 
              onClick={() => updateSettings(isInterceptRequests, isInterceptResponses, !isLoggingEnabled, interceptRegex)}
              className={`w-12 h-6 rounded-full transition-colors flex items-center px-1 ${isLoggingEnabled ? 'bg-purple-500' : 'bg-gray-700'}`}
            >
              <div className={`w-4 h-4 bg-white rounded-full shadow-md transform transition-transform ${isLoggingEnabled ? 'translate-x-6' : 'translate-x-0'}`}></div>
            </button>
          </label>

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
        {activeTab === 'intercept' ? (
          <>
            {/* Left Sidebar: Queue */}
            <div className="w-80 border-r border-gray-800 bg-gray-900/50 flex flex-col">
              <QueuePanel 
                selectedRequestId={selectedRequestId} 
                onSelectRequest={setSelectedRequestId}
                isInterceptRequests={isInterceptRequests}
                isInterceptResponses={isInterceptResponses}
                interceptRegex={interceptRegex}
                onUpdateSettings={(req, res, regex) => updateSettings(req, res, isLoggingEnabled, regex)}
              />
            </div>
            
            {/* Main Editor Area */}
            <div className="flex-1 bg-gray-950 flex flex-col">
              <InspectorPanel 
                requestId={selectedRequestId} 
                onProcessed={() => setSelectedRequestId(null)} 
              />
            </div>
          </>
        ) : activeTab === 'live' ? (
          <LiveChatPanel />
        ) : (
          <div className="flex-1 overflow-hidden">
            <ArchiveBrowser />
          </div>
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

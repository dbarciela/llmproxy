import { useState, useEffect, useRef } from 'react';
import { X, Terminal, PowerOff } from 'lucide-react';

interface LogViewerModalProps {
  isOpen: boolean;
  onClose: () => void;
  serverHealthy?: boolean;
  targetUrl?: string; // Kept in interface just in case App.tsx passes it
  webUiUrl?: string;
}

export function LogViewerModal({ isOpen, onClose, serverHealthy, webUiUrl }: LogViewerModalProps) {
  const [logs, setLogs] = useState<string>('');
  const [autoScroll, setAutoScroll] = useState(true);
  const logEndRef = useRef<HTMLDivElement>(null);
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen) return;

    setLogs(''); // clear logs on open

    const es = new EventSource('/api/proxy/target-logs-stream');

    es.addEventListener('log', (e: any) => {
      try {
        const payload = JSON.parse(e.data);
        if (payload.type === 'INITIAL' || payload.type === 'APPEND') {
          setLogs(prev => prev + payload.data);
        }
      } catch (err) {
        console.error('Failed to parse log chunk', err);
      }
    });

    es.addEventListener('error', () => {
      console.error('SSE Error for logs');
      es.close();
    });

    return () => {
      es.close();
    };
  }, [isOpen]);

  useEffect(() => {
    if (autoScroll && logEndRef.current) {
      logEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs, autoScroll]);

  const handleScroll = () => {
    if (scrollContainerRef.current) {
      const { scrollTop, scrollHeight, clientHeight } = scrollContainerRef.current;
      const isAtBottom = scrollHeight - scrollTop - clientHeight < 50;
      setAutoScroll(isAtBottom);
    }
  };

  const killServer = () => {
    if (window.confirm("Are you sure you want to kill the target server?")) {
      fetch('/api/proxy/kill-target', { method: 'POST' })
        .then(res => {
          if (!res.ok) alert('Failed to kill server');
        })
        .catch(err => console.error("Error killing server:", err));
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/80 z-50 flex items-center justify-center p-4">
      <div className="bg-gray-900 border border-gray-800 rounded-xl shadow-2xl w-full max-w-5xl h-[80vh] flex flex-col">
        <div className="flex items-center justify-between p-4 border-b border-gray-800">
          <div className="flex items-center space-x-4 text-gray-200">
            <div className="flex items-center space-x-2">
              <Terminal className="w-5 h-5" />
              <h2 className="font-semibold">Target Server Console Logs</h2>
            </div>
            {serverHealthy && (
              <div className="flex items-center space-x-2">
                <a 
                  href={webUiUrl} 
                  target="_blank" 
                  rel="noopener noreferrer"
                  className={`text-xs px-3 py-1 bg-purple-600/20 text-purple-400 hover:bg-purple-600/40 hover:text-purple-300 border border-purple-500/30 rounded-full transition-colors flex items-center shadow-sm ${!webUiUrl && 'opacity-50 pointer-events-none'}`}
                >
                  <span className="mr-1">🔗</span> Open Web UI
                </a>
              </div>
            )}
          </div>
          <div className="flex items-center space-x-3">
            <button 
              onClick={killServer}
              title="Kill Target Server"
              className="text-xs flex items-center space-x-1 px-3 py-1.5 bg-red-900/30 text-red-400 hover:bg-red-900/50 hover:text-red-300 border border-red-800/50 rounded-lg transition-colors shadow-sm"
            >
              <PowerOff className="w-3.5 h-3.5" />
              <span>Kill Server</span>
            </button>
            <button 
              onClick={onClose}
              className="text-gray-500 hover:text-gray-300 transition-colors p-1"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>
        
        <div 
          ref={scrollContainerRef}
          onScroll={handleScroll}
          className="flex-1 p-4 overflow-y-auto bg-black font-mono text-xs text-green-400 whitespace-pre-wrap break-words"
        >
          {logs || "Waiting for logs..."}
          <div ref={logEndRef} />
        </div>
        
        <div className="p-3 border-t border-gray-800 bg-gray-900 flex justify-between items-center text-xs text-gray-500">
          <div className="flex items-center space-x-1">
            <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></div>
            <span>Live Stream (SSE)</span>
          </div>
          <label className="flex items-center space-x-2 cursor-pointer">
            <input 
              type="checkbox" 
              checked={autoScroll}
              onChange={(e) => setAutoScroll(e.target.checked)}
              className="rounded border-gray-700 bg-gray-800 text-purple-600 focus:ring-purple-500 focus:ring-offset-gray-900"
            />
            <span>Auto-scroll</span>
          </label>
        </div>
      </div>
    </div>
  );
}

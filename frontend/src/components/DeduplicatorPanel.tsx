import { useEffect, useState } from 'react';
import { ChevronDown, ChevronRight, Activity, Scissors } from 'lucide-react';

interface DeduplicatorPanelProps {
  settings: any;
  updateSettings: (newSettings: any) => void;
}

interface DeduplicatedBlock {
  id: string;
  originalText: string;
}

export function DeduplicatorPanel({ settings, updateSettings }: DeduplicatorPanelProps) {
  const deduplicationThreshold = settings?.threshold || 500;
  const [totalSavedChars, setTotalSavedChars] = useState(0);
  const [totalOriginalChars, setTotalOriginalChars] = useState(0);
  const [blocks, setBlocks] = useState<DeduplicatedBlock[]>([]);
  const [expandedBlocks, setExpandedBlocks] = useState<Record<string, boolean>>({});
  const [localThreshold, setLocalThreshold] = useState(deduplicationThreshold.toString());

  useEffect(() => {
    setLocalThreshold(deduplicationThreshold.toString());
  }, [deduplicationThreshold]);

  useEffect(() => {
    fetch('/api/proxy/plugins/context-deduplicator/stats')
      .then(res => res.json())
      .then(stats => {
        if (stats.savedChars) {
          setTotalSavedChars(stats.savedChars);
        }
        if (stats.totalOriginalChars) {
          setTotalOriginalChars(stats.totalOriginalChars);
        }
        if (stats.blocks) {
          const newBlocks: DeduplicatedBlock[] = Object.entries(stats.blocks).map(([id, text]) => ({
            id,
            originalText: text as string
          }));
          setBlocks(newBlocks.reverse().slice(0, 50)); // keep last 50, newest first
        }
      })
      .catch(console.error);
  }, []);

  useEffect(() => {
    const eventSource = new EventSource('/api/proxy/stream');
    eventSource.addEventListener('live-chat', (e: any) => {
      try {
        const payload = JSON.parse(e.data);
        if (payload.type === 'DEDUPLICATION_STATS') {
          const stats = payload.data;
          if (stats.savedChars) {
            setTotalSavedChars(prev => prev + stats.savedChars);
          }
          if (stats.totalOriginalChars) {
            setTotalOriginalChars(prev => prev + stats.totalOriginalChars);
          }
          if (stats.blocks) {
            const newBlocks: DeduplicatedBlock[] = Object.entries(stats.blocks).map(([id, text]) => ({
              id,
              originalText: text as string
            }));
            setBlocks(prev => [...newBlocks, ...prev].slice(0, 50)); // keep last 50
          }
        }
      } catch (err) {
        console.error(err);
      }
    });

    return () => {
      eventSource.close();
    };
  }, []);

  const handleSaveThreshold = () => {
    const val = parseInt(localThreshold, 10);
    if (!isNaN(val) && val >= 50) {
      updateSettings({ ...settings, threshold: val });
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') handleSaveThreshold();
  };

  const toggleExpand = (id: string) => {
    setExpandedBlocks(prev => ({ ...prev, [id]: !prev[id] }));
  };

  return (
    <div className="flex-1 flex flex-col bg-gray-900 p-6 overflow-hidden">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h2 className="text-2xl font-bold text-gray-100 flex items-center">
            <Scissors className="w-6 h-6 mr-3 text-blue-400" />
            Context Deduplicator
          </h2>
          <p className="text-gray-400 mt-1">Automatically remove duplicated context in large conversations to save tokens and VRAM.</p>
        </div>
        
        <div className="bg-gray-800 rounded-xl p-4 flex items-center space-x-6 border border-gray-700 shadow-lg">
          <div className="flex flex-col justify-center">
            <span className="text-xs text-gray-400 uppercase tracking-wider font-semibold mb-1">Threshold</span>
            <input 
              type="number"
              value={localThreshold}
              onChange={e => setLocalThreshold(e.target.value)}
              onBlur={handleSaveThreshold}
              onKeyDown={handleKeyDown}
              className="w-24 bg-gray-900 border border-gray-700 rounded-md px-2 py-1 text-sm font-bold text-white outline-none focus:border-blue-500 text-center"
              title="Press Enter to save"
            />
          </div>
          <div className="h-10 w-px bg-gray-700"></div>
          <div className="flex flex-col">
            <span className="text-xs text-gray-400 uppercase tracking-wider font-semibold mb-1">Total Saved</span>
            <span className="text-xl font-bold text-green-400">
              {(totalSavedChars / 1024).toFixed(2)} KB <span className="text-sm font-normal text-green-500/70 ml-1">({totalOriginalChars > 0 ? ((totalSavedChars / totalOriginalChars) * 100).toFixed(1) : "0.0"}%)</span>
            </span>
          </div>
          <div className="h-10 w-px bg-gray-700"></div>
          <div className="flex flex-col">
            <span className="text-xs text-gray-400 uppercase tracking-wider font-semibold mb-1">Tokens (Est.)</span>
            <span className="text-xl font-bold text-blue-400">
              {Math.round(totalSavedChars / 4).toLocaleString()} <span className="text-sm font-normal text-blue-500/70 ml-1">({totalOriginalChars > 0 ? ((totalSavedChars / totalOriginalChars) * 100).toFixed(1) : "0.0"}%)</span>
            </span>
          </div>
        </div>
      </div>


      <div className="flex-1 bg-gray-800 rounded-xl border border-gray-700 flex flex-col overflow-hidden">
        <div className="p-4 border-b border-gray-700 bg-gray-800/50 flex justify-between items-center">
          <h3 className="font-medium text-gray-200 flex items-center">
            <Activity className="w-4 h-4 mr-2 text-purple-400" />
            Recently Deduplicated Blocks
          </h3>
          <span className="text-xs text-gray-400">Showing last {blocks.length} blocks</span>
        </div>
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {blocks.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-gray-500">
              <Scissors className="w-12 h-12 mb-3 opacity-20" />
              <p>No duplicated blocks found yet.</p>
              <p className="text-sm">Make sure Deduplication is enabled and you send long, repeated context.</p>
            </div>
          ) : (
            blocks.map(block => (
              <div key={block.id} className="border border-gray-700 rounded-lg overflow-hidden bg-gray-900/50">
                <button 
                  onClick={() => toggleExpand(block.id)}
                  className="w-full flex items-center justify-between p-3 hover:bg-gray-800 transition-colors"
                >
                  <div className="flex items-center space-x-3">
                    {expandedBlocks[block.id] ? <ChevronDown className="w-4 h-4 text-gray-400" /> : <ChevronRight className="w-4 h-4 text-gray-400" />}
                    <span className="font-mono text-sm text-purple-400">{block.id}</span>
                    <span className="text-xs bg-green-900/30 text-green-400 px-2 py-0.5 rounded-full border border-green-800/50">
                      -{block.originalText.length} chars
                    </span>
                  </div>
                </button>
                {expandedBlocks[block.id] && (
                  <div className="p-4 border-t border-gray-700 bg-gray-950">
                    <pre className="text-xs text-gray-300 font-mono overflow-x-auto whitespace-pre-wrap">
                      {block.originalText}
                    </pre>
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

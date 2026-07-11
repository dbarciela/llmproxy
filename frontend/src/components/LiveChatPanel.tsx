import { useEffect, useState, useRef } from 'react';
import { Download } from 'lucide-react';
import { ChatViewer } from './ChatViewer';
import { downloadStringAsFile } from '../utils/downloadUtils';
import { parseLlamaResponse } from '../utils/chatParser';

export default function LiveChatPanel() {
  const [messages, setMessages] = useState<any[]>([]);
  const [collapseXmlMode, setCollapseXmlMode] = useState(true);
  const [liveScroll, setLiveScroll] = useState(true);
  const scrollRef = useRef<HTMLDivElement>(null);
  
  const [metrics, setMetrics] = useState<any>(null);
  const [contextLimit, setContextLimit] = useState<number | null>(null);
  
  useEffect(() => {
    if (liveScroll && scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, liveScroll]);
  
  useEffect(() => {
    const es = new EventSource('/api/proxy/live');
    
    let currentAssistantContent = "";
    let currentToolCalls: any[] = [];
    let isStream = false;

    es.addEventListener('live-chat', (e: any) => {
      const payload = JSON.parse(e.data);
      if (payload.type === 'REQUEST') {
        try {
            const req = JSON.parse(payload.data);
            if (req.messages) {
                setMessages(req.messages);
                currentAssistantContent = "";
                currentToolCalls = [];
                isStream = false;
            }
        } catch { /* ignore */ }
      } else if (payload.type === 'CHUNK') {
         isStream = true;
         if (payload.data !== '[DONE]') {
             try {
                 const chunk = JSON.parse(payload.data);
                 if (chunk.choices && chunk.choices[0].delta) {
                     const delta = chunk.choices[0].delta;
                     if (delta.content) {
                         currentAssistantContent += delta.content;
                     }
                     if (delta.tool_calls) {
                         for (const tc of delta.tool_calls) {
                             const index = tc.index;
                             if (!currentToolCalls[index]) {
                                 currentToolCalls[index] = {
                                     id: tc.id,
                                     type: tc.type || 'function',
                                     function: { name: tc.function?.name || '', arguments: tc.function?.arguments || '' }
                                 };
                             } else if (tc.function?.arguments) {
                                 currentToolCalls[index].function.arguments += tc.function.arguments;
                             }
                         }
                     }
                 }
             } catch { /* ignore */ }
         }
         
         setMessages(prev => {
             if (prev.length === 0) return prev;
             const newMsgs = [...prev];
             const tcFiltered = currentToolCalls.filter(tc => tc !== undefined);
             const updatedMsg: any = { role: 'assistant', content: currentAssistantContent };
             if (tcFiltered.length > 0) updatedMsg.tool_calls = tcFiltered;

             if (newMsgs[newMsgs.length - 1].role === 'assistant') {
                 newMsgs[newMsgs.length - 1] = updatedMsg;
             } else {
                 newMsgs.push(updatedMsg);
             }
             return newMsgs;
         });
      } else if (payload.type === 'DONE') {
          if (!isStream && payload.data) {
              const finalMsg = parseLlamaResponse(payload.data);
              if (finalMsg && finalMsg.role === 'assistant') {
                  setMessages(prev => {
                      if (prev.length === 0) return prev;
                      const newMsgs = [...prev];
                      if (newMsgs[newMsgs.length - 1].role === 'assistant') {
                          newMsgs[newMsgs.length - 1] = finalMsg;
                      } else {
                          newMsgs.push(finalMsg);
                      }
                      return newMsgs;
                  });
              }
          }
      } else if (payload.type === 'METRICS') {
          try {
              setMetrics(JSON.parse(payload.data));
          } catch { /* ignore */ }
      } else if (payload.type === 'CONTEXT_LIMIT') {
          try {
              setContextLimit(parseInt(payload.data));
          } catch { /* ignore */ }
      }
    });

    return () => es.close();
  }, []);

  const handleDownload = () => {
    if (messages.length === 0) return;
    const data = messages.map(m => `### ${m.role === 'user' ? 'User' : 'Assistant'}\n\n${m.content}`).join('\n\n---\n\n');
    downloadStringAsFile(data, 'live-chat.md');
  };

  return (
    <div className="flex-1 bg-gray-950 flex flex-col overflow-hidden relative">
      <div className="absolute top-4 right-6 z-10 flex space-x-2">
        <label className="flex items-center space-x-2 text-xs text-gray-400 bg-gray-900 px-3 py-1.5 rounded-lg border border-gray-800 cursor-pointer hover:text-gray-200">
          <input 
            type="checkbox" 
            checked={liveScroll} 
            onChange={e => setLiveScroll(e.target.checked)} 
            className="rounded border-gray-600 bg-gray-700 text-purple-600 focus:ring-purple-500"
          />
          <span>Auto-Scroll</span>
        </label>
        <label className="flex items-center space-x-2 text-xs text-gray-400 bg-gray-900 px-3 py-1.5 rounded-lg border border-gray-800 cursor-pointer hover:text-gray-200">
          <input 
            type="checkbox" 
            checked={collapseXmlMode} 
            onChange={e => setCollapseXmlMode(e.target.checked)} 
            className="rounded border-gray-600 bg-gray-700 text-purple-600 focus:ring-purple-500"
          />
          <span>Smart Collapse XML</span>
        </label>
        <button
          onClick={handleDownload}
          title="Download Chat as Markdown"
          className="flex items-center space-x-2 text-xs text-gray-400 bg-gray-900 px-3 py-1.5 rounded-lg border border-gray-800 cursor-pointer hover:text-gray-200 transition-colors"
        >
          <Download className="w-3.5 h-3.5" />
          <span>Download</span>
        </button>
      </div>
      <div ref={scrollRef} className="absolute inset-0 overflow-y-auto p-6 mt-10">
        {messages.length === 0 ? (
          <div className="flex h-full items-center justify-center text-gray-500">
            Waiting for chat traffic...
          </div>
        ) : (
          <ChatViewer messages={messages as any} collapseXmlMode={collapseXmlMode} />
        )}
      </div>

      {/* Floating Metrics Widget (Bottom Right) */}
      {metrics && (
        <div className="absolute bottom-6 right-8 z-20 flex items-center space-x-4 text-sm text-gray-300 bg-gray-900/90 backdrop-blur-md px-4 py-2.5 rounded-xl border border-gray-700 shadow-2xl">
          <div className="flex flex-col">
            <span className="text-[10px] text-gray-500 uppercase font-bold tracking-wider mb-0.5">Speed</span>
            <span title="Tokens per Second" className="font-mono text-purple-400">{metrics.tokensPerSec.toFixed(1)} t/s</span>
          </div>
          <div className="w-px h-8 bg-gray-700"></div>
          <div className="flex flex-col">
            <span className="text-[10px] text-gray-500 uppercase font-bold tracking-wider mb-0.5">Latency</span>
            <span title="Time To First Token" className="font-mono text-blue-400">{metrics.ttft}ms</span>
          </div>
          {metrics.totalTokens > 0 && (
            <>
              <div className="w-px h-8 bg-gray-700"></div>
              <div className="flex flex-col">
                <span className="text-[10px] text-gray-500 uppercase font-bold tracking-wider mb-0.5">Context</span>
                <span 
                  title="Context Cost" 
                  className={`font-mono ${contextLimit && metrics.totalTokens > contextLimit * 0.85 ? 'text-red-400 font-bold animate-pulse' : 'text-green-400'}`}
                >
                  {contextLimit && metrics.totalTokens > contextLimit * 0.85 && '🚨 '}
                  {metrics.totalTokens.toLocaleString()} {contextLimit ? `/ ${contextLimit.toLocaleString()}` : ''}
                </span>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}

import { useState, useEffect, useRef } from 'react';
import { Layers, Activity, Server, Eye, EyeOff, Hash, Percent } from 'lucide-react';
import { sseService } from '../services/sseService';
import { registerCommand, commandRegistry } from '../plugins/PluginRegistry';

interface SlotData {
  id: number;
  n_ctx: number;
  speculative: boolean;
  is_processing: boolean;
  id_task: number;
  n_prompt_tokens: number;
  n_prompt_tokens_processed: number;
  n_prompt_tokens_cache: number;
  params: Record<string, any>;
  next_token?: {
    has_next_token: boolean;
    n_remain: number;
    n_decoded: number;
  }[];
}

export function SlotsWidget() {
  const [slots, setSlots] = useState<SlotData[]>([]);
  const [rates, setRates] = useState<Record<number, { pp: number, ts: number }>>({});
  const [globalMetrics, setGlobalMetrics] = useState<any>(null);
  const historyRef = useRef<Record<number, { time: number, processed: number, decoded: number }>>({});

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [showInStatusBar, setShowInStatusBar] = useState(() => {
    try {
      const saved = localStorage.getItem('slots_widget_visible');
      return saved !== 'false'; // Default true
    } catch {
      return true;
    }
  });

  const toggleVisibility = (e: React.MouseEvent) => {
    e.stopPropagation();
    const newVal = !showInStatusBar;
    setShowInStatusBar(newVal);
    localStorage.setItem('slots_widget_visible', newVal.toString());
  };

  useEffect(() => {
    const unsubscribe = sseService.subscribe((payload: any) => {
      if (payload.type === 'SLOTS') {
        try {
          const data = typeof payload.data === 'string' ? JSON.parse(payload.data) : payload.data;
          if (Array.isArray(data)) {
            const now = Date.now();
            const history = historyRef.current;
            
            setRates((prevRates) => {
              const newRates = { ...prevRates };
              data.forEach((slot: SlotData) => {
                const decoded = slot.next_token && slot.next_token.length > 0 ? slot.next_token[0].n_decoded : 0;
                const processed = slot.n_prompt_tokens_processed || 0;
                
                if (history[slot.id]) {
                  const prev = history[slot.id];
                  const deltaSeconds = (now - prev.time) / 1000;
                  if (deltaSeconds > 0) {
                    const pp = Math.max(0, Math.round((processed - prev.processed) / deltaSeconds));
                    const ts = Math.max(0, Math.round((decoded - prev.decoded) / deltaSeconds));
                    // Only update if there's actually processing happening, otherwise keep previous rate (or 0 if idle)
                    newRates[slot.id] = { 
                      pp: processed > prev.processed ? pp : 0, 
                      ts: decoded > prev.decoded ? ts : 0 
                    };
                  }
                }
                history[slot.id] = { time: now, processed, decoded };
              });
              return newRates;
            });

            setSlots(data);
          }
        } catch (err) {
          console.error("Error parsing slots data", err);
        }
      } else if (payload.type === 'METRICS') {
        try {
          const data = typeof payload.data === 'string' ? JSON.parse(payload.data) : payload.data;
          setGlobalMetrics(data);
        } catch (err) {
          console.error("Error parsing metrics data", err);
        }
      }
    });

    // Register Command Palette shortcut
    if (!commandRegistry.find(c => c.id === 'open-metrics')) {
      registerCommand({
        id: 'open-metrics',
        title: 'Open Metrics',
        icon: <Layers className="w-4 h-4 text-orange-400" />,
        section: 'System',
        perform: () => setIsModalOpen(true)
      });
    }

    return () => {
      unsubscribe();
    };
  }, []);

  const activeSlots = slots.filter(s => s.is_processing || (s.next_token && s.next_token[0]?.n_decoded > 0));
  
  // Calculate average context utilization across active slots
  let avgCtxPercent = 0;
  if (activeSlots.length > 0) {
    const totalPercent = activeSlots.reduce((acc, slot) => {
      const decoded = slot.next_token && slot.next_token.length > 0 ? slot.next_token[0].n_decoded : 0;
      const used = (slot.n_prompt_tokens || 0) + decoded;
      return acc + (used / slot.n_ctx);
    }, 0);
    avgCtxPercent = Math.round((totalPercent / activeSlots.length) * 100);
  }

  return (
    <>
      <div 
        className={`flex items-center space-x-2 text-xs font-mono transition-colors ${showInStatusBar ? 'text-gray-400 hover:text-gray-200 cursor-pointer' : 'text-gray-600 cursor-pointer'}`}
        onClick={() => setIsModalOpen(true)}
        title={showInStatusBar ? "Click to open Metrics" : "Metrics are hidden. Click to open."}
      >
        <Layers className={`w-3.5 h-3.5 ${showInStatusBar ? 'text-orange-400' : 'text-gray-600'}`} />
        
        {showInStatusBar ? (
          <>
            <span className="font-bold text-gray-300">Metrics</span>
            <span className={activeSlots.length > 0 ? 'text-orange-400 font-bold' : 'text-gray-500'}>
              {activeSlots.length}/{slots.length || '-'}
            </span>
            
            {activeSlots.length > 0 && (
              <>
                <div className="w-px h-3 bg-gray-700 mx-1"></div>
                <span className="text-gray-400">CTX</span>
                <span className={`${avgCtxPercent > 80 ? 'text-red-400' : avgCtxPercent > 50 ? 'text-yellow-400' : 'text-green-400'}`}>
                  {avgCtxPercent}%
                </span>
                
                {activeSlots.some(s => s.is_processing && (s.n_prompt_tokens_processed || 0) < (s.n_prompt_tokens || 0)) && (
                  <span title="Processing Prompt"><Activity className="w-3.5 h-3.5 text-blue-400 animate-pulse ml-1" /></span>
                )}
              </>
            )}
          </>
        ) : (
          <span className="text-[10px] uppercase">Metrics Hidden</span>
        )}
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4" onClick={() => setIsModalOpen(false)}>
          <div className="bg-gray-900 border border-gray-700 rounded-xl shadow-2xl w-full max-w-4xl max-h-[85vh] flex flex-col overflow-hidden animate-in fade-in zoom-in duration-200" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between p-4 border-b border-gray-800 bg-gray-900/50">
              <div className="flex items-center space-x-3">
                <div className="p-2 bg-orange-500/20 rounded-lg">
                  <Server className="w-5 h-5 text-orange-400" />
                </div>
                <div>
                  <h2 className="text-lg font-bold text-gray-100 flex items-center">
                    Llama.cpp Metrics
                  </h2>
                  <p className="text-xs text-gray-400">Real-time internal context & decoding statistics</p>
                </div>
              </div>
              <div className="flex items-center space-x-3">
                <button
                  onClick={toggleVisibility}
                  className="flex items-center space-x-1 px-3 py-1.5 bg-gray-800 hover:bg-gray-700 text-gray-300 rounded-md text-xs font-medium transition-colors border border-gray-700"
                  title="Toggle visibility in the bottom status bar"
                >
                  {showInStatusBar ? <Eye className="w-3.5 h-3.5 mr-1" /> : <EyeOff className="w-3.5 h-3.5 mr-1" />}
                  {showInStatusBar ? 'Hide from Status Bar' : 'Show in Status Bar'}
                </button>
                <button
                  onClick={() => setIsModalOpen(false)}
                  className="text-gray-400 hover:text-white px-3 py-1 bg-gray-800 rounded-md border border-gray-700 hover:bg-gray-700 transition-colors text-sm"
                >
                  Close (Esc)
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-6 space-y-4 bg-gray-950">
              {globalMetrics && globalMetrics.error === 'disabled' && (
                <div className="bg-yellow-500/10 border border-yellow-500/30 rounded-lg p-3 text-sm text-yellow-200/80 flex items-center mb-4">
                  <Activity className="w-4 h-4 mr-2" />
                  Global metrics are disabled. Start Llama.cpp with --metrics to see server-wide statistics.
                </div>
              )}
              {globalMetrics && !globalMetrics.error && (
                <div className="bg-gray-800 border border-gray-700 rounded-xl p-4 mb-6 shadow-md">
                  <div className="text-xs uppercase text-gray-500 font-bold tracking-wider mb-3">Global Server Health</div>
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                     <div>
                       <div className="text-[10px] text-gray-500 uppercase tracking-widest">Global PP</div>
                       <div className="text-lg font-mono text-blue-400">{globalMetrics.prompt_tokens_seconds?.toFixed(1) || 0} <span className="text-xs text-gray-500">t/s</span></div>
                     </div>
                     <div>
                       <div className="text-[10px] text-gray-500 uppercase tracking-widest">Global TG</div>
                       <div className="text-lg font-mono text-green-400">{globalMetrics.predicted_tokens_seconds?.toFixed(1) || 0} <span className="text-xs text-gray-500">t/s</span></div>
                     </div>
                     <div>
                       <div className="text-[10px] text-gray-500 uppercase tracking-widest">Reqs Processing</div>
                       <div className="text-lg font-mono text-gray-200">{globalMetrics.requests_processing || 0}</div>
                     </div>
                     <div>
                       <div className="text-[10px] text-gray-500 uppercase tracking-widest">Reqs Deferred</div>
                       <div className="text-lg font-mono text-yellow-400">{globalMetrics.requests_deferred || 0}</div>
                     </div>
                  </div>
                </div>
              )}

              {slots.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-gray-500">
                  <Activity className="w-12 h-12 mb-4 opacity-20" />
                  <p>Waiting for slot data from Llama.cpp...</p>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {slots.map((slot) => {
                    const decoded = slot.next_token && slot.next_token.length > 0 ? slot.next_token[0].n_decoded : 0;
                    const n_prompt = slot.n_prompt_tokens || 0;
                    const n_processed = slot.n_prompt_tokens_processed || 0;
                    const used = n_prompt + decoded;
                    const percent = Math.min(100, Math.round((used / slot.n_ctx) * 100));
                    
                    const isProcessing = slot.is_processing && n_processed < n_prompt;
                    const isGenerating = slot.is_processing && n_processed >= n_prompt;
                    const isIdle = !slot.is_processing;

                    return (
                      <div key={slot.id} className={`bg-gray-800 border rounded-xl p-5 relative overflow-hidden ${isIdle ? 'border-gray-700 opacity-60' : 'border-gray-600 shadow-lg'}`}>
                        {/* Background progress bar indicator */}
                        <div 
                          className="absolute bottom-0 left-0 h-1 bg-gradient-to-r from-orange-500 to-yellow-400 transition-all duration-1000 ease-in-out" 
                          style={{ width: `${percent}%` }}
                        />

                        <div className="flex justify-between items-start mb-4">
                          <div className="flex items-center space-x-2">
                            <span className="text-xl font-bold text-gray-200">Slot {slot.id}</span>
                            {isProcessing && (
                              <span className="px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-blue-500/20 text-blue-400 rounded-full border border-blue-500/20 flex items-center">
                                <Activity className="w-3 h-3 mr-1 animate-pulse" /> Processing
                              </span>
                            )}
                            {isGenerating && (
                              <span className="px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-green-500/20 text-green-400 rounded-full border border-green-500/20 flex items-center">
                                <Activity className="w-3 h-3 mr-1" /> Generating
                              </span>
                            )}
                            {isIdle && (
                              <span className="px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider bg-gray-700 text-gray-400 rounded-full">
                                Idle
                              </span>
                            )}
                          </div>
                          <div className="text-right">
                            <div className="text-xs text-gray-400">Task ID</div>
                            <div className="text-sm font-mono text-gray-300">#{slot.id_task ?? '-'}</div>
                          </div>
                        </div>

                        <div className="space-y-4">
                          <div>
                            <div className="flex justify-between text-xs mb-1">
                              <span className="text-gray-400 flex items-center"><Hash className="w-3 h-3 mr-1"/> Context Usage</span>
                              <span className="font-mono text-gray-300">{used.toLocaleString()} / {slot.n_ctx.toLocaleString()}</span>
                            </div>
                            <div className="w-full bg-gray-900 rounded-full h-2.5 border border-gray-700 overflow-hidden">
                              <div className={`h-2.5 rounded-full transition-all duration-500 ${percent > 90 ? 'bg-red-500' : percent > 60 ? 'bg-yellow-500' : 'bg-green-500'}`} style={{ width: `${percent}%` }}></div>
                            </div>
                          </div>

                          <div className="grid grid-cols-3 gap-4 pt-2 border-t border-gray-700">
                            <div>
                              <div className="text-[10px] uppercase text-gray-500 font-bold mb-1 tracking-wider">Tokens</div>
                              <div className="text-sm font-mono text-gray-300">
                                <span className="text-blue-400" title="Prompt Tokens">{n_prompt.toLocaleString()}</span>
                                <span className="text-gray-600 mx-1">+</span>
                                <span className="text-green-400" title="Generated Tokens">{decoded.toLocaleString()}</span>
                              </div>
                            </div>
                            <div>
                              <div className="text-[10px] uppercase text-gray-500 font-bold mb-1 tracking-wider">Speed</div>
                              <div className="text-sm font-mono text-gray-300">
                                {isProcessing && rates[slot.id]?.pp > 0 ? (
                                  <span className="text-blue-400" title="Prompt Processing Rate">{rates[slot.id].pp} t/s</span>
                                ) : isGenerating && rates[slot.id]?.ts > 0 ? (
                                  <span className="text-green-400" title="Token Generation Rate">{rates[slot.id].ts} t/s</span>
                                ) : (
                                  <span className="text-gray-600">-</span>
                                )}
                              </div>
                            </div>
                            <div>
                              <div className="text-[10px] uppercase text-gray-500 font-bold mb-1 tracking-wider">Speculative</div>
                              <div className="text-sm font-medium text-gray-300">
                                {slot.speculative ? (
                                  <span className="text-purple-400">Active</span>
                                ) : (
                                  <span className="text-gray-500">Disabled</span>
                                )}
                              </div>
                            </div>
                          </div>

                          {/* Decoding Params Snippet */}
                          {slot.params && (
                            <div className="pt-2 border-t border-gray-700">
                               <div className="text-[10px] uppercase text-gray-500 font-bold mb-1 tracking-wider flex items-center">
                                  <Percent className="w-3 h-3 mr-1" /> Key Parameters
                               </div>
                               <div className="flex flex-wrap gap-2 mt-2">
                                  {slot.params.temperature !== undefined && (
                                    <span className="text-xs bg-gray-900 border border-gray-700 px-2 py-1 rounded text-gray-400 font-mono">Temp: {slot.params.temperature.toFixed(2)}</span>
                                  )}
                                  {slot.params.top_k !== undefined && (
                                    <span className="text-xs bg-gray-900 border border-gray-700 px-2 py-1 rounded text-gray-400 font-mono">TopK: {slot.params.top_k}</span>
                                  )}
                                  {slot.params.min_p !== undefined && (
                                    <span className="text-xs bg-gray-900 border border-gray-700 px-2 py-1 rounded text-gray-400 font-mono">MinP: {slot.params.min_p}</span>
                                  )}
                               </div>
                            </div>
                          )}

                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}

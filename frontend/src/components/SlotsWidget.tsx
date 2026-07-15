import { useState, useEffect } from 'react';
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
  const [globalMetrics, setGlobalMetrics] = useState<any>(null);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [visibleStats, setVisibleStats] = useState(() => {
    try {
      const saved = localStorage.getItem('metrics_visible_stats');
      if (saved) return JSON.parse(saved);
    } catch {}
    return {
      activeSlots: true,
      avgCtx: true,
      globalPp: false,
      globalTg: false
    };
  });

  const toggleStat = (key: string) => {
    setVisibleStats((prev: any) => {
      const next = { ...prev, [key]: !prev[key] };
      localStorage.setItem('metrics_visible_stats', JSON.stringify(next));
      return next;
    });
  };

  useEffect(() => {
    const unsubscribe = sseService.subscribe((payload: any) => {
      if (payload.type === 'SLOTS') {
        try {
          const data = typeof payload.data === 'string' ? JSON.parse(payload.data) : payload.data;
          if (Array.isArray(data)) {
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
        className={`flex items-center space-x-2 text-xs font-mono transition-colors text-gray-400 hover:text-gray-200 cursor-pointer`}
        onClick={() => setIsModalOpen(true)}
        title="Click to open Metrics"
      >
        <Layers className={`w-3.5 h-3.5 text-orange-400`} />
        
        <span className="font-bold text-gray-300">Metrics</span>
        
        {visibleStats.activeSlots && (
          <span className={activeSlots.length > 0 ? 'text-orange-400 font-bold' : 'text-gray-500'}>
            {activeSlots.length}/{slots.length || '-'}
          </span>
        )}
        
        {activeSlots.length > 0 && (
          <>
            {visibleStats.avgCtx && (
              <>
                <div className="w-px h-3 bg-gray-700 mx-1"></div>
                <span className="text-gray-400">CTX</span>
                <span className={`${avgCtxPercent > 80 ? 'text-red-400' : avgCtxPercent > 50 ? 'text-yellow-400' : 'text-green-400'}`}>
                  {avgCtxPercent}%
                </span>
              </>
            )}
            
            {activeSlots.some(s => {
              const decoded = s.next_token && s.next_token.length > 0 ? s.next_token[0].n_decoded : 0;
              return s.is_processing && decoded === 0;
            }) && (
              <span title="Processing Prompt"><Activity className="w-3.5 h-3.5 text-blue-400 animate-pulse ml-1" /></span>
            )}
          </>
        )}

        {globalMetrics && !globalMetrics.error && (
          <>
            {visibleStats.globalPp && (
              <>
                <div className="w-px h-3 bg-gray-700 mx-1"></div>
                <span className="text-gray-400" title="Global Prompt Processing">PP</span>
                <span className="text-blue-400 font-mono">{globalMetrics.prompt_tokens_seconds?.toFixed(1) || 0}</span>
              </>
            )}
            {visibleStats.globalTg && (
              <>
                <div className="w-px h-3 bg-gray-700 mx-1"></div>
                <span className="text-gray-400" title="Global Token Generation">TG</span>
                <span className="text-green-400 font-mono">{globalMetrics.predicted_tokens_seconds?.toFixed(1) || 0}</span>
              </>
            )}
          </>
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
                       <div className="text-[10px] text-gray-500 uppercase tracking-widest flex items-center justify-between group">
                         <span>Global PP</span>
                         <button onClick={(e) => { e.stopPropagation(); toggleStat('globalPp'); }} className={`p-0.5 rounded transition-colors ${visibleStats.globalPp ? 'text-orange-400 bg-orange-400/10' : 'text-gray-600 hover:text-gray-400'}`} title="Toggle in status bar">
                           {visibleStats.globalPp ? <Eye className="w-3 h-3" /> : <EyeOff className="w-3 h-3" />}
                         </button>
                       </div>
                       <div className="text-lg font-mono text-blue-400">{globalMetrics.prompt_tokens_seconds?.toFixed(1) || 0} <span className="text-xs text-gray-500">t/s</span></div>
                     </div>
                     <div>
                       <div className="text-[10px] text-gray-500 uppercase tracking-widest flex items-center justify-between group">
                         <span>Global TG</span>
                         <button onClick={(e) => { e.stopPropagation(); toggleStat('globalTg'); }} className={`p-0.5 rounded transition-colors ${visibleStats.globalTg ? 'text-orange-400 bg-orange-400/10' : 'text-gray-600 hover:text-gray-400'}`} title="Toggle in status bar">
                           {visibleStats.globalTg ? <Eye className="w-3 h-3" /> : <EyeOff className="w-3 h-3" />}
                         </button>
                       </div>
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

              <div className="flex items-center justify-between px-2 pb-2 mt-4">
                <div className="text-sm font-bold text-gray-300">Individual Slots</div>
                <div className="flex space-x-4">
                  <div className="text-[10px] text-gray-500 uppercase tracking-widest flex items-center space-x-2">
                    <span>Active Slots Status</span>
                    <button onClick={(e) => { e.stopPropagation(); toggleStat('activeSlots'); }} className={`p-0.5 rounded transition-colors ${visibleStats.activeSlots ? 'text-orange-400 bg-orange-400/10' : 'text-gray-600 hover:text-gray-400'}`} title="Toggle in status bar">
                      {visibleStats.activeSlots ? <Eye className="w-3 h-3" /> : <EyeOff className="w-3 h-3" />}
                    </button>
                  </div>
                  <div className="text-[10px] text-gray-500 uppercase tracking-widest flex items-center space-x-2">
                    <span>Average Context</span>
                    <button onClick={(e) => { e.stopPropagation(); toggleStat('avgCtx'); }} className={`p-0.5 rounded transition-colors ${visibleStats.avgCtx ? 'text-orange-400 bg-orange-400/10' : 'text-gray-600 hover:text-gray-400'}`} title="Toggle in status bar">
                      {visibleStats.avgCtx ? <Eye className="w-3 h-3" /> : <EyeOff className="w-3 h-3" />}
                    </button>
                  </div>
                </div>
              </div>

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
                    const used = n_prompt + decoded;
                    const percent = Math.min(100, Math.round((used / slot.n_ctx) * 100));
                    
                    const isGenerating = slot.is_processing && decoded > 0;
                    const isProcessing = slot.is_processing && decoded === 0;
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

                          <div className="grid grid-cols-2 gap-4 pt-2 border-t border-gray-700">
                            <div>
                              <div className="text-[10px] uppercase text-gray-500 font-bold mb-1 tracking-wider">Tokens</div>
                              <div className="text-sm font-mono text-gray-300">
                                <span className="text-blue-400" title="Prompt Tokens">{n_prompt.toLocaleString()}</span>
                                <span className="text-gray-600 mx-1">+</span>
                                <span className="text-green-400" title="Generated Tokens">{decoded.toLocaleString()}</span>
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

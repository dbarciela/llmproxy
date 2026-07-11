import { useState, useEffect } from 'react';
import { Cpu, MemoryStick, MonitorDot } from 'lucide-react';

interface HardwareStats {
  cpuLoad: number;
  cpuTemp?: number;
  ramUsedGb: string;
  ramTotalGb: string;
  ramPercent: number;
  gpuName?: string;
  gpuTemp?: number;
  vramUsedGb?: string;
  vramTotalGb?: string;
  vramPercent?: number;
}

export function HardwareWidget() {
  const [stats, setStats] = useState<HardwareStats | null>(null);

  useEffect(() => {
    const es = new EventSource('/api/proxy/live');
    
    es.addEventListener('live-chat', (e: any) => {
      try {
        const payload = JSON.parse(e.data);
        if (payload.type === 'HARDWARE') {
          setStats(payload.data);
        }
      } catch { /* ignore */ }
    });

    return () => es.close();
  }, []);

  if (!stats) return null;

  return (
    <div className="flex items-center space-x-4 bg-gray-900 px-4 py-2 rounded-xl border border-gray-800 text-xs text-gray-300">
      {/* CPU */}
      <div className="flex flex-col items-center justify-center min-w-[60px]" title="CPU Load">
        <div className="flex items-center text-[10px] text-gray-500 mb-0.5 space-x-1">
          <span className="font-bold uppercase tracking-wider">CPU</span>
          {stats.cpuTemp !== undefined && <span>{stats.cpuTemp}°C</span>}
        </div>
        <div className="flex items-center space-x-1.5 text-blue-400 font-mono">
          <Cpu className="w-3.5 h-3.5" />
          <span>{stats.cpuLoad}%</span>
        </div>
      </div>

      <div className="w-px h-6 bg-gray-700"></div>

      {/* RAM */}
      <div className="flex flex-col items-center justify-center min-w-[60px]" title={`RAM: ${stats.ramUsedGb}GB / ${stats.ramTotalGb}GB`}>
        <div className="flex items-center text-[10px] text-gray-500 mb-0.5 space-x-1">
          <span className="font-bold uppercase tracking-wider">RAM</span>
        </div>
        <div className="flex items-center space-x-1.5 text-green-400 font-mono">
          <MemoryStick className="w-3.5 h-3.5" />
          <span>{stats.ramPercent}%</span>
        </div>
      </div>

      {stats.vramPercent !== undefined && (
        <>
          <div className="w-px h-6 bg-gray-700"></div>
          {/* GPU / VRAM */}
          <div className="flex flex-col items-center justify-center min-w-[60px]" title={`VRAM: ${stats.vramUsedGb}GB / ${stats.vramTotalGb}GB`}>
            <div className="flex items-center text-[10px] text-gray-500 mb-0.5 space-x-1">
              <span className="font-bold uppercase tracking-wider truncate max-w-[80px]" title={stats.gpuName}>{stats.gpuName || 'GPU'}</span>
              {stats.gpuTemp !== undefined && <span>{stats.gpuTemp}°C</span>}
            </div>
            <div className={`flex items-center space-x-1.5 font-mono ${stats.vramPercent > 85 ? 'text-red-400' : 'text-purple-400'}`}>
              <MonitorDot className="w-3.5 h-3.5" />
              <span>{stats.vramPercent}%</span>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

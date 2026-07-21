import { useState, useEffect, useRef } from 'react';
import { RefreshCw, ChevronUp, Check, Settings, Terminal } from 'lucide-react';

export interface RestartCommand {
  id: string;
  name: string;
  command: string;
}

interface RestartButtonProps {
  onRestart: (command?: string, commandName?: string) => void;
  onOpenSettings?: () => void;
}

export function RestartButton({ onRestart, onOpenSettings }: RestartButtonProps) {
  const [commands, setCommands] = useState<RestartCommand[]>([]);
  const [selectedId, setSelectedId] = useState<string>(() => {
    return localStorage.getItem('aura_last_restart_command_id') || '';
  });
  const [isOpen, setIsOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const fetchCommands = async () => {
    try {
      const res = await fetch('/api/proxy/restart-commands');
      if (res.ok) {
        const data: RestartCommand[] = await res.json();
        setCommands(data);
        if (data.length > 0) {
          const savedId = localStorage.getItem('aura_last_restart_command_id');
          if (!savedId || !data.some(c => c.id === savedId)) {
            setSelectedId(data[0].id);
            localStorage.setItem('aura_last_restart_command_id', data[0].id);
          }
        }
      }
    } catch (err) {
      console.error("Failed to load restart commands:", err);
    }
  };

  useEffect(() => {
    fetchCommands();
  }, []);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  const activeCommand = commands.find(c => c.id === selectedId) || commands[0];

  const handleExecute = (cmd?: RestartCommand) => {
    const target = cmd || activeCommand;
    if (target) {
      setSelectedId(target.id);
      localStorage.setItem('aura_last_restart_command_id', target.id);
      onRestart(target.command, target.name);
    } else {
      onRestart();
    }
    setIsOpen(false);
  };

  return (
    <div className="relative flex items-center" ref={menuRef}>
      {/* Context Menu Dropdown */}
      {isOpen && (
        <div className="absolute bottom-8 right-0 w-72 bg-gray-900 border border-gray-700 rounded-xl shadow-2xl overflow-hidden z-50 animate-in fade-in slide-in-from-bottom-2 duration-150">
          <div className="p-2.5 border-b border-gray-800 bg-gray-900/80 flex items-center justify-between">
            <span className="text-xs font-bold text-gray-300 uppercase tracking-wider flex items-center">
              <Terminal className="w-3.5 h-3.5 mr-1.5 text-orange-400" />
              Restart Commands
            </span>
            {onOpenSettings && (
              <button
                onClick={() => { setIsOpen(false); onOpenSettings(); }}
                className="text-gray-400 hover:text-gray-200 transition-colors p-1 rounded hover:bg-gray-800"
                title="Configure restart commands"
              >
                <Settings className="w-3.5 h-3.5" />
              </button>
            )}
          </div>

          <div className="p-1 max-h-56 overflow-y-auto space-y-0.5">
            {commands.length === 0 ? (
              <div className="p-3 text-xs text-gray-500 text-center">
                No custom restart commands configured. Using default restart script.
              </div>
            ) : (
              commands.map(cmd => {
                const isSelected = activeCommand?.id === cmd.id;
                return (
                  <button
                    key={cmd.id}
                    onClick={() => handleExecute(cmd)}
                    className={`w-full text-left px-3 py-2 rounded-lg text-xs transition-colors flex items-center justify-between group ${
                      isSelected ? 'bg-orange-500/10 text-orange-300 border border-orange-500/20' : 'text-gray-300 hover:bg-gray-800'
                    }`}
                  >
                    <div className="flex-1 min-w-0 pr-2">
                      <div className="font-semibold truncate text-gray-200 group-hover:text-white flex items-center">
                        {cmd.name}
                      </div>
                      <div className="text-[10px] text-gray-500 font-mono truncate mt-0.5" title={cmd.command}>
                        {cmd.command}
                      </div>
                    </div>
                    {isSelected && (
                      <Check className="w-4 h-4 text-orange-400 flex-shrink-0 ml-2" />
                    )}
                  </button>
                );
              })
            )}
          </div>

          {onOpenSettings && (
            <div className="p-1.5 border-t border-gray-800 bg-gray-950/60 text-center">
              <button
                onClick={() => { setIsOpen(false); onOpenSettings(); }}
                className="text-[11px] text-indigo-400 hover:text-indigo-300 font-medium transition-colors w-full py-1 text-center"
              >
                + Manage Commands...
              </button>
            </div>
          )}
        </div>
      )}

      {/* Split Button */}
      <div className="inline-flex items-center rounded-md bg-gray-800/80 border border-gray-700 hover:border-gray-600 transition-colors p-0.5">
        <button
          onClick={() => handleExecute()}
          title={`Restart Server (${activeCommand?.name || 'Default'})`}
          className="p-1 text-gray-400 hover:text-white transition-colors flex items-center space-x-1"
        >
          <RefreshCw className="w-3 h-3 text-orange-400" />
        </button>
        <div className="w-px h-3 bg-gray-700 mx-0.5" />
        <button
          onClick={() => setIsOpen(!isOpen)}
          title="Select Restart Command"
          className={`p-1 text-gray-400 hover:text-white transition-colors rounded ${isOpen ? 'bg-gray-700 text-white' : ''}`}
        >
          <ChevronUp className="w-3 h-3" />
        </button>
      </div>
    </div>
  );
}

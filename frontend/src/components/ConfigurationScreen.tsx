import { useState, useEffect } from 'react';
import { DragDropContext, Droppable, Draggable, type DropResult } from '@hello-pangea/dnd';
import { Settings, GripVertical, Shield, Activity, Layers, Type, Eye, EyeOff, AlertTriangle, RefreshCw, Zap, Terminal, Plus, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

interface PluginMetadata {
  id: string;
  name: string;
  uiTabName: string;
  hasUiToggle: boolean;
  description: string;
  isBuffering?: boolean;
  isAsync?: boolean;
}

interface RestartCommand {
  id: string;
  name: string;
  command: string;
}

export function ConfigurationScreen({ globalPlugins, updatePluginOrder, hiddenTabs, toggleTabHidden, defaultTab, updateDefaultTab }: { 
  globalPlugins: PluginMetadata[];
  updatePluginOrder: (newOrder: string[]) => void;
  hiddenTabs: string[];
  toggleTabHidden: (id: string) => void;
  defaultTab: string;
  updateDefaultTab: (tabId: string) => void;
}) {
  const [plugins, setPlugins] = useState<PluginMetadata[]>(globalPlugins);
  const [restartCommands, setRestartCommands] = useState<RestartCommand[]>([]);
  const [newName, setNewName] = useState('');
  const [newCommand, setNewCommand] = useState('');
  const [isSavingCmds, setIsSavingCmds] = useState(false);

  useEffect(() => {
    setPlugins(globalPlugins);
  }, [globalPlugins]);

  useEffect(() => {
    fetch('/api/proxy/restart-commands')
      .then(res => res.json())
      .then(data => {
        if (Array.isArray(data)) setRestartCommands(data);
      })
      .catch(err => console.error("Failed to load restart commands:", err));
  }, []);

  const saveCommands = async (cmdsToSave: RestartCommand[]) => {
    setIsSavingCmds(true);
    try {
      const res = await fetch('/api/proxy/restart-commands', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(cmdsToSave)
      });
      if (res.ok) {
        toast.success('Restart commands updated');
      } else {
        toast.error('Failed to update restart commands');
      }
    } catch (err) {
      console.error(err);
      toast.error('Failed to update restart commands');
    } finally {
      setIsSavingCmds(false);
    }
  };

  const handleAddCommand = () => {
    if (!newCommand.trim()) return;
    const name = newName.trim() || newCommand.trim().split(/[/\\]/).pop() || 'Command';
    const newCmdObj: RestartCommand = {
      id: 'cmd-' + Math.random().toString(36).substring(2, 9),
      name,
      command: newCommand.trim()
    };
    const updated = [...restartCommands, newCmdObj];
    setRestartCommands(updated);
    setNewName('');
    setNewCommand('');
    saveCommands(updated);
  };

  const handleRemoveCommand = (id: string) => {
    const updated = restartCommands.filter(c => c.id !== id);
    setRestartCommands(updated);
    saveCommands(updated);
  };

  const handleDragEnd = (result: DropResult) => {
    if (!result.destination) return;

    const items = Array.from(plugins);
    const [reorderedItem] = items.splice(result.source.index, 1);
    items.splice(result.destination.index, 0, reorderedItem);

    setPlugins(items);
    updatePluginOrder(items.map(p => p.id));
  };

  const getIcon = (id: string) => {
    switch (id) {
      case 'context-deduplicator': return <Shield className="w-5 h-5 text-blue-400" />;
      case 'prompt-transformer': return <Type className="w-5 h-5 text-purple-400" />;
      case 'manual-editor': return <Layers className="w-5 h-5 text-orange-400" />;
      case 'archive': return <Activity className="w-5 h-5 text-green-400" />;
      default: return <Settings className="w-5 h-5 text-gray-400" />;
    }
  };

  return (
    <div className="flex-1 flex flex-col bg-gray-900 p-6 overflow-hidden">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h2 className="text-2xl font-bold text-gray-100 flex items-center">
            <Settings className="w-6 h-6 mr-3 text-blue-400" />
            Global Pipeline Configuration
          </h2>
          <p className="text-gray-400 mt-1">
            Reorder how requests flow through the Aura plugins and configure system options.
          </p>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto max-w-4xl mx-auto w-full space-y-8">
        
        {/* General Settings */}
        <div className="bg-gray-800 p-5 rounded-xl border border-gray-700">
          <h3 className="text-lg font-medium text-gray-200 mb-4 flex items-center">
            <Settings className="w-5 h-5 mr-2 text-indigo-400" />
            General Settings
          </h3>
          <div className="flex items-center justify-between p-4 bg-gray-900/50 rounded-lg border border-gray-700">
            <div>
              <p className="text-gray-200 font-medium">Default Tab</p>
              <p className="text-sm text-gray-500">Choose which tab opens automatically when Aura starts.</p>
            </div>
            <select
              value={defaultTab}
              onChange={(e) => updateDefaultTab(e.target.value)}
              className="bg-gray-800 text-gray-200 border border-gray-600 rounded-lg px-3 py-2 outline-none focus:border-blue-500 transition-colors"
            >
              {globalPlugins.map(p => (
                <option key={p.id} value={p.id}>{p.uiTabName}</option>
              ))}
            </select>
          </div>
        </div>

        {/* Target Server Restart Commands Settings */}
        <div className="bg-gray-800 p-5 rounded-xl border border-gray-700">
          <h3 className="text-lg font-medium text-gray-200 mb-2 flex items-center">
            <Terminal className="w-5 h-5 mr-2 text-orange-400" />
            Target Server Restart Commands
          </h3>
          <p className="text-sm text-gray-400 mb-4">
            Configure target server restart commands. The status bar restart button executes the last selected command.
          </p>

          <div className="space-y-2 mb-4">
            {restartCommands.length === 0 ? (
              <div className="p-4 bg-gray-900/50 rounded-lg border border-gray-700 text-sm text-gray-500 text-center">
                No custom commands configured yet. Add your scripts below.
              </div>
            ) : (
              restartCommands.map((cmd, idx) => (
                <div key={cmd.id || idx} className="flex items-center justify-between p-3 bg-gray-900/60 rounded-lg border border-gray-700">
                  <div className="flex-1 min-w-0 pr-4">
                    <div className="text-sm font-semibold text-gray-200">{cmd.name}</div>
                    <div className="text-xs font-mono text-gray-400 truncate" title={cmd.command}>{cmd.command}</div>
                  </div>
                  <button
                    onClick={() => handleRemoveCommand(cmd.id)}
                    className="p-1.5 text-red-400 hover:text-red-300 hover:bg-red-500/10 rounded transition-colors"
                    title="Remove command"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))
            )}
          </div>

          <div className="p-4 bg-gray-900/40 rounded-lg border border-gray-750 space-y-3">
            <div className="text-xs font-semibold uppercase tracking-wider text-gray-400">Add New Restart Command</div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <input
                type="text"
                placeholder="Name (e.g. Run 27B)"
                value={newName}
                onChange={e => setNewName(e.target.value)}
                className="bg-gray-800 text-gray-200 border border-gray-700 rounded-lg px-3 py-2 text-sm outline-none focus:border-orange-500"
              />
              <input
                type="text"
                placeholder="Command / Script Path (e.g. C:\ai\run 27b.bat)"
                value={newCommand}
                onChange={e => setNewCommand(e.target.value)}
                className="bg-gray-800 text-gray-200 border border-gray-700 rounded-lg px-3 py-2 text-sm font-mono outline-none focus:border-orange-500"
              />
            </div>
            <button
              onClick={handleAddCommand}
              disabled={!newCommand.trim() || isSavingCmds}
              className="px-4 py-2 bg-orange-600 hover:bg-orange-500 disabled:opacity-50 text-white rounded-lg text-xs font-semibold transition-colors flex items-center"
            >
              <Plus className="w-4 h-4 mr-1.5" /> Add Command
            </button>
          </div>
        </div>

        {/* Pipeline Ordering */}
        <div className="bg-gray-800 p-5 rounded-xl border border-gray-700">
          <h3 className="text-lg font-medium text-gray-200 mb-4 flex items-center">
            <Layers className="w-5 h-5 mr-2 text-indigo-400" />
            Execution Order
          </h3>
          <p className="text-sm text-gray-400 mb-6">
            Drag and drop plugins to change their execution order. Plugins at the top run first for requests.
          </p>
          
          <DragDropContext onDragEnd={handleDragEnd}>
            <Droppable droppableId="pipeline-order">
              {(provided) => (
                <div 
                  {...provided.droppableProps}
                  ref={provided.innerRef}
                  className="space-y-3"
                >
                  {plugins.map((plugin, index) => (
                    <Draggable key={plugin.id} draggableId={plugin.id} index={index}>
                      {(provided, snapshot) => (
                        <div
                          ref={provided.innerRef}
                          {...provided.draggableProps}
                          className={`flex items-center p-4 rounded-lg border transition-colors ${
                            snapshot.isDragging 
                              ? 'bg-gray-750 border-blue-500/50 shadow-xl shadow-blue-900/20' 
                              : 'bg-gray-900/50 border-gray-700 hover:bg-gray-800'
                          }`}
                        >
                          <div 
                            {...provided.dragHandleProps}
                            className="mr-4 text-gray-500 hover:text-gray-300 cursor-grab active:cursor-grabbing p-1"
                          >
                            <GripVertical className="w-5 h-5" />
                          </div>
                          
                          <div className="flex items-center justify-center w-8 h-8 rounded-full bg-gray-800 border border-gray-700 mr-4 text-sm font-medium">
                            {index + 1}
                          </div>

                          <div className="mr-4">
                            {getIcon(plugin.id)}
                          </div>

                          <div className="flex-1">
                            <h4 className="font-medium text-gray-200 flex items-center">
                              {plugin.name}
                              {plugin.isAsync ? (
                                <span className="ml-2 px-2 py-0.5 rounded-full bg-purple-500/20 text-purple-400 text-[10px] font-bold tracking-wider flex items-center uppercase" title="Async Plugin: Runs out of band without blocking the proxy.">
                                  <RefreshCw className="w-3 h-3 mr-1" /> Async
                                </span>
                              ) : plugin.isBuffering ? (
                                <span className="ml-2 px-2 py-0.5 rounded-full bg-orange-500/20 text-orange-400 text-[10px] font-bold tracking-wider flex items-center uppercase" title="Buffering Plugin: Degrades performance for contexts > 50MB">
                                  <AlertTriangle className="w-3 h-3 mr-1" /> Buffering
                                </span>
                              ) : (
                                <span className="ml-2 px-2 py-0.5 rounded-full bg-green-500/20 text-green-400 text-[10px] font-bold tracking-wider flex items-center uppercase" title="Streaming Plugin: Zero memory overhead processing.">
                                  <Zap className="w-3 h-3 mr-1" /> Streaming
                                </span>
                              )}
                            </h4>
                            <p className="text-xs text-gray-400 mt-0.5">{plugin.description}</p>
                          </div>
                          
                          <button
                            onClick={() => toggleTabHidden(plugin.id)}
                            className={`p-2 rounded-lg transition-colors mr-2 ${hiddenTabs.includes(plugin.id) ? 'bg-gray-800 text-gray-500 hover:bg-gray-700 hover:text-gray-300' : 'bg-blue-500/20 text-blue-400 hover:bg-blue-500/30'}`}
                            title={hiddenTabs.includes(plugin.id) ? "Show tab in menu" : "Hide tab from menu"}
                          >
                            {hiddenTabs.includes(plugin.id) ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                          </button>
                          
                          {plugin.hasUiToggle && (
                            <div className="ml-2 flex items-center space-x-2">
                              <span className="text-[10px] uppercase tracking-wider text-gray-500 font-bold">Has Toggle</span>
                              <div className="w-2.5 h-2.5 rounded-full bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.6)]"></div>
                            </div>
                          )}
                        </div>
                      )}
                    </Draggable>
                  ))}
                  {provided.placeholder}
                </div>
              )}
            </Droppable>
          </DragDropContext>
        </div>

      </div>
    </div>
  );
}

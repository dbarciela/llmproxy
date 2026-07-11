import { useState, useEffect } from 'react';
import { DragDropContext, Droppable, Draggable, type DropResult } from '@hello-pangea/dnd';
import { Settings, GripVertical, Shield, Activity, Layers, Type, Eye, EyeOff, AlertTriangle, RefreshCw, Zap } from 'lucide-react';

interface PluginMetadata {
  id: string;
  name: string;
  uiTabName: string;
  hasUiToggle: boolean;
  description: string;
  isBuffering?: boolean;
  isAsync?: boolean;
}

export function ConfigurationScreen({ globalPlugins, updatePluginOrder, hiddenTabs, toggleTabHidden }: { 
  globalPlugins: PluginMetadata[];
  updatePluginOrder: (newOrder: string[]) => void;
  hiddenTabs: string[];
  toggleTabHidden: (id: string) => void;
}) {
  const [plugins, setPlugins] = useState<PluginMetadata[]>(globalPlugins);

  useEffect(() => {
    setPlugins(globalPlugins);
  }, [globalPlugins]);

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
            Reorder how requests flow through the LlamaProxy plugins.
          </p>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto max-w-4xl mx-auto w-full space-y-8">
        
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

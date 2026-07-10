import { useState, useEffect } from 'react';
import { DragDropContext, Droppable, Draggable } from '@hello-pangea/dnd';
import { GripVertical, Clock, List } from 'lucide-react';
import { RegexRuleList } from './RegexRuleList';

interface RequestContext {
  id: string;
  method: string;
  uri: string;
  phase: string;
}

interface QueuePanelProps {
  selectedRequestId: string | null;
  onSelectRequest: (id: string) => void;
  isInterceptRequests: boolean;
  isInterceptResponses: boolean;
  interceptInvalidJson: boolean;
  interceptRegexRules: string[];
  onUpdateSettings: (interceptRequests: boolean, interceptResponses: boolean, logging: boolean, invalidJson: boolean, intRules: string[]) => void;
}

export function QueuePanel({ 
  selectedRequestId, onSelectRequest, 
  isInterceptRequests, isInterceptResponses, 
  interceptInvalidJson, interceptRegexRules,
  onUpdateSettings 
}: QueuePanelProps) {
  const [queue, setQueue] = useState<RequestContext[]>([]);

  const fetchQueue = () => {
    fetch('/api/proxy/queue')
      .then(res => res.json())
      .then(data => setQueue(data))
      .catch(err => console.error("Error fetching queue", err));
  };

  useEffect(() => {
    fetchQueue();
    const interval = setInterval(fetchQueue, 2000); // Poll for updates
    return () => clearInterval(interval);
  }, []);

  const onDragEnd = (result: any) => {
    if (!result.destination) return;

    const items = Array.from(queue);
    const [reorderedItem] = items.splice(result.source.index, 1);
    items.splice(result.destination.index, 0, reorderedItem);

    setQueue(items);

    const newOrder = items.map(item => item.id);
    fetch('/api/proxy/reorder', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(newOrder)
    }).catch(err => console.error("Error reordering", err));
  };

  return (
    <div className="flex flex-col h-full bg-gray-900 border-r border-gray-800">
      <div className="p-4 border-b border-gray-800 bg-gray-900/80 sticky top-0 z-10 flex flex-col space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold tracking-wide text-gray-300 uppercase flex items-center">
            <List className="w-4 h-4 mr-2" /> Pending Requests
          </h2>
          <span className="bg-blue-900/50 text-blue-400 text-xs py-0.5 px-2 rounded-full border border-blue-800/50">
            {queue.length}
          </span>
        </div>
        
        <div className="flex flex-col space-y-2">
          <label className="flex items-center justify-between cursor-pointer">
            <span className="text-xs font-medium text-gray-400">Intercept Requests</span>
            <button 
              onClick={() => onUpdateSettings(!isInterceptRequests, isInterceptResponses, false, interceptInvalidJson, interceptRegexRules)}
              className={`w-10 h-5 rounded-full transition-colors flex items-center px-1 ${isInterceptRequests ? 'bg-blue-600' : 'bg-gray-700'}`}
            >
              <div className={`w-3.5 h-3.5 bg-white rounded-full shadow-md transform transition-transform ${isInterceptRequests ? 'translate-x-5' : 'translate-x-0'}`}></div>
            </button>
          </label>
          <label className="flex items-center justify-between cursor-pointer">
            <span className="text-xs font-medium text-gray-400">Intercept Responses</span>
            <button 
              onClick={() => onUpdateSettings(isInterceptRequests, !isInterceptResponses, false, interceptInvalidJson, interceptRegexRules)}
              className={`w-10 h-5 rounded-full transition-colors flex items-center px-1 ${isInterceptResponses ? 'bg-purple-600' : 'bg-gray-700'}`}
            >
              <div className={`w-3.5 h-3.5 bg-white rounded-full shadow-md transform transition-transform ${isInterceptResponses ? 'translate-x-5' : 'translate-x-0'}`}></div>
            </button>
          </label>
          <label className="flex items-center justify-between cursor-pointer">
            <span className="text-xs font-medium text-gray-400">Intercept Invalid JSON</span>
            <button 
              onClick={() => onUpdateSettings(isInterceptRequests, isInterceptResponses, false, !interceptInvalidJson, interceptRegexRules)}
              className={`w-10 h-5 rounded-full transition-colors flex items-center px-1 ${interceptInvalidJson ? 'bg-red-600' : 'bg-gray-700'}`}
            >
              <div className={`w-3.5 h-3.5 bg-white rounded-full shadow-md transform transition-transform ${interceptInvalidJson ? 'translate-x-5' : 'translate-x-0'}`}></div>
            </button>
          </label>
          
          <div className="pt-2 border-t border-gray-800 space-y-3">
            <RegexRuleList 
              title="Regex Matcher (Optional)" 
              rules={interceptRegexRules} 
              isObjectRule={false} 
              onChange={(newRules) => onUpdateSettings(isInterceptRequests, isInterceptResponses, false, interceptInvalidJson, newRules)} 
            />
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-2">
        {queue.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-40 text-gray-500">
            <Clock className="w-8 h-8 mb-2 opacity-50" />
            <p className="text-sm">No pending requests</p>
          </div>
        ) : (
          <DragDropContext onDragEnd={onDragEnd}>
            <Droppable droppableId="queue">
              {(provided) => (
                <div {...provided.droppableProps} ref={provided.innerRef} className="space-y-2">
                  {queue.map((req, index) => (
                    <Draggable key={req.id} draggableId={req.id} index={index}>
                      {(provided, snapshot) => (
                        <div
                          ref={provided.innerRef}
                          {...provided.draggableProps}
                          onClick={() => onSelectRequest(req.id)}
                          className={`
                            flex items-center p-3 rounded-lg border text-sm cursor-pointer transition-all
                            ${snapshot.isDragging ? 'shadow-lg shadow-black/50 border-blue-500 bg-gray-800' : ''}
                            ${selectedRequestId === req.id 
                                ? 'bg-blue-900/20 border-blue-500/50 text-blue-100' 
                                : 'bg-gray-800 border-gray-700 hover:border-gray-600 hover:bg-gray-750 text-gray-300'
                            }
                          `}
                        >
                          <div {...provided.dragHandleProps} className="mr-3 text-gray-500 hover:text-gray-300">
                            <GripVertical className="w-4 h-4" />
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center space-x-2 mb-1">
                              <span className={`font-mono text-xs font-bold px-1.5 py-0.5 rounded ${
                                req.method === 'POST' ? 'bg-green-900/50 text-green-400' : 
                                req.method === 'GET' ? 'bg-blue-900/50 text-blue-400' : 'bg-gray-700 text-gray-300'
                              }`}>
                                {req.method}
                              </span>
                              <span className={`font-mono text-[10px] font-bold px-1 py-0.5 rounded ${
                                req.phase === 'REQ' ? 'bg-blue-600/30 text-blue-300 border border-blue-600/50' : 'bg-purple-600/30 text-purple-300 border border-purple-600/50'
                              }`}>
                                {req.phase}
                              </span>
                            </div>
                            <div className="text-xs truncate text-gray-400" title={req.uri}>
                              {req.uri}
                            </div>
                          </div>
                        </div>
                      )}
                    </Draggable>
                  ))}
                  {provided.placeholder}
                </div>
              )}
            </Droppable>
          </DragDropContext>
        )}
      </div>
    </div>
  );
}

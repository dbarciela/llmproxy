import { X, CheckCircle2, Circle, Loader2, AlertCircle, Minus } from 'lucide-react';
import type { BackgroundTask } from '../hooks/useBackgroundTasks';

interface ProgressModalProps {
  task: BackgroundTask | null;
  onClose: (id: string) => void;
  onMinimize: (id: string) => void;
}

export function ProgressModal({ task, onClose, onMinimize }: ProgressModalProps) {
  if (!task || task.isMinimized) return null;

  return (
    <div className="fixed inset-0 bg-black/80 z-50 flex items-center justify-center p-4 backdrop-blur-sm">
      <div className="bg-gray-900 border border-gray-800 rounded-2xl shadow-2xl w-full max-w-lg flex flex-col overflow-hidden">
        
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-gray-800 bg-gray-900/50">
          <h2 className="font-semibold text-gray-200">{task.title}</h2>
          <div className="flex items-center space-x-2">
            {!task.isDone && (
              <button 
                onClick={() => onMinimize(task.id)}
                className="text-gray-500 hover:text-gray-300 transition-colors p-1"
                title="Minimize to background"
              >
                <Minus className="w-5 h-5" />
              </button>
            )}
            {task.isDone && (
              <button 
                onClick={() => onClose(task.id)}
                className="text-gray-500 hover:text-gray-300 transition-colors p-1"
              >
                <X className="w-5 h-5" />
              </button>
            )}
          </div>
        </div>
        
        {/* Body */}
        <div className="p-6 flex flex-col space-y-4 max-h-[60vh] overflow-y-auto">
          {task.errorMsg && (
            <div className="p-3 bg-red-900/30 border border-red-800/50 rounded-lg flex items-start space-x-3 text-red-200 text-sm">
              <AlertCircle className="w-5 h-5 text-red-400 shrink-0" />
              <span>{task.errorMsg}</span>
            </div>
          )}

          <div className="space-y-4">
            {task.steps.map((step) => (
              <div key={step.id} className="flex items-start space-x-3">
                <div className="mt-0.5 shrink-0">
                  {step.status === 'done' && <CheckCircle2 className="w-5 h-5 text-green-500" />}
                  {step.status === 'running' && <Loader2 className="w-5 h-5 text-purple-500 animate-spin" />}
                  {step.status === 'pending' && <Circle className="w-5 h-5 text-gray-700" />}
                  {step.status === 'error' && <AlertCircle className="w-5 h-5 text-red-500" />}
                </div>
                <div className="flex flex-col">
                  <span className={`text-sm font-medium capitalize ${step.status === 'done' ? 'text-gray-400' : step.status === 'running' ? 'text-gray-200' : 'text-gray-500'}`}>
                    {step.label.toLowerCase()}
                  </span>
                  {step.message && (
                    <span className="text-xs text-gray-500 mt-1">{step.message}</span>
                  )}
                </div>
              </div>
            ))}
            
            {task.steps.length === 0 && !task.errorMsg && !task.isDone && (
              <div className="flex items-center space-x-3 text-gray-500 text-sm py-2">
                <Loader2 className="w-5 h-5 animate-spin" />
                <span>Initializing...</span>
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-gray-800 bg-gray-900/50 flex justify-end space-x-3">
          {!task.isDone && (
            <button
              onClick={() => onMinimize(task.id)}
              className="px-4 py-2 rounded-lg text-sm font-medium transition-colors bg-gray-800 hover:bg-gray-700 text-gray-300"
            >
              Run in Background
            </button>
          )}
          <button
            onClick={() => onClose(task.id)}
            disabled={!task.isDone}
            className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              task.isDone 
                ? 'bg-purple-600 hover:bg-purple-500 text-white shadow-lg shadow-purple-500/20' 
                : 'bg-gray-800 text-gray-500 cursor-not-allowed hidden'
            }`}
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

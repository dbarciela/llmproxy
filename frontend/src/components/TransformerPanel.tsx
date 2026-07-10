import { Scissors } from 'lucide-react';

interface TransformerPanelProps {
  settings: any;
  updateSettings: (newSettings: any) => void;
}

export function TransformerPanel({ settings }: TransformerPanelProps) {
  const promptRules = settings?.promptReplaceRules || [];
  const responseRules = settings?.responseReplaceRules || [];

  return (
    <div className="flex-1 flex flex-col bg-gray-900 p-6 overflow-hidden">
      <div className="flex items-center justify-between mb-8">
        <div>
          <h2 className="text-2xl font-bold text-gray-100 flex items-center">
            <Scissors className="w-6 h-6 mr-3 text-purple-400" />
            Prompt Transformer
          </h2>
          <p className="text-gray-400 mt-1">Automatically transform prompts and responses using Regex rules before they reach the model or client.</p>
        </div>
      </div>
      
      <div className="flex-1 overflow-y-auto space-y-6">
        <div className="bg-gray-800 p-5 rounded-xl border border-gray-700">
          <h3 className="text-lg font-medium text-gray-200 mb-4">Prompt Transformation Rules</h3>
          {promptRules.length === 0 ? (
            <p className="text-sm text-gray-500">No prompt rules defined. Configure them in the Interceptor queue.</p>
          ) : (
            <div className="space-y-3">
              {promptRules.map((rule: any, i: number) => (
                <div key={i} className="flex flex-col bg-gray-900 p-3 rounded-lg border border-gray-700">
                  <div className="text-sm font-mono text-pink-400 mb-1">{rule.pattern}</div>
                  <div className="text-sm font-mono text-green-400">➔ {rule.replacement}</div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="bg-gray-800 p-5 rounded-xl border border-gray-700">
          <h3 className="text-lg font-medium text-gray-200 mb-4">Response Transformation Rules</h3>
          {responseRules.length === 0 ? (
            <p className="text-sm text-gray-500">No response rules defined. Configure them in the Interceptor queue.</p>
          ) : (
            <div className="space-y-3">
              {responseRules.map((rule: any, i: number) => (
                <div key={i} className="flex flex-col bg-gray-900 p-3 rounded-lg border border-gray-700">
                  <div className="text-sm font-mono text-pink-400 mb-1">{rule.pattern}</div>
                  <div className="text-sm font-mono text-green-400">➔ {rule.replacement}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

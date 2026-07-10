import { Scissors } from 'lucide-react';
import { RegexRuleList } from './RegexRuleList';

interface TransformerPanelProps {
  settings: any;
  updateSettings: (newSettings: any) => void;
}

export function TransformerPanel({ settings, updateSettings }: TransformerPanelProps) {
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
      
      <div className="flex-1 overflow-y-auto space-y-6 max-w-4xl mx-auto w-full">
        <div className="bg-gray-800 p-5 rounded-xl border border-gray-700">
          <h3 className="text-lg font-medium text-gray-200 mb-4">Request Transformation</h3>
          <p className="text-sm text-gray-400 mb-4">These rules will be applied to the outgoing prompt payload before it reaches the target LLM.</p>
          <RegexRuleList 
            title="Request Find & Replace" 
            rules={promptRules} 
            isObjectRule={true} 
            onChange={(newRules) => updateSettings({ ...settings, promptReplaceRules: newRules })} 
          />
        </div>

        <div className="bg-gray-800 p-5 rounded-xl border border-gray-700">
          <h3 className="text-lg font-medium text-gray-200 mb-4">Response Transformation</h3>
          <p className="text-sm text-gray-400 mb-4">These rules will be applied to the incoming payload before it is forwarded to the client.</p>
          <RegexRuleList 
            title="Response Find & Replace" 
            rules={responseRules} 
            isObjectRule={true} 
            onChange={(newRules) => updateSettings({ ...settings, responseReplaceRules: newRules })} 
          />
        </div>
      </div>
    </div>
  );
}

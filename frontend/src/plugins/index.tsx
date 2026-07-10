import type { PluginUI } from './PluginRegistry';
import { DeduplicatorPanel } from '../components/DeduplicatorPanel';
import { TransformerPanel } from '../components/TransformerPanel';

export const enabledPlugins: PluginUI[] = [
  {
    id: 'context-deduplicator',
    order: 10,
    name: 'Deduplicator',
    component: DeduplicatorPanel,
    renderTabAction: (settings, updateSettings) => {
      const enabled = settings?.enabled || false;
      return (
        <button 
          onClick={(e) => { e.stopPropagation(); updateSettings({ ...settings, enabled: !enabled }); }}
          className={`ml-1 w-8 h-8 flex items-center justify-center rounded-full transition-colors cursor-pointer ${enabled ? 'bg-blue-500/20 text-blue-400 hover:bg-blue-500/30' : 'bg-gray-700/50 text-gray-400 hover:bg-gray-700'}`}
          title={enabled ? "Disable Context Deduplication" : "Enable Context Deduplication"}
        >
          <div className={`w-2.5 h-2.5 rounded-full ${enabled ? 'bg-blue-400 shadow-[0_0_8px_rgba(96,165,250,0.8)]' : 'bg-gray-500'}`}></div>
        </button>
      );
    }
  },
  {
    id: 'prompt-transformer',
    order: 30,
    name: 'Transformer',
    component: TransformerPanel,
    renderTabAction: (settings, updateSettings) => {
      const enabled = settings?.enabled || false;
      const promptRules = settings?.promptReplaceRules || [];
      const responseRules = settings?.responseReplaceRules || [];
      const hasRules = promptRules.length > 0 || responseRules.length > 0;
      
      return (
        <button 
          onClick={(e) => { 
            e.stopPropagation(); 
            if (hasRules) {
              updateSettings({ ...settings, enabled: !enabled }); 
            }
          }}
          className={`ml-1 w-8 h-8 flex items-center justify-center rounded-full transition-colors cursor-pointer ${
            !hasRules ? 'bg-gray-800/50 text-gray-600 cursor-not-allowed' :
            enabled ? 'bg-blue-500/20 text-blue-400 hover:bg-blue-500/30' : 'bg-gray-700/50 text-gray-400 hover:bg-gray-700'
          }`}
          title={!hasRules ? "No rules defined" : enabled ? "Disable Transformer" : "Enable Transformer"}
        >
          <div className={`w-2.5 h-2.5 rounded-full ${
            !hasRules ? 'bg-gray-600' :
            enabled ? 'bg-blue-400 shadow-[0_0_8px_rgba(96,165,250,0.8)]' : 'bg-gray-500'
          }`}></div>
        </button>
      );
    }
  }
];

import { useState } from 'react';
import { Plus, Trash2, ChevronDown, ChevronRight } from 'lucide-react';

interface RegexRuleListProps {
  title: string;
  rules: any[];
  isObjectRule: boolean; // if false, it's string array. If true, it's {regex, with} array.
  onChange: (newRules: any[]) => void;
}

export function RegexRuleList({ title, rules, isObjectRule, onChange }: RegexRuleListProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  const handleAdd = () => {
    if (isObjectRule) {
      onChange([...rules, { regex: '', with: '' }]);
    } else {
      onChange([...rules, '']);
    }
    setIsExpanded(true);
  };

  const handleRemove = (index: number) => {
    const newRules = [...rules];
    newRules.splice(index, 1);
    onChange(newRules);
  };

  const handleChangeString = (index: number, val: string) => {
    const newRules = [...rules];
    newRules[index] = val;
    onChange(newRules);
  };

  const handleChangeObject = (index: number, field: 'regex' | 'with', val: string) => {
    const newRules = [...rules];
    newRules[index] = { ...newRules[index], [field]: val };
    onChange(newRules);
  };

  return (
    <div className="border border-gray-800 rounded-md bg-gray-900/50 overflow-hidden">
      <div 
        className="flex items-center justify-between px-3 py-2 cursor-pointer hover:bg-gray-800 transition-colors"
        onClick={() => setIsExpanded(!isExpanded)}
      >
        <div className="flex items-center space-x-2">
          {isExpanded ? <ChevronDown className="w-3.5 h-3.5 text-gray-400" /> : <ChevronRight className="w-3.5 h-3.5 text-gray-400" />}
          <span className="text-xs font-medium text-gray-300">{title}</span>
          <span className="bg-gray-800 text-gray-400 text-[10px] px-1.5 rounded-full">{rules.length}</span>
        </div>
        <button 
          onClick={(e) => { e.stopPropagation(); handleAdd(); }}
          className="p-1 hover:bg-gray-700 rounded-md text-gray-400 hover:text-white transition-colors"
          title="Add Rule"
        >
          <Plus className="w-3.5 h-3.5" />
        </button>
      </div>

      {isExpanded && (
        <div className="p-2 space-y-2 bg-gray-900 border-t border-gray-800">
          {rules.length === 0 && (
            <div className="text-xs text-gray-500 text-center py-2">No rules configured.</div>
          )}
          {rules.map((rule, idx) => (
            <div key={idx} className="flex flex-col space-y-1 relative group">
              <div className="flex space-x-2">
                {isObjectRule ? (
                  <div className="flex-1 flex flex-col space-y-1">
                    <input
                      type="text"
                      placeholder="Find Regex..."
                      value={rule.regex || ''}
                      onChange={(e) => handleChangeObject(idx, 'regex', e.target.value)}
                      className="w-full bg-gray-950 border border-gray-700 rounded-md px-2 py-1 text-xs text-gray-300 focus:outline-none focus:border-blue-500 font-mono"
                    />
                    <input
                      type="text"
                      placeholder="Replace with..."
                      value={rule.with || ''}
                      onChange={(e) => handleChangeObject(idx, 'with', e.target.value)}
                      className="w-full bg-gray-950 border border-gray-700 rounded-md px-2 py-1 text-xs text-gray-300 focus:outline-none focus:border-blue-500 font-mono"
                    />
                  </div>
                ) : (
                  <input
                    type="text"
                    placeholder="e.g. .*error.*"
                    value={rule || ''}
                    onChange={(e) => handleChangeString(idx, e.target.value)}
                    className="flex-1 bg-gray-950 border border-gray-700 rounded-md px-2 py-1 text-xs text-gray-300 focus:outline-none focus:border-blue-500 font-mono"
                  />
                )}
                <button 
                  onClick={() => handleRemove(idx)}
                  className="p-1 text-gray-500 hover:text-red-400 hover:bg-red-400/10 rounded-md h-fit transition-colors"
                  title="Remove Rule"
                >
                  <Trash2 className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

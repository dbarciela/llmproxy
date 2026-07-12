import { useState } from 'react';
import { QueuePanel } from './QueuePanel';
import { InspectorPanel } from './InspectorPanel';

export default function ManualEditorPanel({ settings, updateSettings }: any) {
  const [selectedRequestId, setSelectedRequestId] = useState<string | null>(null);

  return (
    <>
      <div className="w-80 h-full border-r border-gray-800 bg-gray-900">
        <QueuePanel 
          selectedRequestId={selectedRequestId} 
          onSelectRequest={setSelectedRequestId}
          interceptInvalidJson={settings?.interceptInvalidJson || false}
          interceptRegexRules={settings?.interceptRegexRules || []}
          onUpdateSettings={(invalid: boolean, intR: string[]) => {
            updateSettings({ ...settings, interceptInvalidJson: invalid, interceptRegexRules: intR });
          }}
        />
      </div>
      <div className="flex-1 bg-gray-950 flex flex-col">
        <InspectorPanel requestId={selectedRequestId} onProcessed={() => setSelectedRequestId(null)} />
      </div>
    </>
  );
}

import React from 'react';
import { TransformerPanel } from '../components/TransformerPanel';
import { DeduplicatorPanel } from '../components/DeduplicatorPanel';
import { ArchiveBrowser } from '../components/ArchiveBrowser';

// Export them to avoid unused import TS errors if we ever need them dynamically
export const pluginComponents: Record<string, React.ComponentType<any>> = {
  'prompt-transformer': TransformerPanel,
  'context-deduplicator': DeduplicatorPanel,
  'archive': ArchiveBrowser
};
// Keep other plugin components here if they exist

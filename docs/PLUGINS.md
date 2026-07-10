# Guia de Desenvolvimento de Plugins para o LlamaProxy

O LlamaProxy possui uma arquitetura 100% modular. Podes adicionar novos comportamentos de interceção, modificação ou análise criando **Plugins**.

Um Plugin completo é composto por:
1. **Backend**: Uma classe Java que implementa a interface `ProxyPlugin`.
2. **Frontend** (Opcional): Um componente React `.tsx` que é injetado dinamicamente na Interface Gráfica.

---

## 1. Criar um Plugin no Backend (Java)

Todos os plugins no LlamaProxy processam pedidos (Requests) e respostas (Responses) em formato "pipeline" (corrente de execução).

Cria uma classe na pasta `backend/src/main/java/com/example/llamaproxy/pipeline/plugins/`.

### Exemplo: `HelloPlugin.java`
```java
package com.example.llamaproxy.pipeline.plugins;

import com.example.llamaproxy.config.PluginSettingsManager;
import com.example.llamaproxy.pipeline.ProxyPlugin;
import com.example.llamaproxy.pipeline.RequestContext;
import com.example.llamaproxy.pipeline.ResponseContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10) // Define a ordem de execução no pipeline
public class HelloPlugin implements ProxyPlugin {

    private final PluginSettingsManager settingsManager;

    public HelloPlugin(PluginSettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    // Estrutura de configurações guardada automaticamente no JSON
    public static class HelloSettings {
        public boolean enabled = false;
        public String greeting = "Hello AI!";
    }

    @Override
    public String getId() { return "hello-plugin"; }

    @Override
    public String getName() { return "Hello Plugin"; }

    @Override
    public String getDescription() { return "A simple greeting plugin."; }

    @Override
    public Object getDefaultSettings() { return new HelloSettings(); }

    @Override
    public void processRequest(RequestContext context) {
        HelloSettings settings = settingsManager.getSettingsAs(getId(), HelloSettings.class);
        if (settings == null || !settings.enabled) return;
        
        // Modificar o Payload do Request
        // String payload = context.getPayload();
        // context.setPayload(...);
    }

    @Override
    public void processResponse(ResponseContext context) {
        // Modificar a Response aqui
    }
}
```

O `PluginSettingsManager` irá garantir que as definições por defeito são registadas no arranque, e podes atualizá-las nativamente pela API de configurações genérica do LlamaProxy sem escrever *Controllers* adicionais.

---

## 2. Criar a Interface Gráfica (React)

Se o teu Plugin precisa de interface para ser configurado pelo utilizador, cria o teu componente em `frontend/src/components/MyPluginPanel.tsx`.

### Exemplo: `HelloPanel.tsx`
```tsx
import React from 'react';

// Todos os plugins recebem as settings e uma função para as atualizar.
interface HelloPanelProps {
  settings: any;
  updateSettings: (newSettings: any) => void;
}

export function HelloPanel({ settings, updateSettings }: HelloPanelProps) {
  const enabled = settings?.enabled || false;
  const greeting = settings?.greeting || "";

  return (
    <div className="p-6">
      <h2 className="text-xl font-bold mb-4">Hello Plugin Settings</h2>
      
      <label className="flex items-center space-x-2">
        <input 
          type="checkbox" 
          checked={enabled} 
          onChange={(e) => updateSettings({ ...settings, enabled: e.target.checked })}
        />
        <span>Activar saudação</span>
      </label>
      
      <input 
         className="mt-4 p-2 bg-gray-800 text-white rounded"
         value={greeting}
         onChange={(e) => updateSettings({ ...settings, greeting: e.target.value })}
      />
    </div>
  );
}
```

### 3. Registar o Plugin

Abre o ficheiro `frontend/src/plugins/index.ts` e regista o teu componente React para que seja auto-injetado na barra superior do LlamaProxy.

```typescript
import type { PluginUI } from './PluginRegistry';
import { DeduplicatorPanel } from '../components/DeduplicatorPanel';
import { HelloPanel } from '../components/HelloPanel';

export const enabledPlugins: PluginUI[] = [
  {
    id: 'context-deduplicator',
    order: 10,
    name: 'Deduplicator',
    component: DeduplicatorPanel
  },
  {
    id: 'hello-plugin', // DEVE ser igual ao getId() do Java!
    order: 20, // Define a ordem na barra superior (mapeia com a @Order do backend)
    name: 'Say Hello',
    component: HelloPanel,
    renderTabAction: (settings, updateSettings) => {
      // (Opcional) Permite injetar um botão de on/off direto na tab!
      const enabled = settings?.enabled || false;
      return (
        <button onClick={(e) => { e.stopPropagation(); updateSettings({ ...settings, enabled: !enabled }); }}>
          {enabled ? 'ON' : 'OFF'}
        </button>
      );
    }
  }
];
```

E já está! Ao iniciar o LlamaProxy, irá aparecer um novo botão "Say Hello" (ordenado corretamente através do atributo `order`) no menu principal. O clique nesse botão renderiza o teu `HelloPanel` que está automaticamente sincronizado com a classe `HelloPlugin` no servidor.

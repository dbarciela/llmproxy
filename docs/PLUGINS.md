# Guia de Desenvolvimento de Plugins para o LlamaProxy

O LlamaProxy possui uma arquitetura 100% modular. Podes adicionar novos comportamentos de interceção, modificação ou análise criando **Plugins**.

Um Plugin completo é composto por:
1. **Backend**: Uma classe Java que implementa a interface `BufferingPlugin` ou `StreamingPlugin`.
2. **Frontend** (Opcional): Um componente React `.tsx` que é injetado dinamicamente na Interface Gráfica.

---

## 1. Criar um Plugin no Backend (Java)

Todos os plugins no LlamaProxy processam pedidos (Requests) e respostas (Responses) em formato "pipeline" (corrente de execução).

Podes implementar uma de duas interfaces base, dependendo da necessidade do teu plugin:
- **`BufferingPlugin`**: Recebe o payload do request/response como uma `String` completa. Tem maior impacto na memória para payloads gigantescos (ex: > 50MB), mas é fundamental se o teu plugin precisar de analisar ou editar o documento como um todo (ex: Manipulação de JSON complexa, Deduplicação por Sliding Window, Editores Manuais). Os plugins de Buffering recebem um aviso laranja no ecrã de Configuração ⚠️.
- **`StreamingPlugin`**: Recebe o payload como um `InputStream` e escreve num `OutputStream`. Processa dados *on-the-fly* sem manter tudo em memória. Ideal para substituições em streaming (ex: expressões regulares simples linha-a-linha).

Cria uma classe na pasta `backend/src/main/java/com/example/llamaproxy/pipeline/plugins/`.

### Exemplo: `HelloPlugin.java`
```java
package com.example.llamaproxy.pipeline.plugins;

import com.example.llamaproxy.config.PluginSettingsManager;
import com.example.llamaproxy.pipeline.BufferingPlugin;
import com.example.llamaproxy.pipeline.RequestContext;
import com.example.llamaproxy.pipeline.ResponseContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10) // Define a ordem de execução no pipeline
public class HelloPlugin implements BufferingPlugin {

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
    public String getUiTabName() { return "Say Hello"; }

    @Override
    public boolean hasUiToggle() { return true; }

    @Override
    public int getDefaultOrder() { return 20; }

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

Abre o ficheiro `frontend/src/plugins/index.tsx` e mapeia o ID do teu plugin para o componente React. A barra superior e a ordem do pipeline são geradas dinamicamente com base nas respostas do backend, logo não precisas de definir a ordem ou os toggles no frontend!

```typescript
import React from 'react';
import { HelloPanel } from '../components/HelloPanel';

export const pluginComponents: Record<string, React.ComponentType<any>> = {
  'hello-plugin': HelloPanel
};
```

O LlamaProxy vai detetar o teu plugin no backend, adicionar o toggle na barra superior (se `hasUiToggle() = true`), e carregar o teu componente React (`HelloPanel`) na tab "Say Hello". Também vai aparecer automaticamente na tab de Global Configuration para que a sua ordem de execução possa ser modificada.

### Notas importantes:
- Os dados injetados via `updateSettings` no Frontend são guardados no disco (`proxy-settings.json`).

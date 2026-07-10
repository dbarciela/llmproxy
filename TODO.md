# LlamaProxy - Roadmap & TODO

Este documento lista as funcionalidades planeadas para o LlamaProxy, focadas em melhorar a capacidade de *debugging*, análise e manipulação da interação entre o IDE (onde corre o agente AI) e o servidor local do Llama.cpp.

## 🛠️ 1. Debugging de Pedidos e Respostas
- [ ] **"Replay" & Playground Interativo:** A capacidade de pegar num pedido intercetado no painel Network Logs e, com um clique, abri-lo num separador de Playground. Aí poderias alterar a temperatura, reescrever parte do prompt e enviá-lo novamente ao servidor Llama manualmente para ver se uma pequena afinação resolvia a alucinação do modelo, sem precisares de voltar a desencadear toda a ação no IDE.
- [ ] **Validação Estrutural e Breakpoints (JSON Validator):** Como o IDE espera muitas vezes JSON, se o modelo alucinar na formatação o IDE "encrava". Esta *feature* divide-se em várias vertentes:
  - **Deteção Passiva:** O LlamaProxy deteta que o pedido envolvia JSON e faz `JSON.parse()` à resposta. Se falhar, o pedido ganha uma *badge* vermelha nos *Network Logs* com a mensagem de erro exata (ex: falta de chaveta), explicando imediatamente o porquê do IDE falhar.
  - **Visualizador de JSON Enriquecido:** Para suportar a validação, os componentes visuais do proxy que exibem os JSONs devem ser atualizados para suportar sublinhados vermelhos ou realces indicando a linha/coluna exata onde a sintaxe quebrou.
  - [x] **JSON Inválido como Breakpoint (Interceção Ativa):** Hoje o *Interceptor* só para pedidos com base em *regex*. A ideia é adicionar uma opção "Intercetar se JSON for inválido". Assim, se o Llama devolver um JSON partido, o proxy bloqueia a entrega ao IDE e atira a resposta para a *Queue*. O programador pode abrir, corrigir o JSON manualmente na UI do LlamaProxy, e clicar em "Forward" para salvar a sessão do IDE!
  - [ ] **Auto-Healing (Futuro):** O LlamaProxy interceta o JSON inválido, injeta uma nova instrução para o Llama em pano de fundo ("Corrije o teu JSON: erro X") e, quando estiver correto, reencaminha de forma transparente para o IDE.
- [ ] **Análise Lógica do Prompt:** Em vez de mostrar o prompt como um bloco gigante de texto cru, o LlamaProxy podia tentar fazer "parsing" inteligente, separando visualmente o System Prompt, o Contexto (ficheiros que o IDE enviou) e a User Instruction. Isto ajudaria muito a ler prompts gigantes.

## ⚡ 2. Performance e Análise de Tokens
- [x] **Métricas de Tokenização:** Mostrar os metadados reais da inferência do lado do cliente: Tempo até ao primeiro token (TTFT - Time To First Token), Tokens por segundo e Custo de Contexto. Isto é vital para perceber se os delays no IDE são culpa da rede, do tamanho do contexto ou se o Llama local está sobrecarregado.
- [x] **Visualizador de Tokens:** Um problema comum nos LLMs locais é o corte abrupto devido aos limites de contexto. Mostrar o número de tokens de um pedido e colocar um aviso visual se estiver perigosamente perto do limite máximo do modelo que tens carregado.
- [x] **Monitorização de Hardware (Live VRAM/CPU):** Como tens o servidor a correr localmente, o LlamaProxy podia monitorizar o teu sistema (VRAM, RAM, CPU) e ter um pequeno widget no cabeçalho. Se o Llama estiver lento, bates o olho e percebes imediatamente "ah, o modelo transbordou a VRAM e fez fallback para a RAM (swap)".

## 🎭 3. Mocking e Manipulação (Testes ao Agente)
- [ ] **Manipulação e Injeção de Contexto (Transformadores de Prompt):** Capacidade de o proxy interceptar e alterar automaticamente o conteúdo dos pedidos antes de chegarem ao Llama. Isto inclui:
  - [ ] **Injeção de Prefixos/Sufixos:** Adicionar regras fixas ao *System Prompt* (ex: "responde sempre em Português" ou adicionar diretivas de formatação) de forma transparente para o IDE.
  - [x] **Find & Replace Dinâmico (Regex):** Substituir partes específicas do prompt do IDE em tempo real baseando-se em Regex. Muito útil para remover bad-patterns que o IDE insista em enviar. Agora suporta múltiplos conjuntos de regras para substituição e também para a condição de interceção!
  - [ ] **Deduplicação de Contexto:** Algoritmos experimentais de otimização onde o LlamaProxy analisa o histórico do *chat* que o IDE envia e tenta remover blocos de código ou instruções redundantes que apenas gastam tokens e confundem a atenção do modelo (optimização agressiva de contexto).

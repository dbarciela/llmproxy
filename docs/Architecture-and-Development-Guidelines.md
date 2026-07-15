# Architecture and Development Guidelines

This document outlines the architectural decisions, design patterns, and best practices for developing Aura (The LLM Proxy).

## 1. Backend Architecture (Spring Boot & Java 21)

### 1.1 Proxy Pipeline
Aura acts as a middleware reverse proxy between the frontend/client and the LLM Server (e.g., Llama.cpp).
- **Entry Point:** `ProxyController.java` intercepts `/v1/**` requests.
- **Pipeline:** Requests are passed through a `ProxyPipeline` which executes an ordered list of `ProxyPlugin` instances.
- **Context Objects:** `RequestContext` and `ResponseContext` are passed through the pipeline, containing the payload, headers, and metadata.

### 1.2 Plugin System
Plugins encapsulate discrete features and can mutate the request or intercept the response.
- **`ProxyPlugin` Interface:** The base contract.
- **`AsyncPlugin`:** For fire-and-forget background operations (e.g., saving to DB in `ArchivePlugin`).
- **`BufferingPlugin`:** For plugins that need to inspect or modify the full streaming response before releasing it (e.g., `FormatFixerPlugin`).
- **`StreamingPlugin`:** For plugins that need to read chunks in real-time without buffering them (e.g., `LiveChatPlugin`).

### 1.3 SSE Communication (`SseBroadcaster.java`)
The backend communicates asynchronous events to the frontend via Server-Sent Events using a single, multiplexed stream:
- **`SseBroadcaster`**: A singleton component that manages active `SseEmitter` clients.
- **Virtual Threads**: Each client is assigned a single Virtual Thread connected to an unbounded `BlockingQueue`. This allows the server to buffer bursts of events without tying up OS threads.
- **State Replay**: When a new client connects, the `SseBroadcaster` immediately replays the cached state (e.g., the last `REQUEST` payload, `CHUNK`s, or `DONE` event) so the frontend UI rehydrates instantly without data gaps.

### 1.4 Concurrency & Threads
- **Virtual Threads:** Enabled via `spring.threads.virtual.enabled=true`. All requests, SSE streams, and async operations are inherently lightweight.
- **Blocking Operations:** Because we use Virtual Threads, blocking operations (e.g., `Thread.sleep`, `queue.take()`, or synchronous HTTP calls) are safe and do not cause thread starvation.
- **Target Polling:** Services like `TargetServerController`, `SlotsMonitorService`, and `MetricsMonitorService` use Virtual Threads to continuously poll the LLM Server `/slots` and `/metrics` without overhead.

---

## 2. Frontend Architecture (React + Vite + Tailwind)

### 2.1 Plugin Registry (`PluginRegistry.ts`)
The UI is modular. Plugins register their tabs, configuration components, and commands dynamically.
- **`PluginUI`:** Registers the UI component and icon for a plugin tab.
- **`GlobalCommand`:** Registers a static command for the Command Palette (Cmd+K).
- **`SearchProvider`:** Registers a dynamic search function (e.g., historical archive search) for the Command Palette.

### 2.2 Server-Sent Events (SSE)
- **Problem:** Browsers limit concurrent HTTP/1.1 connections to the same host (usually 6).
- **Solution:** `sseService.ts` implements a Singleton pattern. It opens exactly **one** `EventSource` connection to `/api/proxy/live` and dispatches payloads to any subscribed React component.
- **Stateful Caching:** `sseService.ts` intercepts and caches the latest payload of key events (like `SLOTS` or `REQUEST`), dispatching them immediately to new components that subscribe late (e.g., when a Modal opens).
- **Rule:** **Never** instantiate `new EventSource()` directly inside a component unless for a completely separate stream (like target-logs). Always use `sseService.subscribe()`.

### 2.3 UX & Styling
- **Tailwind CSS:** Used for all styling.
- **Dark Theme:** The default and only theme is dark mode (slate/gray-900).
- **Toasts:** Use `sonner` for non-intrusive notifications (`toast.success`, `toast.error`).
- **Monaco Editor:** Used for code inspection and manual editing. Always load schemas dynamically (e.g., OpenAI schema) to enable IntelliSense.

---

## 3. Development Guidelines

1. **Do not block the React UI thread:** Use `useBackgroundTasks.ts` for long-running frontend operations (e.g., updating target server models).
2. **Encapsulate logic in Plugins:** If adding a new feature that intercepts traffic, implement it as a `ProxyPlugin` in the backend and register a `PluginUI` in the frontend.
3. **Database Operations:** Use `JdbcTemplate` for simple queries. Ensure all DB operations inside plugins are asynchronous (`AsyncPlugin`) so they don't delay the LLM response to the user.
4. **Resilience:** The proxy must never crash if a plugin fails. Wrap plugin execution in try-catch blocks and log appropriately.

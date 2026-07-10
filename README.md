# LlamaProxy 🦙

LlamaProxy is a powerful, developer-friendly reverse proxy designed to sit between your AI applications and an OpenAI-compatible backend (specifically tailored for `llama.cpp` servers). It features a modern React-based UI and a highly concurrent Java Spring Boot backend built on Virtual Threads.

With LlamaProxy, you can seamlessly intercept, modify, monitor, and archive your AI API traffic in real-time.

---

## 🌟 Key Features

* **📡 Live Chat View**: Monitor conversations happening between your apps and the AI backend in real-time. Fully supports streaming, Markdown rendering, and expandable Tool Calls.
* **⏸️ Request & Response Interceptor**: Pause incoming requests or outgoing responses mid-flight. Review and modify the JSON payload using the built-in Monaco Editor before forwarding them.
* **🗃️ Archive & Network Logs**: Automatically logs all traffic to an FTS5-backed SQLite database. Perform blazing-fast full-text searches across your entire history of prompts and responses.
* **🔔 Stateful Notifications**: An intelligent SSE-driven notification system that alerts you when requests are paused or updates are available, but stays out of your way if you're already viewing the relevant UI panel.
* **🛠️ Target Server Control & Console**: Monitor the standard output of your `llama.cpp` process in real-time via SSE, kill the server, or launch a configured Web UI directly from the dashboard.
* **🔄 Background Updates**: Automatically checks for `llama.cpp` updates, downloads, unzips, and restarts the server via an elegant SSE-powered Progress Modal.
* **🧹 Smart Archive Cleanup**: Automatically detect and purge redundant chat sessions (where smaller sessions are perfect subsets of larger ones) to keep your database clean.
* **✂️ Context Deduplication**: Seamlessly strip out enormous duplicated text blocks from conversations using a blazing-fast Rabin-Karp algorithm. Saves massive amounts of tokens and VRAM while preserving cache integrity.
* **⚡ Prompt Transformer**: Automatically transform prompts and responses using Regex rules before they reach the model or client.
* **💻 Hardware Metrics & Tokenization**: Monitor system VRAM and Context Token Limits in a real-time widget, alongside Time To First Token (TTFT) and Generation Speed (T/s).
* **🧩 Dynamic Plugin Architecture**: Completely decoupled backend and frontend plugins. Build new UI panels and interceptors without touching the core framework. See the [Plugins Documentation](docs/PLUGINS.md).

---

## 🏗️ Architecture & Tech Stack

**Backend:**
* Java 25 + Spring Boot 3
* **Concurrency:** Fully utilizes Java Virtual Threads for scalable, non-blocking I/O operations.
* **Database:** SQLite (with FTS5 enabled) for lightweight, high-performance local storage.
* **Real-time:** Server-Sent Events (SSE) via Spring `SseEmitter` for live proxy data and notifications.

**Frontend:**
* React 18 + Vite
* TypeScript + TailwindCSS for sleek, responsive UI design.
* **Monaco Editor** for robust, developer-grade JSON editing.
* **React Markdown** & plugins for intelligent chat rendering (including expandable tool calls).

---

## 🚀 Getting Started

### Prerequisites
* Java Development Kit (JDK) 25 or higher
* Node.js and npm
* A running instance of `llama.cpp` (or any OpenAI-compatible API)

### Configuration
By default, the backend expects your target server to be running on `http://localhost:8080/v1` (or whichever port is defined in your `application.properties`). Ensure you configure the following properties in `backend/src/main/resources/application.properties` as needed:

* `target.server.url`: The base URL of the LLM API server.
* `target.webui.url`: Optional link to a Web UI (like OpenWebUI) accessible from the console modal.
* `target.server.restart-script`: Path to the script used to restart the target server.
* `llama.cpp.install.dir`: Path to the directory where `llama.cpp` releases are extracted.

### Building & Running

1. **Start the Backend**
   Open a terminal in the `backend` directory and run:
   ```bash
   cd backend
   ./mvnw clean spring-boot:run
   ```

2. **Start the Frontend (Development Mode)**
   Open a separate terminal in the `frontend` directory and run:
   ```bash
   cd frontend
   npm install
   npm run dev
   ```
   *The Vite dev server will typically start on `http://localhost:5173` and proxy API calls to the backend on port 8080.*

3. **Build Frontend for Production**
   To serve the UI directly from the Spring Boot backend:
   ```bash
   cd frontend
   npm run build
   ```
   This will compile the UI and place it in the `backend/src/main/resources/static` directory. You can then access the proxy UI at `http://localhost:8080`.

---

## 🔌 API Proxy Usage

Once LlamaProxy is running, point your AI applications to `http://localhost:8080` instead of your actual LLM server. 
For example, if using the OpenAI Python SDK:

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8080/v1",
    api_key="sk-no-key-required"
)

# Your requests will now appear in the LlamaProxy dashboard!
```

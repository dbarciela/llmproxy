# Aura ✨

Aura (**Au**gmented **R**equest **A**rchitecture) is a powerful, developer-friendly AI Hub designed to sit between your AI applications and an OpenAI-compatible backend (agnostic, but originally tailored for local servers). It features a modern React-based UI and a highly concurrent Java Spring Boot backend built on Virtual Threads.

With Aura, you can seamlessly intercept, modify, monitor, and archive your AI API traffic in real-time.

> [!WARNING]
> **Disclaimer**: This is a personal project built specifically to satisfy my own development needs. The majority of the codebase was generated using Artificial Intelligence with minimal human code review. While I welcome issue reports and pull requests, **nobody should use this application in production, critical environments, or expose it to the public internet**. It was built for local development and research purposes only.

---

## 🌟 Key Features

* **🕵️‍♂️ Live Inspection & Interception**: Monitor LLM traffic in real-time (with streaming & Markdown support). Pause, review, and edit JSON payloads mid-flight using a built-in Monaco Editor.
* **🧠 Context Optimization**: Automatically strip out massive, redundant text blocks using a Rabin-Karp algorithm (Context Deduplication) and transform prompts on-the-fly to save tokens and VRAM.
* **📊 Advanced Telemetry**: Track live system metrics (CPU, VRAM usage) and token generation speeds (Time-to-First-Token, T/s) directly from the dashboard status bar.
* **🗃️ Smart Archiving**: Automatically logs all traffic to an FTS5-backed SQLite database for blazing-fast full-text searches. Aura actively purges redundant chat sessions to keep your history clean.
* **🛠️ Server Orchestration**: Control your local backend (e.g., `llama.cpp`) right from the UI. Stream console logs, trigger background updates, or launch connected Web UIs with a single click.
* **🧩 Modular Plugin Pipeline**: Built on a highly concurrent Java Virtual Threads backend with a drag-and-drop React frontend. Easily build and reorder custom interceptors (Streaming, Async, or Buffering) without touching the core framework. See the [Plugins Documentation](docs/PLUGINS.md).

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

Once Aura is running, point your AI applications to `http://localhost:8080` instead of your actual LLM server. 
For example, if using the OpenAI Python SDK:

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8080/v1",
    api_key="sk-no-key-required"
)

# Your requests will now appear in the Aura dashboard!
```

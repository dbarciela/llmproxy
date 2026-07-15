type Listener = (data: any) => void;

class SseService {
  private es: EventSource | null = null;
  private listeners: Set<Listener> = new Set();
  private connectionCount = 0;
  
  private lastEvents: Record<string, any> = {};
  private lastChunks: any[] = [];
  private lastRequestId: string | null = null;

  subscribe(listener: Listener) {
    this.listeners.add(listener);
    this.connectionCount++;

    // Replay state immediately for this new listener
    if (this.lastEvents['REQUEST']) listener(this.lastEvents['REQUEST']);
    this.lastChunks.forEach(chunk => listener(chunk));
    if (this.lastEvents['DONE']) listener(this.lastEvents['DONE']);
    
    // Replay other stateful events
    Object.keys(this.lastEvents).forEach(type => {
      if (type !== 'REQUEST' && type !== 'DONE' && this.lastEvents[type]) {
        listener(this.lastEvents[type]);
      }
    });

    if (this.connectionCount === 1) {
      this.connect();
    }

    return () => {
      this.listeners.delete(listener);
      this.connectionCount--;
      if (this.connectionCount === 0) {
        this.disconnect();
      }
    };
  }

  private connect() {
    if (this.es) return;
    this.es = new EventSource('/api/proxy/live');
    this.es.addEventListener('live-chat', (e: any) => {
      try {
        const payload = JSON.parse(e.data);
        
        // Cache management
        if (payload.type === 'REQUEST') {
          this.lastRequestId = payload.id;
          this.lastEvents['REQUEST'] = payload;
          this.lastEvents['DONE'] = null;
          this.lastChunks = [];
        } else if (payload.type === 'CHUNK' && payload.id === this.lastRequestId) {
          this.lastChunks.push(payload);
        } else if (payload.type === 'DONE' && payload.id === this.lastRequestId) {
          this.lastEvents['DONE'] = payload;
        } else {
          // Cache metrics, hardware, context limits, etc based on type (or pluginId for plugins)
          const cacheKey = payload.pluginId ? `PLUGIN_${payload.pluginId}_${payload.type}` : payload.type;
          this.lastEvents[cacheKey] = payload;
        }

        this.listeners.forEach(l => l(payload));
      } catch (err) {
        console.error("Failed to parse SSE payload", err);
      }
    });
    this.es.onerror = () => {
      console.error("SSE connection error in sseService");
    };
  }

  private disconnect() {
    if (this.es) {
      this.es.close();
      this.es = null;
    }
  }
}

export const sseService = new SseService();

import { useState, useCallback, useRef } from 'react';

export interface Step {
  id: string;
  label: string;
  status: 'pending' | 'running' | 'done' | 'error';
  message?: string;
}

export interface BackgroundTask {
  id: string; // The URL will be used as ID since we usually have 1 task per URL
  title: string;
  streamUrl: string;
  steps: Step[];
  isDone: boolean;
  errorMsg: string | null;
  isMinimized: boolean;
}

export function useBackgroundTasks() {
  const [tasks, setTasks] = useState<BackgroundTask[]>([]);
  const eventSources = useRef<Record<string, EventSource>>({});

  const startTask = useCallback((url: string, title: string) => {
    setTasks(prev => {
      // If task already exists, just maximize it
      if (prev.find(t => t.id === url)) {
        return prev.map(t => t.id === url ? { ...t, isMinimized: false } : t);
      }
      
      const newTask: BackgroundTask = {
        id: url,
        title,
        streamUrl: url,
        steps: [],
        isDone: false,
        errorMsg: null,
        isMinimized: false
      };
      return [...prev, newTask];
    });

    if (eventSources.current[url]) {
        return; // Already streaming
    }

    const es = new EventSource(url);
    eventSources.current[url] = es;

    es.addEventListener('progress', (e: any) => {
      try {
        const payload = JSON.parse(e.data);
        
        setTasks(prev => prev.map(task => {
          if (task.id !== url) return task;

          if (payload.step === 'ERROR') {
            es.close();
            delete eventSources.current[url];
            return { ...task, errorMsg: payload.message, isDone: true };
          }

          if (payload.step === 'DONE') {
            es.close();
            delete eventSources.current[url];
            const updatedSteps = task.steps.map(s => s.status === 'running' ? { ...s, status: 'done' as const } : s);
            return { ...task, isDone: true, steps: updatedSteps };
          }

          const existingIdx = task.steps.findIndex(s => s.id === payload.step);
          if (existingIdx >= 0) {
            const nextSteps = [...task.steps];
            nextSteps[existingIdx] = { 
                ...nextSteps[existingIdx], 
                status: payload.status, 
                message: payload.message || nextSteps[existingIdx].message 
            };
            return { ...task, steps: nextSteps };
          } else {
            return { 
                ...task, 
                steps: [...task.steps, { 
                    id: payload.step, 
                    label: payload.step.replace(/_/g, ' '), 
                    status: payload.status, 
                    message: payload.message 
                }] 
            };
          }
        }));

      } catch (err) {
        console.error('Failed to parse progress chunk', err);
      }
    });

    es.addEventListener('error', () => {
      setTasks(prev => prev.map(task => {
        if (task.id !== url || task.isDone) return task;
        return { ...task, errorMsg: "Connection lost to the server.", isDone: true };
      }));
      es.close();
      delete eventSources.current[url];
    });

  }, []);

  const minimizeTask = useCallback((id: string) => {
    setTasks(prev => prev.map(t => t.id === id ? { ...t, isMinimized: true } : t));
  }, []);

  const openTask = useCallback((id: string) => {
    setTasks(prev => prev.map(t => t.id === id ? { ...t, isMinimized: false } : t));
  }, []);

  const closeTask = useCallback((id: string) => {
    setTasks(prev => prev.filter(t => t.id !== id));
    if (eventSources.current[id]) {
        eventSources.current[id].close();
        delete eventSources.current[id];
    }
  }, []);

  return {
    tasks,
    startTask,
    minimizeTask,
    openTask,
    closeTask
  };
}

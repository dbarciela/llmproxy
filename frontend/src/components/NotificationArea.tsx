import { useState, useEffect, useRef } from 'react';
import { Bell, X, ExternalLink } from 'lucide-react';

export interface NotificationAction {
    label: string;
    api?: string;
    url?: string;
    tab?: string;
    autoDismiss?: boolean;
}

export interface NotificationDTO {
    id: string;
    sourcePlugin: string;
    title: string;
    message: string;
    level: string;
    createdAt: number;
    actions: NotificationAction[];
}

const getRelativeTime = (timestamp: number) => {
    if (!timestamp) return '';
    const diff = Date.now() - timestamp;
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return 'Just now';
    if (mins < 60) return `${mins}m ago`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
};

interface Props {
    onChangeTab: (tab: any) => void;
}

export function NotificationArea({ onChangeTab }: Props) {
    const [notifications, setNotifications] = useState<NotificationDTO[]>([]);
    const [isOpen, setIsOpen] = useState(false);
    const panelRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const fetchNotifs = () => {
            fetch('/api/proxy/notifications')
                .then(r => r.json())
                .then(data => setNotifications(data))
                .catch(() => {});
        };
        
        fetchNotifs();
        const interval = setInterval(fetchNotifs, 10000);

        const evtSource = new EventSource('/api/proxy/live');
        
        evtSource.addEventListener('live-chat', (event) => {
            try {
                const payload = JSON.parse(event.data);
                if (payload.type === 'NOTIFICATION') {
                    const newNotif = typeof payload.data === 'string' ? JSON.parse(payload.data) : payload.data;
                    setNotifications(prev => {
                        if (prev.find(n => n.id === newNotif.id)) return prev;
                        return [newNotif, ...prev];
                    });
                }
            } catch(e) { /* ignore parse errors */ }
        });

        return () => {
            evtSource.close();
            clearInterval(interval);
        };
    }, []);

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (panelRef.current && !panelRef.current.contains(event.target as Node)) {
                setIsOpen(false);
            }
        };
        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside);
        }
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [isOpen]);

    const markAsRead = async (id: string, e?: React.MouseEvent) => {
        if (e) e.stopPropagation();
        try {
            await fetch(`/api/proxy/notifications/${id}/read`, { method: 'POST' });
            setNotifications(prev => prev.filter(n => n.id !== id));
        } catch {}
    };

    const handleAction = async (notif: NotificationDTO, action: NotificationAction) => {
        if (action.api) {
            try {
                const res = await fetch(action.api, { method: 'POST' });
                const text = await res.text();
                // We won't alert here to avoid blocking UI, we could just rely on new notifications
                console.log("Action response:", text);
            } catch (e) {
                console.error('Action failed', e);
            }
        }
        if (action.url) {
            window.open(action.url, '_blank');
        }
        if (action.tab) {
            onChangeTab(action.tab);
        }
        
        if (action.autoDismiss !== false) {
            markAsRead(notif.id);
            setIsOpen(false);
        }
    };

    const getLevelColors = (level: string) => {
        switch (level) {
            case 'warning': return 'bg-yellow-900/30 border-yellow-700 text-yellow-400';
            case 'error': return 'bg-red-900/30 border-red-700 text-red-400';
            case 'success': return 'bg-green-900/30 border-green-700 text-green-400';
            case 'info':
            default: return 'bg-blue-900/30 border-blue-700 text-blue-400';
        }
    };

    return (
        <div className="relative" ref={panelRef}>
            <button 
                onClick={() => setIsOpen(!isOpen)}
                className="relative p-2 rounded-full hover:bg-gray-800 transition-colors text-gray-300"
            >
                <Bell className="w-5 h-5" />
                {notifications.length > 0 && (
                    <span className="absolute top-0 right-0 inline-flex items-center justify-center w-4 h-4 text-[10px] font-bold text-white bg-red-500 rounded-full">
                        {notifications.length}
                    </span>
                )}
            </button>

            {isOpen && (
                <div className="absolute right-0 mt-2 w-80 bg-gray-900 border border-gray-700 rounded-lg shadow-xl overflow-hidden z-50">
                    <div className="p-3 border-b border-gray-800 bg-gray-950 flex justify-between items-center">
                        <h3 className="text-sm font-semibold text-gray-200">Notifications</h3>
                        {notifications.length > 0 && (
                            <button 
                                onClick={() => notifications.forEach(n => markAsRead(n.id))}
                                className="text-xs text-gray-500 hover:text-gray-300"
                            >
                                Clear All
                            </button>
                        )}
                    </div>
                    
                    <div className="max-h-96 overflow-y-auto">
                        {notifications.length === 0 ? (
                            <div className="p-4 text-center text-gray-500 text-sm">
                                No new notifications
                            </div>
                        ) : (
                            notifications.map(n => (
                                <div key={n.id} className={`p-3 border-b border-gray-800/50 ${getLevelColors(n.level)} bg-opacity-10 border-l-4`}>
                                    <div className="flex justify-between items-start">
                                        <div className="flex flex-col">
                                            <h4 className="text-sm font-semibold">{n.title}</h4>
                                            {n.createdAt && <span className="text-[10px] text-gray-500">{getRelativeTime(n.createdAt)}</span>}
                                        </div>
                                        <button onClick={(e) => markAsRead(n.id, e)} className="text-gray-500 hover:text-gray-300">
                                            <X className="w-4 h-4" />
                                        </button>
                                    </div>
                                    <p className="text-xs mt-1 text-gray-400">{n.message}</p>
                                    
                                    {n.actions && n.actions.length > 0 && (
                                        <div className="mt-3 flex flex-wrap gap-2">
                                            {n.actions.map((act, i) => (
                                                <button 
                                                    key={i}
                                                    onClick={() => handleAction(n, act)}
                                                    className="px-2 py-1 text-xs font-medium rounded bg-gray-800 hover:bg-gray-700 text-gray-200 border border-gray-700 transition-colors flex items-center"
                                                >
                                                    {act.label}
                                                    {act.url && <ExternalLink className="w-3 h-3 ml-1 opacity-70" />}
                                                </button>
                                            ))}
                                        </div>
                                    )}
                                </div>
                            ))
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}

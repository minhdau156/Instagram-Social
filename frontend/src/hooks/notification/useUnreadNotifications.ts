import { Client } from "@stomp/stompjs";
import { useEffect, useState } from "react"
import SockJS from "sockjs-client";
import { useQueryClient } from "@tanstack/react-query";


export const useUnreadNotifications = () => {
    const queryClient = useQueryClient();
    const [unreadCount, setUnreadCount] = useState(0);
    useEffect(() => {
        const token = localStorage.getItem('accessToken');
        if (!token) return;
        const client = new Client({
            webSocketFactory: () => new SockJS('http://localhost:8080/ws', null, { transports: ['websocket'] }),
            connectHeaders: { Authorization: `Bearer ${token}` },
            reconnectDelay: 5000,
            onConnect: () => {
                client.subscribe(
                    '/user/topic/notifications',
                    (_frame) => {
                        setUnreadCount(prevCount => prevCount + 1);
                        queryClient.invalidateQueries({ queryKey: ['notifications'] });
                    }
                );

            },
            onDisconnect: () => { },
        })
        client.activate();
        return () => {
            client.deactivate();
        }
    }, [queryClient]);

    const resetCount = () => setUnreadCount(0);

    return { unreadCount, resetCount };
}
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from './useAuth';
import type { Conversation, Message, SendMessagePayload } from '../types/messaging';

export function useWebSocket(conversationId: string | null) {
  const queryClient = useQueryClient();
  const { profile } = useAuth();
  const clientRef = useRef<Client | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [typingUserIds, setTypingUserIds] = useState<string[]>([]);
  const typingTimers = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  // Create STOMP client once and activate for the lifetime of the session
  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 5000,
      onConnect: () => setIsConnected(true),
      onDisconnect: () => setIsConnected(false),
    });
    clientRef.current = client;
    client.activate();

    const timers = typingTimers.current;
    return () => {
      client.deactivate();
      setIsConnected(false);
      timers.forEach(clearTimeout);
    };
  }, []);

  // Subscribe to conversation messages and typing events when a conversation is open
  useEffect(() => {
    const client = clientRef.current;
    if (!client || !isConnected || !conversationId) return;

    const msgSub = client.subscribe(
      `/topic/conversations/${conversationId}`,
      (frame) => {
        const message: Message = JSON.parse(frame.body);

        // Prepend to messages cache (newest-first)
        queryClient.setQueryData<Message[]>(
          ['messages', conversationId],
          (old) => (old ? [message, ...old] : [message])
        );

        // Update lastMessage on the matching conversation
        queryClient.setQueryData<Conversation[]>(['conversations'], (old) =>
          old?.map((conv) =>
            conv.id === conversationId ? { ...conv, lastMessage: message } : conv
          ) ?? []
        );
      }
    );

    const typingSub = client.subscribe(
      `/topic/conversations/${conversationId}/typing`,
      (frame) => {
        const { userId, isTyping } = JSON.parse(frame.body) as {
          userId: string;
          isTyping: boolean;
        };

        setTypingUserIds((prev) => {
          const without = prev.filter((id) => id !== userId);
          return isTyping ? [...without, userId] : without;
        });

        // Auto-clear after 3 seconds of no update
        const existing = typingTimers.current.get(userId);
        if (existing) clearTimeout(existing);
        if (isTyping) {
          const timer = setTimeout(() => {
            setTypingUserIds((prev) => prev.filter((id) => id !== userId));
            typingTimers.current.delete(userId);
          }, 3000);
          typingTimers.current.set(userId, timer);
        }
      }
    );

    return () => {
      msgSub.unsubscribe();
      typingSub.unsubscribe();
    };
  }, [conversationId, isConnected, queryClient]);

  // Global unread-count subscription (for nav badge)
  useEffect(() => {
    const client = clientRef.current;
    const userId = profile?.user.id;
    if (!client || !isConnected || !userId) return;

    const sub = client.subscribe(
      `/user/${userId}/topic/unread-count`,
      (frame) => {
        const { conversationId: convId, unreadCount } = JSON.parse(frame.body) as {
          conversationId: string;
          unreadCount: number;
        };
        queryClient.setQueryData<Conversation[]>(['conversations'], (old) =>
          old?.map((conv) =>
            conv.id === convId ? { ...conv, unreadCount } : conv
          ) ?? []
        );
      }
    );

    return () => sub.unsubscribe();
  }, [isConnected, profile?.user.id, queryClient]);

  const sendMessage = (payload: SendMessagePayload) => {
    const client = clientRef.current;
    if (!client?.connected || !conversationId) return;
    client.publish({
      destination: '/app/chat.send',
      body: JSON.stringify({ ...payload, conversationId }),
    });
  };

  const sendTyping = (convId: string, isTyping: boolean) => {
    const client = clientRef.current;
    if (!client?.connected) return;
    client.publish({
      destination: '/app/chat.typing',
      body: JSON.stringify({ conversationId: convId, isTyping }),
    });
  };

  return { sendMessage, sendTyping, isConnected, typingUserIds };
}

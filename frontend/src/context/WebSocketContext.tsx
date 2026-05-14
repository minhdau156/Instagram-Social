import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import React, { createContext, useContext, useEffect, useRef, useState } from 'react';
import { InfiniteData, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../hooks/useAuth';
import type { Conversation, Message } from '../types/messaging';

interface WebSocketContextValue {
  isConnected: boolean;
  totalUnreadCount: number | null;
  typingUserIds: string[];
  sendTyping: (conversationId: string, isTyping: boolean) => void;
  setActiveConversationId: (id: string | null) => void;
}

const WebSocketContext = createContext<WebSocketContextValue | null>(null);

export function WebSocketProvider({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient();
  const { profile } = useAuth();
  const clientRef = useRef<Client | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [typingUserIds, setTypingUserIds] = useState<string[]>([]);
  const [totalUnreadCount, setTotalUnreadCount] = useState<number | null>(null);
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const typingTimers = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  // Single STOMP client for the entire session
  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (!token) return;

    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws', null, { transports: ['websocket'] }),
      connectHeaders: { Authorization: `Bearer ${token}` },
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

  // Subscribe to conversation messages and typing when a chat is open
  useEffect(() => {
    const client = clientRef.current;
    if (!client || !isConnected || !activeConversationId) return;

    const msgSub = client.subscribe(
      `/topic/conversations/${activeConversationId}`,
      (frame) => {
        const message: Message = JSON.parse(frame.body);
        if (message.senderId === profile?.user.id) return;

        queryClient.setQueryData<InfiniteData<Message[]>>(
          ['messages', activeConversationId],
          (old) => {
            if (!old) return old;
            const [firstPage, ...rest] = old.pages;
            return { ...old, pages: [[message, ...(firstPage ?? [])], ...rest] };
          }
        );

        queryClient.setQueryData<Conversation[]>(['conversations'], (old) =>
          old?.map((conv) =>
            conv.id === activeConversationId ? { ...conv, lastMessage: message } : conv
          ) ?? []
        );
      }
    );

    const typingSub = client.subscribe(
      `/topic/conversations/${activeConversationId}/typing`,
      (frame) => {
        const { userId, isTyping } = JSON.parse(frame.body) as { userId: string; isTyping: boolean };

        setTypingUserIds((prev) => {
          const without = prev.filter((id) => id !== userId);
          return isTyping ? [...without, userId] : without;
        });

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
      setTypingUserIds([]);
    };
  }, [activeConversationId, isConnected, queryClient, profile?.user.id]);

  // Global unread-count subscription (nav badge)
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
        const updated = queryClient.setQueryData<Conversation[]>(['conversations'], (old) =>
          old?.map((conv) => conv.id === convId ? { ...conv, unreadCount } : conv) ?? []
        );
        if (updated) {
          setTotalUnreadCount(updated.reduce((sum, c) => sum + (c.unreadCount ?? 0), 0));
        }
      }
    );

    return () => sub.unsubscribe();
  }, [isConnected, profile?.user.id, queryClient]);

  const sendTyping = (convId: string, isTyping: boolean) => {
    const client = clientRef.current;
    if (!client?.connected) return;
    client.publish({
      destination: '/app/chat.typing',
      body: JSON.stringify({ conversationId: convId, isTyping }),
    });
  };

  return (
    <WebSocketContext.Provider value={{ isConnected, totalUnreadCount, typingUserIds, sendTyping, setActiveConversationId }}>
      {children}
    </WebSocketContext.Provider>
  );
}

export function useWebSocketContext() {
  const ctx = useContext(WebSocketContext);
  if (!ctx) throw new Error('useWebSocketContext must be used within WebSocketProvider');
  return ctx;
}

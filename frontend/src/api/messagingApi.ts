import { api } from './client';
import type { Conversation, CreateConversationPayload, Message, SendMessagePayload } from '../types/messaging';

const BASE = '/api/v1/conversations';

export const messagingApi = {
  getConversations: async (page = 0, size = 20): Promise<Conversation[]> => {
    const { data } = await api.get(BASE, { params: { page, size } });
    return data.data;
  },

  createConversation: async (payload: CreateConversationPayload): Promise<Conversation> => {
    const { data } = await api.post(BASE, payload);
    return data.data;
  },

  getMessages: async (conversationId: string, cursor?: string, limit = 30): Promise<Message[]> => {
    const params: Record<string, string | number> = { limit };
    if (cursor !== undefined) params.cursor = cursor;
    const { data } = await api.get(`${BASE}/${conversationId}/messages`, { params });
    return data.data;
  },

  sendMessage: async (conversationId: string, payload: SendMessagePayload): Promise<Message> => {
    const { data } = await api.post(`${BASE}/${conversationId}/messages`, payload);
    return data.data;
  },

  markRead: async (conversationId: string, messageId: string): Promise<void> => {
    await api.put(`${BASE}/${conversationId}/read`, { messageId });
  },

  addGroupMembers: async (conversationId: string, memberIds: string[]): Promise<void> => {
    await api.post(`${BASE}/${conversationId}/members`, { memberIds });
  },

  leaveConversation: async (conversationId: string): Promise<void> => {
    await api.delete(`${BASE}/${conversationId}/members/me`);
  },
};

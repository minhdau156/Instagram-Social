export type MessageType = 'TEXT' | 'IMAGE' | 'VIDEO' | 'POST_SHARE';

export type MessageStatus = 'SENT' | 'DELIVERED' | 'READ';

export interface ConversationMember {
    userId: string;
    username: string;
    avatarUrl: string | null;
    role: 'OWNER' | 'MEMBER';
}

export interface Message {
    id: string;
    conversationId: string;
    senderId: string;
    senderUsername: string;
    senderAvatarUrl: string | null;
    content: string | null;
    messageType: MessageType;
    mediaUrl: string | null;
    sharedPostId: string | null;
    status: MessageStatus | null;
    createdAt: string;
}

export interface Conversation {
    id: string;
    name: string | null;
    isGroup: boolean;
    lastMessage: Message | null;
    unreadCount: number;
    createdAt: string;
}

export interface SendMessagePayload {
    content: string | null;
    messageType: MessageType;
    mediaUrl?: string;
    sharedPostId?: string;
}

export interface CreateConversationPayload {
    participantIds: string[];
    name?: string;
    isGroup: boolean;
}
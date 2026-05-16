export type NotificationType = 'LIKE_POST' | 'LIKE_COMMENT' | 'COMMENT_POST' | 'REPLY_COMMENT' | 'FOLLOW' | 'FOLLOW_REQUEST' | 'FOLLOW_ACCEPTED' | 'MENTION_POST' | 'MENTION_COMMENT' | 'DIRECT_MESSAGE' | 'GROUP_MESSAGE' | 'POST_SHARED';

export type EntityType = 'POST' | 'COMMENT' | 'FOLLOW' | 'MESSAGE';

export interface Notification {
    id: string;
    type: NotificationType;
    entityType: EntityType;
    entityId: string | null;
    actorUsername: string | null;
    actorAvatarUrl: string | null;
    isRead: boolean;
    createdAt: string;
}

export interface NotificationSettings {
    likesEnabled: boolean;
    commentsEnabled: boolean;
    followsEnabled: boolean;
    messagesEnabled: boolean;
    pushEnabled: boolean;
}

export interface RegisterDeviceTokenPayload {
    token: string;
    platform: 'FCM' | 'APNS';
}

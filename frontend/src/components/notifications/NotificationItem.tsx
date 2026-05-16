import { useNavigate } from "react-router-dom";
import { Notification } from "../../types/notification";
import { ListItemButton, ListItemAvatar, Avatar, ListItemText, Box } from "@mui/material";
import { formatDistanceToNow } from "date-fns";

interface NotificationProps {
    notification: Notification;
    onRead: (id: string) => void;
}

function getNotificationText(notification: Notification): string {
    const actor = notification.actorUsername ?? 'Someone';
    switch (notification.type) {
        case 'LIKE_POST':
            return `${actor} liked your photo`;
        case 'LIKE_COMMENT':
            return `${actor} liked your comment`;
        case 'COMMENT_POST':
            return `${actor} commented on your photo`;
        case 'REPLY_COMMENT':
            return `${actor} replied to your comment`;
        case 'FOLLOW':
            return `${actor} started following you`;
        case 'FOLLOW_REQUEST':
            return `${actor} requested to follow you`;
        case 'FOLLOW_ACCEPTED':
            return `${actor} accepted your follow request`;
        case 'MENTION_POST':
            return `${actor} mentioned you in a post`;
        case 'MENTION_COMMENT':
            return `${actor} mentioned you in a comment`;
        case 'DIRECT_MESSAGE':
            return `${actor} sent you a message`;
        case 'GROUP_MESSAGE':
            return `${actor} sent you a message`;
        case 'POST_SHARED':
            return `${actor} shared your photo`;
        default:
            return 'You have a new notification';
    }
}

function getNotificationPath(notification: Notification): string {
    switch (notification.entityType) {
        case 'POST':
            return notification.entityId ? `/posts/${notification.entityId}` : '/';
        case 'FOLLOW':
            return notification.actorUsername ? `/profile/${notification.actorUsername}` : '/';
        case 'MESSAGE':
            return '/messages';
        default:
            return '/';
    }
}

export const NotificationItem = ({ notification, onRead }: NotificationProps) => {
    const navigate = useNavigate();
    return (
        <ListItemButton onClick={() => {
            onRead(notification.id);
            navigate(getNotificationPath(notification));
        }} sx={{
            bgcolor: notification.isRead ? 'transparent' : 'action.hover'
        }}>
            <ListItemAvatar>
                <Avatar src={notification.actorAvatarUrl || undefined} alt={notification.actorUsername || 'Unknown'} />
            </ListItemAvatar>
            <ListItemText
                primary={getNotificationText(notification)}
                secondary={formatDistanceToNow(new Date(notification.createdAt), { addSuffix: true })}
                primaryTypographyProps={{
                    fontWeight: notification.isRead ? 400 : 600
                }}
            />

            {!notification.isRead && (
                <Box sx={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    bgcolor: 'primary.main'
                }} />
            )}
        </ListItemButton>
    );
};
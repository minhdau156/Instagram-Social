import { Conversation } from "../../types/messaging";
import { Avatar, Badge, Box, ListItemAvatar, ListItemButton, ListItemText, Typography } from "@mui/material";
import GroupIcon from "@mui/icons-material/Group"

interface ConversationListItemProps {
    conversation: Conversation,
    currentUserId?: string,
    isSelected: boolean,
    isClick: boolean,
    onClick: () => void
}

export function ConversationListItem({ conversation, isSelected, onClick, isClick }: ConversationListItemProps) {
    const displayName = conversation.isGroup
        ? (conversation.name ?? 'Group')
        : (conversation.eachOtherName ?? 'Unknown');


    return (
        <ListItemButton selected={isSelected} onClick={onClick}>
            <ListItemAvatar>
                <Badge
                    badgeContent={isClick ? 0 : conversation.unreadCount}
                    color="primary"
                    overlap="circular"
                    invisible={conversation.unreadCount === 0}
                >
                    {conversation.isGroup ? (
                        <Avatar><GroupIcon /></Avatar>
                    ) : (
                        <Avatar src=" https://png.pngtree.com/png-clipart/20210608/ourlarge/pngtree-dark-gray-simple-avatar-png-image_3418404.jpg"></Avatar>
                    )}
                </Badge>
            </ListItemAvatar>
            <ListItemText
                primary={displayName}
                secondary={conversation.lastMessage?.content ?? 'No messages yet'}
                secondaryTypographyProps={{ noWrap: true }}
            />
            <Box sx={{
                ml: 'auto', alignSelf: 'flex-start'
            }}>
                <Typography variant="caption" color="text.secondary">
                    {new Date(conversation.lastMessage?.createdAt || '').toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </Typography>
            </Box>
        </ListItemButton>
    )
}
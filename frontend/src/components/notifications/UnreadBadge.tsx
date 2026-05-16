import { Badge, IconButton } from "@mui/material";
import NotificationsOutlinedIcon from '@mui/icons-material/NotificationsOutlined';
import React from "react";

export interface UnreadBadgeProps {
    onClick: (event: React.MouseEvent<HTMLButtonElement>) => void;
    unreadCount: number;
}

export const UnreadBadge = ({ onClick, unreadCount }: UnreadBadgeProps) => {
    return (
        <IconButton color="inherit" onClick={onClick}>
            <Badge badgeContent={unreadCount} color="error" invisible={unreadCount === 0}>
                <NotificationsOutlinedIcon />
            </Badge>
        </IconButton>
    );
};
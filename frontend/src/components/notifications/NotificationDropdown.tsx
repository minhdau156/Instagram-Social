import { Box, Button, Divider, List, Popover, Skeleton, Typography } from "@mui/material";
import { useNotifications } from "../../hooks/notification/useNotifications";
import { NotificationItem } from "./NotificationItem";
import { useNavigate } from "react-router-dom";

interface NotificationDropdownProps {
    anchorEl: HTMLButtonElement | null;
    onClose: () => void;
}

export const NotificationDropdown = ({ anchorEl, onClose }: NotificationDropdownProps) => {
    const { notifications, isLoading, markRead, markAllRead } = useNotifications();
    const notificationList = notifications.slice(0, 10);
    const navigate = useNavigate();
    return (
        <Popover
            open={Boolean(anchorEl)}
            anchorEl={anchorEl}
            anchorOrigin={{
                vertical: 'bottom',
                horizontal: 'right'
            }}
            transformOrigin={{
                vertical: 'top',
                horizontal: 'right'
            }}
            PaperProps={{
                sx: { width: 360, maxHeight: 480, mt: 1 }
            }}
            onClose={onClose}
        >
            <Box sx={{ display: 'flex', justifyContent: 'space-between', p: 2 }}>
                <Typography variant="h6">
                    Notifications
                </Typography>
                <Button variant="text" size="small" onClick={() => markAllRead.mutate()}>
                    Mark all as read
                </Button>
            </Box>
            <Divider />
            <List sx={{ overflow: 'auto', maxHeight: 480 }}>
                {isLoading && (
                    Array.from({ length: 3 }).map((_, index) => (
                        <Skeleton
                            key={index}
                            variant="rectangular"
                            height={60}
                            sx={{ margin: 1, borderRadius: 1 }}
                        />
                    ))
                )}
                {notificationList.length === 0 && !isLoading && (
                    <Typography
                        variant="body2"
                        sx={{ textAlign: 'center', color: 'text.secondary', p: 2 }}
                    >
                        No notifications yet
                    </Typography>
                )}
                {notificationList.map((notification) => (
                    <NotificationItem
                        key={notification.id}
                        notification={notification}
                        onRead={(id) => {
                            markRead.mutate({ id });
                            onClose();
                        }}
                    />
                ))}

            </List>
            <Divider />
            <Button fullWidth variant="text" onClick={() => { navigate(`/notifications`); onClose(); }}>See all</Button>
        </Popover >
    );
}
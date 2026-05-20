import { useEffect, useRef } from "react";
import { useNotifications } from "../../hooks/notification/useNotifications";
import { Box, Button, CircularProgress, Divider, List, ListSubheader, Skeleton, Typography } from "@mui/material";
import { NotificationItem } from "../../components/notifications/NotificationItem";


export default function NotificationsPage() {
    const { notifications, isLoading, isFetchingNextPage, fetchNextPage, hasNextPage, markRead, markAllRead } = useNotifications();
    const sentinelRef = useRef<HTMLDivElement>(null);

    const now = Date.now();
    const DAY = 24 * 60 * 60 * 1000;
    const today = notifications.filter(n => now - new Date(n.createdAt).getTime() < DAY);
    const week = notifications.filter(n => {
        const age = now - new Date(n.createdAt).getTime();
        return age >= DAY && age < 7 * DAY;
    });
    const earlier = notifications.filter(n => now - new Date(n.createdAt).getTime() >= 7 * DAY);

    useEffect(() => {
        if (!hasNextPage || isFetchingNextPage) return;

        const observer = new IntersectionObserver((entries) => {
            const firstEntry = entries[0];
            if (firstEntry.isIntersecting && hasNextPage && !isFetchingNextPage) {
                fetchNextPage();
            }
        }, {
            rootMargin: '200px',
            threshold: 0
        });

        if (sentinelRef.current) {
            observer.observe(sentinelRef.current);
        }

        return () => observer.disconnect();
    }, [hasNextPage, isFetchingNextPage, fetchNextPage]);
    return (
        <Box sx={{ maxWidth: "600px", mx: "auto", px: { xs: 1, sm: 2 }, py: 2 }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
                <Typography variant="h5" fontWeight={600}>Notifications</Typography>
                <Button variant="outlined" size="small" onClick={() => markAllRead.mutate(undefined)}>Mark all as read</Button>
            </Box>
            {isLoading && (
                Array.from({ length: 5 }).map((_, index) => (
                    <Skeleton key={index} variant="rectangular" height={72} sx={{ mb: 1, borderRadius: 1 }} />
                ))
            )}

            {!isLoading && notifications?.length === 0 && (
                <Box sx={{ py: 8, textAlign: "center" }}>
                    <Typography color="text.secondary">You're all caught up!</Typography>
                </Box>
            )}
            <List>
                {today.length > 0 && (
                    <>
                        <ListSubheader sx={{ bgcolor: "background.paper", fontWeight: 600 }}>Today</ListSubheader>
                        {today.map((notification) => (
                            <NotificationItem key={notification.id} notification={notification} onRead={() => markRead.mutate({ id: notification.id })} />
                        ))}
                        {(week.length > 0 || earlier.length > 0) && <Divider />}
                    </>
                )}
                {week.length > 0 && (
                    <>
                        <ListSubheader sx={{ bgcolor: "background.paper", fontWeight: 600 }}>This Week</ListSubheader>
                        {week.map((notification) => (
                            <NotificationItem key={notification.id} notification={notification} onRead={() => markRead.mutate({ id: notification.id })} />
                        ))}
                        {earlier.length > 0 && <Divider />}
                    </>
                )}
                {earlier.length > 0 && (
                    <>
                        <ListSubheader sx={{ bgcolor: "background.paper", fontWeight: 600 }}>Earlier</ListSubheader>
                        {earlier.map((notification) => (
                            <NotificationItem key={notification.id} notification={notification} onRead={() => markRead.mutate({ id: notification.id })} />
                        ))}
                    </>
                )}

            </List>
            <Box ref={sentinelRef} sx={{ height: 20, display: "flex", justifyContent: "center", alignItems: "center", mb: 2 }}>
                {isFetchingNextPage && <CircularProgress size={24} />}
            </Box>

        </Box>
    );
};
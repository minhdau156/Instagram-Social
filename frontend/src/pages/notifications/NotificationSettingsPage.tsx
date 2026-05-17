import { useNotificationSettings } from "../../hooks/notification/useNotificationSettings";
import { useEffect, useState } from "react";
import { Box, List, ListItem, ListItemText, Skeleton, Switch, Typography } from "@mui/material";
import type { NotificationSettings } from "../../types/notification";

export default function NotificationSettingsPage() {
    const { settings, isLoading, updateSettings, isUpdating } = useNotificationSettings();
    const [localSettings, setLocalSettings] = useState<NotificationSettings | null>(null);
    useEffect(() => {
        if (settings) {
            setLocalSettings(settings);
        }
    }, [settings]);
    const SETTINGS_ROWS: { field: keyof NotificationSettings; label: string }[] = [
        { field: 'likesEnabled', label: 'Likes' },
        { field: 'commentsEnabled', label: 'Comments' },
        { field: 'followsEnabled', label: 'Follows' },
        { field: 'messagesEnabled', label: 'Messages' },
        { field: 'pushEnabled', label: 'Push Notifications' },
    ];



    const handleToggle = (field: keyof NotificationSettings) => {
        if (!localSettings) return;
        const newValue = !localSettings[field];
        setLocalSettings((prev) => {
            if (!prev) return null;
            return {
                ...prev,
                [field]: newValue,
            }
        });
        updateSettings.mutate({
            likesEnabled: field === "likesEnabled" ? newValue : localSettings.likesEnabled,
            commentsEnabled: field === "commentsEnabled" ? newValue : localSettings.commentsEnabled,
            followsEnabled: field === "followsEnabled" ? newValue : localSettings.followsEnabled,
            messagesEnabled: field === "messagesEnabled" ? newValue : localSettings.messagesEnabled,
            pushEnabled: field === "pushEnabled" ? newValue : localSettings.pushEnabled,
        });
    };
    return (
        <Box sx={{ maxWidth: '500px', mx: 'auto', py: 2, px: { xs: 1, sm: 2 } }}>
            <Typography variant="h5" fontWeight={600} mb={3}>
                Notification Settings
            </Typography>
            {isLoading && (
                Array.from({ length: 5 }).map((_, index) => (
                    <Skeleton key={index} variant="rectangular" height={56} sx={{ borderRadius: 1 }} />
                ))
            )}
            <List disablePadding>
                {SETTINGS_ROWS.map(({ field, label }) => (
                    <ListItem key={field}>
                        <ListItemText primary={label} />
                        <Switch
                            checked={localSettings?.[field] || false}
                            onChange={() => handleToggle(field)}
                            disabled={isUpdating}
                        />
                    </ListItem>
                ))}
            </List>
        </Box>
    );
};
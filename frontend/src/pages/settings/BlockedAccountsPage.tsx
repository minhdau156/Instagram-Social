import { useState } from 'react';
import {
    Alert,
    Avatar,
    Box,
    Button,
    Divider,
    List,
    ListItem,
    ListItemAvatar,
    ListItemText,
    Skeleton,
    Typography,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useNavigate } from 'react-router-dom';
import { formatDistanceToNow } from 'date-fns';
import { useBlockedUsers } from '../../hooks/moderation/useBlockedUsers';
import { useUnblockUser } from '../../hooks/moderation/useUnblockUser';
import type { UserBlock } from '../../types/moderation';

export default function BlockedAccountsPage() {
    const navigate = useNavigate();
    const { blockedUsers, isLoading, isError } = useBlockedUsers();
    const { mutate: unblock } = useUnblockUser();
    const [pendingUsername, setPendingUsername] = useState<string | null>(null);

    const handleUnblock = (username: string) => {
        setPendingUsername(username);
        unblock(username, { onSettled: () => setPendingUsername(null) });
    };

    const formatBlockedAt = (blockedAt: string) => {
        try {
            return `Blocked ${formatDistanceToNow(new Date(blockedAt), { addSuffix: true })}`;
        } catch {
            return 'Blocked recently';
        }
    };

    return (
        <Box sx={{ maxWidth: 600, mx: 'auto', py: 3, px: { xs: 2, sm: 3 } }}>
            <Button
                variant="text"
                startIcon={<ArrowBackIcon />}
                onClick={() => navigate(-1)}
                sx={{ mb: 2 }}
            >
                Back to Settings
            </Button>

            <Typography variant="h5" fontWeight={600} mb={1}>
                Blocked Accounts
            </Typography>
            <Typography variant="body2" color="text.secondary" mb={3}>
                People you've blocked can't see your posts or find your profile. They won't be notified when you unblock them.
            </Typography>

            {isLoading && (
                <List disablePadding>
                    {Array.from({ length: 5 }).map((_, i) => (
                        <ListItem key={i} secondaryAction={<Skeleton variant="rectangular" width={72} height={30} sx={{ borderRadius: 1 }} />}>
                            <ListItemAvatar>
                                <Skeleton variant="circular" width={40} height={40} animation="wave" />
                            </ListItemAvatar>
                            <ListItemText
                                primary={<Skeleton variant="rectangular" width={120} height={14} animation="wave" sx={{ mb: 0.5, borderRadius: 0.5 }} />}
                                secondary={<Skeleton variant="rectangular" width={80} height={12} animation="wave" sx={{ borderRadius: 0.5 }} />}
                            />
                        </ListItem>
                    ))}
                </List>
            )}

            {isError && (
                <Alert severity="error">
                    Could not load blocked accounts. Please try again.
                </Alert>
            )}

            {!isLoading && !isError && blockedUsers.length === 0 && (
                <Box sx={{ py: 6 }}>
                    <Typography color="text.secondary" align="center">
                        You haven't blocked anyone yet.
                    </Typography>
                </Box>
            )}

            {!isLoading && !isError && blockedUsers.length > 0 && (
                <List disablePadding>
                    {blockedUsers.map((user: UserBlock, index: number) => (
                        <Box key={user.blockedUserId}>
                            {index > 0 && <Divider component="li" />}
                            <ListItem
                                secondaryAction={
                                    <Button
                                        variant="outlined"
                                        size="small"
                                        disabled={pendingUsername === user.username}
                                        onClick={() => handleUnblock(user.username)}
                                    >
                                        Unblock
                                    </Button>
                                }
                            >
                                <ListItemAvatar>
                                    <Avatar src={user.avatarUrl ?? undefined}>
                                        {user.username[0].toUpperCase()}
                                    </Avatar>
                                </ListItemAvatar>
                                <ListItemText
                                    primary={
                                        <Typography variant="body2" fontWeight={500}>
                                            {user.username}
                                        </Typography>
                                    }
                                    secondary={
                                        <>
                                            {user.fullName && (
                                                <Typography variant="caption" display="block">
                                                    {user.fullName}
                                                </Typography>
                                            )}
                                            <Typography variant="caption" display="block">
                                                {formatBlockedAt(user.blockedAt)}
                                            </Typography>
                                        </>
                                    }
                                />
                            </ListItem>
                        </Box>
                    ))}
                </List>
            )}
        </Box>
    );
}

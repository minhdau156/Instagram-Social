import { useState } from 'react';
import {
    Avatar, Button, CircularProgress, Dialog, DialogActions, DialogContent,
    DialogTitle, List, ListItemAvatar, ListItemButton, ListItemText, TextField, Typography,
} from '@mui/material';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { usersApi } from '../../api/usersApi';
import { messagingApi } from '../../api/messagingApi';
import { User } from '../../types/user';
import { useDebounce } from '../../hooks/useDebounce';

interface NewConversationDialogProps {
    open: boolean;
    onClose: () => void;
}

export function NewConversationDialog({ open, onClose }: NewConversationDialogProps) {
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedUser, setSelectedUser] = useState<User | null>(null);
    const [isCreating, setIsCreating] = useState(false);

    const debouncedTerm = useDebounce(searchTerm, 300);

    const { data: results = [], isLoading } = useQuery({
        queryKey: ['userSearch', debouncedTerm],
        queryFn: () => usersApi.search(debouncedTerm),
        enabled: debouncedTerm.length > 0,
    });

    const handleStart = async () => {
        if (!selectedUser) return;
        setIsCreating(true);
        try {
            const conversation = await messagingApi.createConversation({
                participantIds: [selectedUser.id],
                isGroup: false,
            });
            queryClient.invalidateQueries({ queryKey: ['conversations'] });
            navigate(`/messages/${conversation.id}`);
            onClose();
        } finally {
            setIsCreating(false);
        }
    };

    const handleClose = () => {
        setSearchTerm('');
        setSelectedUser(null);
        onClose();
    };

    return (
        <Dialog open={open} onClose={handleClose} fullWidth maxWidth="xs">
            <DialogTitle>New Message</DialogTitle>
            <DialogContent>
                <TextField
                    fullWidth
                    autoFocus
                    placeholder="Search people..."
                    variant="outlined"
                    size="small"
                    sx={{ mb: 2 }}
                    value={searchTerm}
                    onChange={e => { setSearchTerm(e.target.value); setSelectedUser(null); }}
                />
                {isLoading ? (
                    <CircularProgress size={24} sx={{ display: 'block', mx: 'auto' }} />
                ) : debouncedTerm && results.length === 0 ? (
                    <Typography color="text.secondary" textAlign="center">No users found</Typography>
                ) : (
                    <List disablePadding>
                        {results.map(user => (
                            <ListItemButton
                                key={user.id}
                                selected={selectedUser?.id === user.id}
                                onClick={() => setSelectedUser(user)}
                            >
                                <ListItemAvatar>
                                    <Avatar src={user.avatarUrl ?? undefined}>
                                        {user.username[0].toUpperCase()}
                                    </Avatar>
                                </ListItemAvatar>
                                <ListItemText primary={user.username} secondary={user.fullName} />
                            </ListItemButton>
                        ))}
                    </List>
                )}
            </DialogContent>
            <DialogActions>
                <Button variant="text" onClick={handleClose}>Cancel</Button>
                <Button
                    variant="contained"
                    disabled={!selectedUser || isCreating}
                    onClick={handleStart}
                >
                    {isCreating ? <CircularProgress size={20} /> : 'Start Chat'}
                </Button>
            </DialogActions>
        </Dialog>
    );
}

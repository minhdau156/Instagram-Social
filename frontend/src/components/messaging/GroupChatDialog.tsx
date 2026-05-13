import { useState } from 'react';
import {
    Avatar, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent,
    DialogTitle, List, ListItemAvatar, ListItemButton, ListItemText, TextField, Typography,
} from '@mui/material';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { usersApi } from '../../api/usersApi';
import { messagingApi } from '../../api/messagingApi';
import { User } from '../../types/user';
import { useDebounce } from '../../hooks/useDebounce';

interface GroupChatDialogProps {
    open: boolean;
    onClose: () => void;
}

export function GroupChatDialog({ open, onClose }: GroupChatDialogProps) {
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [groupName, setGroupName] = useState('');
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedUsers, setSelectedUsers] = useState<User[]>([]);
    const [isCreating, setIsCreating] = useState(false);

    const debouncedTerm = useDebounce(searchTerm, 300);

    const { data: results = [], isLoading } = useQuery({
        queryKey: ['userSearch', debouncedTerm],
        queryFn: () => usersApi.search(debouncedTerm),
        enabled: debouncedTerm.length > 0,
    });

    const selectedIds = new Set(selectedUsers.map(u => u.id));
    const filteredResults = results.filter(u => !selectedIds.has(u.id));

    const handleSelect = (user: User) => {
        setSelectedUsers(prev => [...prev, user]);
        setSearchTerm('');
    };

    const handleRemove = (userId: string) => {
        setSelectedUsers(prev => prev.filter(u => u.id !== userId));
    };

    const handleCreate = async () => {
        if (!groupName.trim() || selectedUsers.length < 2) return;
        setIsCreating(true);
        try {
            const conversation = await messagingApi.createConversation({
                participantIds: selectedUsers.map(u => u.id),
                name: groupName.trim(),
                isGroup: true,
            });
            queryClient.invalidateQueries({ queryKey: ['conversations'] });
            navigate(`/messages/${conversation.id}`);
            onClose();
        } finally {
            setIsCreating(false);
        }
    };

    const handleClose = () => {
        setGroupName('');
        setSearchTerm('');
        setSelectedUsers([]);
        onClose();
    };

    return (
        <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
            <DialogTitle>New Group</DialogTitle>
            <DialogContent>
                <TextField
                    fullWidth
                    label="Group name"
                    variant="outlined"
                    sx={{ mb: 2 }}
                    value={groupName}
                    onChange={e => setGroupName(e.target.value)}
                />

                {selectedUsers.length > 0 && (
                    <Box display="flex" flexWrap="wrap" gap={0.5} mb={1}>
                        {selectedUsers.map(user => (
                            <Chip
                                key={user.id}
                                avatar={<Avatar src={user.avatarUrl ?? undefined}>{user.username[0].toUpperCase()}</Avatar>}
                                label={user.username}
                                onDelete={() => handleRemove(user.id)}
                                size="small"
                            />
                        ))}
                    </Box>
                )}

                <TextField
                    fullWidth
                    placeholder="Search people..."
                    variant="outlined"
                    size="small"
                    sx={{ mb: 1 }}
                    value={searchTerm}
                    onChange={e => setSearchTerm(e.target.value)}
                />

                {isLoading ? (
                    <CircularProgress size={24} sx={{ display: 'block', mx: 'auto' }} />
                ) : debouncedTerm && filteredResults.length === 0 ? (
                    <Typography color="text.secondary" textAlign="center">No users found</Typography>
                ) : (
                    <List disablePadding>
                        {filteredResults.map(user => (
                            <ListItemButton key={user.id} onClick={() => handleSelect(user)}>
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
                    disabled={!groupName.trim() || selectedUsers.length < 2 || isCreating}
                    onClick={handleCreate}
                >
                    {isCreating ? <CircularProgress size={20} /> : 'Create Group'}
                </Button>
            </DialogActions>
        </Dialog>
    );
}

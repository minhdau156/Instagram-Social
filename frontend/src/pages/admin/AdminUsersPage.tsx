import { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
    Avatar, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
    IconButton, Link, Menu, MenuItem, Paper, Select, Skeleton, Table, TableBody,
    TableCell, TableContainer, TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import PersonIcon from '@mui/icons-material/Person';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import { format } from 'date-fns';
import { useAdminUsers } from '../../hooks/admin/useAdminUsers';
import { useSuspendUser } from '../../hooks/admin/useSuspendUser';
import { useUnsuspendUser } from '../../hooks/admin/useUnsuspendUser';
import type { AccountStatus, AdminUser } from '../../types/moderation';

type ChipColor = 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning';

const accountStatusColor = (status: AccountStatus): ChipColor => {
    switch (status) {
        case 'ACTIVE': return 'success';
        case 'SUSPENDED': return 'error';
        case 'PENDING_VERIFICATION': return 'warning';
        default: return 'default';
    }
};

export default function AdminUsersPage() {
    const [searchInput, setSearchInput] = useState('');
    const [debouncedSearch, setDebouncedSearch] = useState('');
    const [statusFilter, setStatusFilter] = useState('');
    const [page, setPage] = useState(0);
    const [menuAnchor, setMenuAnchor] = useState<{ el: HTMLElement; user: AdminUser } | null>(null);
    const [suspendTarget, setSuspendTarget] = useState<AdminUser | null>(null);
    const [suspendReason, setSuspendReason] = useState('');

    const { mutate: suspendUser, isPending: isSuspending } = useSuspendUser();
    const { mutate: unsuspendUser } = useUnsuspendUser();

    useEffect(() => {
        const t = setTimeout(() => { setDebouncedSearch(searchInput); setPage(0); }, 300);
        return () => clearTimeout(t);
    }, [searchInput]);

    const filters = {
        username: debouncedSearch || undefined,
        status: statusFilter || undefined,
    };

    const { users, isLoading } = useAdminUsers(filters, page, 20);

    const handleSuspend = () => {
        if (!suspendTarget) return;
        suspendUser(
            { id: suspendTarget.id, payload: { reason: suspendReason } },
            { onSuccess: () => { setSuspendTarget(null); setSuspendReason(''); } }
        );
    };

    const handleUnsuspend = (user: AdminUser) => {
        unsuspendUser(user.id);
        setMenuAnchor(null);
    };

    const handleOpenSuspendDialog = (user: AdminUser) => {
        setSuspendTarget(user);
        setSuspendReason('');
        setMenuAnchor(null);
    };

    return (
        <Box sx={{ p: 3 }}>
            <Typography variant="h5" fontWeight={600} mb={2}>User Management</Typography>
            <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
                <TextField
                    size="small"
                    placeholder="Search by username..."
                    value={searchInput}
                    onChange={e => setSearchInput(e.target.value)}
                    sx={{ minWidth: 240 }}
                />
                <Select
                    size="small"
                    value={statusFilter}
                    onChange={e => { setStatusFilter(e.target.value); setPage(0); }}
                    displayEmpty
                    sx={{ minWidth: 160 }}
                >
                    <MenuItem value="">All</MenuItem>
                    <MenuItem value="ACTIVE">Active</MenuItem>
                    <MenuItem value="SUSPENDED">Suspended</MenuItem>
                    <MenuItem value="DEACTIVATED">Deactivated</MenuItem>
                </Select>
            </Box>
            <TableContainer component={Paper}>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell>Avatar</TableCell>
                            <TableCell>Username</TableCell>
                            <TableCell>Email</TableCell>
                            <TableCell>Full Name</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell>Verified</TableCell>
                            <TableCell>Joined Date</TableCell>
                            <TableCell>Actions</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {isLoading && Array.from({ length: 10 }).map((_, i) => (
                            <TableRow key={i}>
                                <TableCell colSpan={8}>
                                    <Skeleton variant="rectangular" height={52} animation="wave" />
                                </TableCell>
                            </TableRow>
                        ))}
                        {!isLoading && users.length === 0 && (
                            <TableRow>
                                <TableCell colSpan={8}>
                                    <Typography color="text.secondary" align="center" sx={{ py: 4 }}>
                                        No users found matching the current filters.
                                    </Typography>
                                </TableCell>
                            </TableRow>
                        )}
                        {!isLoading && users.map(user => (
                            <TableRow key={user.id}>
                                <TableCell>
                                    <Avatar sx={{ width: 32, height: 32 }}>
                                        <PersonIcon fontSize="small" />
                                    </Avatar>
                                </TableCell>
                                <TableCell>
                                    <Link component={RouterLink} to={`/${user.username}/bio`} underline="hover">
                                        {user.username}
                                    </Link>
                                </TableCell>
                                <TableCell>{user.email}</TableCell>
                                <TableCell>{user.fullName ?? '—'}</TableCell>
                                <TableCell>
                                    <Chip
                                        label={user.accountStatus}
                                        size="small"
                                        color={accountStatusColor(user.accountStatus)}
                                    />
                                </TableCell>
                                <TableCell>
                                    {user.isVerified
                                        ? <CheckCircleIcon color="primary" fontSize="small" />
                                        : '—'
                                    }
                                </TableCell>
                                <TableCell>{format(new Date(user.createdAt), 'MMM d, yyyy')}</TableCell>
                                <TableCell>
                                    <IconButton
                                        size="small"
                                        onClick={e => setMenuAnchor({ el: e.currentTarget, user })}
                                    >
                                        <MoreVertIcon fontSize="small" />
                                    </IconButton>
                                </TableCell>
                            </TableRow>
                        ))}
                    </TableBody>
                </Table>
            </TableContainer>
            <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mt: 2 }}>
                <Button size="small" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
                    Previous
                </Button>
                <Button size="small" disabled={users.length < 20} onClick={() => setPage(p => p + 1)}>
                    Next
                </Button>
            </Box>

            <Menu
                anchorEl={menuAnchor?.el}
                open={menuAnchor !== null}
                onClose={() => setMenuAnchor(null)}
            >
                <MenuItem
                    onClick={() => setMenuAnchor(null)}
                    component={RouterLink}
                    to={`/${menuAnchor?.user.username}/bio`}
                >
                    View Profile
                </MenuItem>
                {menuAnchor?.user.accountStatus === 'ACTIVE' && (
                    <MenuItem onClick={() => handleOpenSuspendDialog(menuAnchor.user)}>
                        Suspend User
                    </MenuItem>
                )}
                {menuAnchor?.user.accountStatus === 'SUSPENDED' && (
                    <MenuItem onClick={() => handleUnsuspend(menuAnchor.user)}>
                        Unsuspend User
                    </MenuItem>
                )}
            </Menu>

            <Dialog
                open={suspendTarget !== null}
                onClose={() => setSuspendTarget(null)}
                maxWidth="sm"
                fullWidth
            >
                <DialogTitle>Suspend @{suspendTarget?.username}?</DialogTitle>
                <DialogContent>
                    <TextField
                        multiline
                        rows={3}
                        label="Reason"
                        required
                        fullWidth
                        value={suspendReason}
                        onChange={e => setSuspendReason(e.target.value)}
                        sx={{ mt: 1 }}
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setSuspendTarget(null)} disabled={isSuspending}>
                        Cancel
                    </Button>
                    <Button
                        variant="contained"
                        color="error"
                        disabled={suspendReason.trim() === '' || isSuspending}
                        onClick={handleSuspend}
                    >
                        Suspend
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}

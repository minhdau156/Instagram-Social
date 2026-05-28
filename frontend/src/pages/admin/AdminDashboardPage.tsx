import type { ReactNode } from 'react';
import { Box, Button, Card, CardContent, Skeleton, Typography } from '@mui/material';
import PeopleIcon from '@mui/icons-material/People';
import ReportIcon from '@mui/icons-material/Report';
import BlockIcon from '@mui/icons-material/Block';
import TaskAltIcon from '@mui/icons-material/TaskAlt';
import { useNavigate } from 'react-router-dom';
import { useAdminReports } from '../../hooks/admin/useAdminReports';
import { useAdminUsers } from '../../hooks/admin/useAdminUsers';
import { PermissionGate } from '../../components/common/PermissionGate';

export default function AdminDashboardPage() {
    const navigate = useNavigate();
    const { reports: pendingReports, isLoading: loadingPending } = useAdminReports('PENDING', 0, 100);
    const { users: allUsers, isLoading: loadingUsers } = useAdminUsers(undefined, 0, 100);
    const { users: suspendedUsers, isLoading: loadingSuspended } = useAdminUsers({ status: 'SUSPENDED' }, 0, 100);
    const { reports: resolvedReports, isLoading: loadingResolved } = useAdminReports('RESOLVED', 0, 100);

    const today = new Date().toDateString();
    const resolvedToday = resolvedReports.filter(r => {
        try { return new Date(r.createdAt).toDateString() === today; }
        catch { return false; }
    }).length;

    const stats: { label: string; value: number; loading: boolean; icon: ReactNode }[] = [
        {
            label: 'Pending Reports',
            value: pendingReports.length,
            loading: loadingPending,
            icon: <ReportIcon sx={{ fontSize: 40, color: 'warning.main' }} />,
        },
        {
            label: 'Total Users',
            value: allUsers.length,
            loading: loadingUsers,
            icon: <PeopleIcon sx={{ fontSize: 40, color: 'primary.main' }} />,
        },
        {
            label: 'Suspended Users',
            value: suspendedUsers.length,
            loading: loadingSuspended,
            icon: <BlockIcon sx={{ fontSize: 40, color: 'error.main' }} />,
        },
        {
            label: 'Resolved Today',
            value: resolvedToday,
            loading: loadingResolved,
            icon: <TaskAltIcon sx={{ fontSize: 40, color: 'success.main' }} />,
        },
    ];

    return (
        <Box sx={{ p: 3 }}>
            <Typography variant="h4" fontWeight={700} mb={3}>Admin Dashboard</Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(4, 1fr)' }, gap: 2, mb: 3 }}>
                {stats.map(({ label, value, loading, icon }) => (
                    <Card key={label}>
                        <CardContent>
                            {loading ? (
                                <Skeleton variant="rectangular" height={80} />
                            ) : (
                                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                                    <Box>
                                        <Typography variant="h3" fontWeight={700}>{value}</Typography>
                                        <Typography variant="body2" color="text.secondary">{label}</Typography>
                                    </Box>
                                    {icon}
                                </Box>
                            )}
                        </CardContent>
                    </Card>
                ))}
            </Box>
            <Box sx={{ display: 'flex', gap: 2 }}>
                <Button variant="contained" onClick={() => navigate('/admin/reports?status=PENDING')}>
                    Review Pending Reports
                </Button>
                <Button variant="outlined" onClick={() => navigate('/admin/users')}>
                    Manage Users
                </Button>
                <PermissionGate role="SUPER_ADMIN">
                    <Button variant="outlined" onClick={() => navigate('/admin/roles')}>
                        Roles &amp; Permissions
                    </Button>
                </PermissionGate>
            </Box>
        </Box>
    );
}

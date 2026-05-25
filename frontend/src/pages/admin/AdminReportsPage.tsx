import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
    Box, Button, Chip, IconButton, Menu, MenuItem, Paper, Skeleton,
    Tab, Table, TableBody, TableCell, TableContainer, TableHead,
    TableRow, Tabs, Typography,
} from '@mui/material';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import { format } from 'date-fns';
import { useAdminReports } from '../../hooks/admin/useAdminReports';
import { ReviewReportDialog } from '../../components/admin/ReviewReportDialog';
import type { Report, ReportEntityType, ReportStatus, ReviewAction } from '../../types/moderation';

type ChipColor = 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning';

const entityTypeColor = (type: ReportEntityType): ChipColor => {
    switch (type) {
        case 'POST': return 'primary';
        case 'USER': return 'secondary';
        case 'MESSAGE': return 'warning';
        default: return 'default';
    }
};

const statusColor = (status: ReportStatus): ChipColor => {
    switch (status) {
        case 'PENDING': return 'warning';
        case 'REVIEWED': return 'info';
        case 'RESOLVED': return 'success';
        default: return 'default';
    }
};

const TAB_STATUSES: (ReportStatus | undefined)[] = [undefined, 'PENDING', 'REVIEWED', 'RESOLVED', 'DISMISSED'];
const TAB_LABELS = ['All', 'Pending', 'Reviewed', 'Resolved', 'Dismissed'];

export default function AdminReportsPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [page, setPage] = useState(0);
    const [menuAnchor, setMenuAnchor] = useState<{ el: HTMLElement; report: Report } | null>(null);
    const [reviewDialog, setReviewDialog] = useState<{ report: Report; defaultAction?: ReviewAction } | null>(null);

    const rawStatus = searchParams.get('status');
    const activeStatus = TAB_STATUSES.includes(rawStatus as ReportStatus)
        ? (rawStatus as ReportStatus)
        : undefined;
    const currentTabIndex = Math.max(0, TAB_STATUSES.indexOf(activeStatus));

    const { reports, isLoading } = useAdminReports(activeStatus, page, 20);

    const handleTabChange = (_: React.SyntheticEvent, index: number) => {
        const status = TAB_STATUSES[index];
        setSearchParams(status ? { status } : {});
        setPage(0);
    };

    const handleMenuAction = (action: ReviewAction) => {
        if (!menuAnchor) return;
        setReviewDialog({ report: menuAnchor.report, defaultAction: action });
        setMenuAnchor(null);
    };

    return (
        <Box sx={{ p: 3 }}>
            <Typography variant="h5" fontWeight={600} mb={2}>Content Reports</Typography>
            <Tabs value={currentTabIndex} onChange={handleTabChange} sx={{ mb: 2 }}>
                {TAB_LABELS.map(label => <Tab key={label} label={label} />)}
            </Tabs>
            <TableContainer component={Paper}>
                <Table>
                    <TableHead>
                        <TableRow>
                            <TableCell>Reporter</TableCell>
                            <TableCell>Entity Type</TableCell>
                            <TableCell>Reason</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell>Submitted Date</TableCell>
                            <TableCell>Actions</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {isLoading && Array.from({ length: 8 }).map((_, i) => (
                            <TableRow key={i}>
                                <TableCell colSpan={6}>
                                    <Skeleton variant="rectangular" height={52} animation="wave" />
                                </TableCell>
                            </TableRow>
                        ))}
                        {!isLoading && reports.length === 0 && (
                            <TableRow>
                                <TableCell colSpan={6}>
                                    <Box sx={{ py: 6, textAlign: 'center' }}>
                                        <Typography color="text.secondary">
                                            No reports matching the current filter.
                                        </Typography>
                                    </Box>
                                </TableCell>
                            </TableRow>
                        )}
                        {!isLoading && reports.map(report => (
                            <TableRow key={report.id}>
                                <TableCell>{report.reporterUsername}</TableCell>
                                <TableCell>
                                    <Chip label={report.entityType} size="small" color={entityTypeColor(report.entityType)} />
                                </TableCell>
                                <TableCell>{report.reason}</TableCell>
                                <TableCell>
                                    <Chip label={report.status} size="small" color={statusColor(report.status)} />
                                </TableCell>
                                <TableCell>{format(new Date(report.createdAt), 'MMM d, yyyy')}</TableCell>
                                <TableCell>
                                    <IconButton
                                        size="small"
                                        onClick={e => setMenuAnchor({ el: e.currentTarget, report })}
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
                <Button size="small" disabled={reports.length < 20} onClick={() => setPage(p => p + 1)}>
                    Next
                </Button>
            </Box>

            <Menu
                anchorEl={menuAnchor?.el}
                open={menuAnchor !== null}
                onClose={() => setMenuAnchor(null)}
            >
                <MenuItem onClick={() => handleMenuAction('RESOLVE')}>Resolve</MenuItem>
                <MenuItem onClick={() => handleMenuAction('DISMISS')}>Dismiss</MenuItem>
                <MenuItem onClick={() => handleMenuAction('MARK_REVIEWED')}>Mark as Reviewed</MenuItem>
            </Menu>

            <ReviewReportDialog
                open={reviewDialog !== null}
                onClose={() => setReviewDialog(null)}
                report={reviewDialog?.report ?? null}
                defaultAction={reviewDialog?.defaultAction}
            />
        </Box>
    );
}

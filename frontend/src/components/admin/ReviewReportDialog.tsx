import { useEffect, useState } from 'react';
import {
    Box, Button, Chip, CircularProgress, Dialog, DialogActions,
    DialogContent, DialogTitle, FormControlLabel, IconButton,
    Radio, RadioGroup, Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import { format } from 'date-fns';
import { useReviewReport } from '../../hooks/admin/useReviewReport';
import type { Report, ReportEntityType, ReviewAction } from '../../types/moderation';

interface ReviewReportDialogProps {
    open: boolean;
    onClose: () => void;
    report: Report | null;
    defaultAction?: ReviewAction;
}

type ChipColor = 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning';

const entityTypeColor = (type: ReportEntityType): ChipColor => {
    switch (type) {
        case 'POST': return 'primary';
        case 'USER': return 'secondary';
        case 'MESSAGE': return 'warning';
        default: return 'default';
    }
};

export function ReviewReportDialog({ open, onClose, report, defaultAction }: ReviewReportDialogProps) {
    const [selectedAction, setSelectedAction] = useState<ReviewAction | null>(defaultAction ?? null);
    const { mutate: reviewReport, isPending } = useReviewReport();

    useEffect(() => {
        if (open) setSelectedAction(defaultAction ?? null);
    }, [open, defaultAction]);

    const handleConfirm = () => {
        if (!report || !selectedAction) return;
        reviewReport(
            { id: report.id, payload: { action: selectedAction } },
            { onSuccess: onClose }
        );
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
            <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                Review Report
                <IconButton onClick={onClose} size="small" disabled={isPending}>
                    <CloseIcon fontSize="small" />
                </IconButton>
            </DialogTitle>
            <DialogContent>
                {report && (
                    <Box sx={{ mb: 2 }}>
                        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', mb: 0.5 }}>
                            <Chip label={report.entityType} size="small" color={entityTypeColor(report.entityType)} />
                            <Typography variant="body2">{report.reason}</Typography>
                        </Box>
                        <Typography variant="body2" color="text.secondary">
                            Reported by @{report.reporterUsername}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" display="block">
                            {format(new Date(report.createdAt), 'MMM d, yyyy')}
                        </Typography>
                    </Box>
                )}
                <RadioGroup
                    value={selectedAction ?? ''}
                    onChange={(_, v) => setSelectedAction(v as ReviewAction)}
                >
                    <FormControlLabel value="RESOLVE" control={<Radio />} label="Resolve: Take action and close this report" />
                    <FormControlLabel value="DISMISS" control={<Radio />} label="Dismiss: No violation found, close this report" />
                    <FormControlLabel value="MARK_REVIEWED" control={<Radio />} label="Mark as Reviewed: Acknowledged but monitoring" />
                </RadioGroup>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={isPending}>Cancel</Button>
                <Button
                    variant="contained"
                    onClick={handleConfirm}
                    disabled={selectedAction === null || isPending}
                    startIcon={isPending ? <CircularProgress size={16} color="inherit" /> : undefined}
                >
                    Confirm
                </Button>
            </DialogActions>
        </Dialog>
    );
}

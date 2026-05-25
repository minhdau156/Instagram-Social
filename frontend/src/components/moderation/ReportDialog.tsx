import { useEffect, useState } from "react";
import { ReportEntityType, ReportReason } from "../../types/moderation";
import { useSubmitReport } from "../../hooks/moderation/useSubmitReport";
import { Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, FormControlLabel, IconButton, Radio, RadioGroup, TextField, Typography } from "@mui/material";
import CloseIcon from '@mui/icons-material/Close';

const REASON_LABELS: Record<ReportReason, string> = {
    SPAM: 'Spam',
    HATE_SPEECH: 'Hate speech or symbols',
    NUDITY: 'Nudity or sexual activity',
    VIOLENCE: 'Violence or dangerous organizations',
    HARASSMENT: 'Harassment',
    FALSE_INFORMATION: 'False information',
    SELF_HARM: 'Self harm or suicide',
    OTHER: 'Other',
};

interface ReportDialogProps {
    open: boolean;
    onClose: () => void;
    entityType: ReportEntityType;
    entityId: string;
    title?: string;
}

export const ReportDialog = ({ open, onClose, entityType, entityId, title }: ReportDialogProps) => {
    const [selectedReason, setSelectedReason] = useState<ReportReason | null>(null);
    const [details, setDetails] = useState('');

    const { mutate: submitReport, isPending, isError } = useSubmitReport();

    useEffect(() => {
        if (open) {
            setSelectedReason(null);
            setDetails('');
        }
    }, [open]);


    return (
        <Dialog open={open} onClose={onClose}>
            <DialogTitle>
                {title || `Report ${entityType}`}
                <IconButton
                    color="inherit"
                    onClick={onClose}
                    sx={{
                        position: 'absolute',
                        right: 8,
                        top: 8,
                    }}
                >
                    <CloseIcon />
                </IconButton>
            </DialogTitle>
            <DialogContent>
                <DialogContentText>
                    Why are you reporting this?
                    <RadioGroup
                        value={selectedReason}
                        onChange={(event) => setSelectedReason(event.target.value as ReportReason)}
                    >
                        {Object.entries(REASON_LABELS).map(([key, label]) => (
                            <FormControlLabel
                                key={key}
                                value={key}
                                control={<Radio />}
                                label={label}
                            />
                        ))}
                        {selectedReason === 'OTHER' && (
                            <TextField
                                value={details}
                                onChange={(event) => setDetails(event.target.value)}
                                label="Details"
                                placeholder="Tell us more (optional)"
                                multiline
                                rows={3}
                                inputProps={{ maxLength: 1000 }}
                            />
                        )}
                    </RadioGroup>
                </DialogContentText>
                {isError && (
                    <Typography color="error">
                        Something went wrong. Please try again.
                    </Typography>
                )}
            </DialogContent>
            <DialogActions>
                <Button variant="text" onClick={onClose} disabled={isPending}>
                    Cancel
                </Button>
                <Button variant="contained" onClick={() => submitReport({ entityType, entityId, reason: selectedReason!, details: details.trim() || undefined }, { onSuccess: onClose })} disabled={!selectedReason || isPending}>
                    {isPending ? <CircularProgress size={16} color="inherit" /> : 'Submit Report'}
                </Button>
            </DialogActions>
        </Dialog>
    )
}
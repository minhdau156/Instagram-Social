import { useState } from "react";
import { useAddComment } from "../../hooks/comment/useAddComment";
import { Box, TextField, IconButton, CircularProgress, Button } from "@mui/material";
import SendIcon from '@mui/icons-material/Send';

interface CommentInputProps {
    postId: string;
    parentId?: string | null;   // null or undefined = top-level
    initialValue?: string;      // for edit mode
    onSuccess?: () => void;     // called after successful submit
    onCancel?: () => void;      // called on cancel (edit mode)
    placeholder?: string;
    onSubmit?: (content: string) => void; // override default submit logic
    autoFocus?: boolean;
}


export function CommentInput({ postId, parentId, initialValue, onSuccess, onCancel, placeholder, onSubmit, autoFocus }: CommentInputProps) {
    const [value, setValue] = useState(initialValue ?? '');
    const addMutation = useAddComment(postId);

    const handleSubmit = () => {
        if (!value.trim()) return;
        if (onSubmit) {
            onSubmit(value.trim());
        } else {
            addMutation.mutate(
                { content: value.trim(), parentId: parentId ?? null },
                { onSuccess: () => { setValue(''); onSuccess?.(); } }
            );
        }
    };



    return (
        <Box sx={{ display: 'flex', alignItems: 'flex-end', gap: 1, p: 1 }}>
            <TextField
                fullWidth
                multiline
                maxRows={4}
                value={value}
                onChange={(e) => setValue(e.target.value)}
                placeholder={placeholder ?? 'Add a comment…'}
                onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSubmit(); } }}
                size="small"
                variant="standard"
                autoFocus={autoFocus}
            />
            <IconButton onClick={handleSubmit} disabled={!value.trim() || addMutation.isPending}>
                {addMutation.isPending ? <CircularProgress size={16} /> : <SendIcon />}
            </IconButton>
            {onCancel && (
                <Button size="small" onClick={onCancel}>Cancel</Button>
            )}
        </Box>
    );
}
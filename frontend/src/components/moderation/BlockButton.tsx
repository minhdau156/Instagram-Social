import { Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from "@mui/material";
import { useBlockUser } from "../../hooks/moderation/useBlockUser";
import { useUnblockUser } from "../../hooks/moderation/useUnblockUser";
import { useState } from "react";
import BlockIcon from "@mui/icons-material/Block";



interface BlockButtonProps {
    username: string;
    isBlocked: boolean;
    onToggle?: () => void;
}

export const BlockButton = ({ username, isBlocked, onToggle }: BlockButtonProps) => {
    const [confirmOpen, setConfirmOpen] = useState(false);
    const { mutate: blockUser, isPending: isBlocking } = useBlockUser();
    const { mutate: unblockUser, isPending: isUnblocking } = useUnblockUser();
    const isPending = isBlocking || isUnblocking;
    return (
        <>
            {isBlocked ? (
                <Button onClick={() => unblockUser(username)} aria-label={`Unblock ${username}`} disabled={isPending}>
                    <BlockIcon />
                </Button>
            ) : (
                <Button onClick={() => setConfirmOpen(true)} aria-label={`Block ${username}`} disabled={isPending}>
                    <BlockIcon />
                </Button>
            )}


            <Dialog
                open={confirmOpen}
                onClose={() => setConfirmOpen(false)}
            >
                <DialogTitle>
                    {`Block @${username}`}
                </DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        You won't see posts from @{username} and you won't be able to message them. They won't know you've blocked them.
                    </DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button variant="text" onClick={() => setConfirmOpen(false)} disabled={isPending}>
                        Cancel
                    </Button>
                    <Button color="error" variant="contained" onClick={() => blockUser(username, {
                        onSuccess: () => {
                            setConfirmOpen(false);
                            if (onToggle) onToggle();
                        }
                    })} disabled={isPending}>
                        {isBlocking ? <CircularProgress size={16} /> : "Block"}
                    </Button>
                </DialogActions>
            </Dialog>
        </>
    );
}
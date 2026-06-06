import { Dialog, DialogTitle, DialogContent, List, CircularProgress, Typography, Box } from "@mui/material";
import { useFollowing } from "../../hooks/follow/useFollowing";
import { UserListItem } from "./UserListItem";
import { useAuth } from "../../hooks/useAuth";
import { useRef, useCallback } from "react";

interface FollowingDialogProps {
    username: string;
    open: boolean;
    setOpen: (open: boolean) => void;
}

export const FollowingDialog = ({ username, open, setOpen }: FollowingDialogProps) => {
    const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = useFollowing(username);
    const { profile } = useAuth();

    const observer = useRef<IntersectionObserver | null>(null);
    const lastElementRef = useCallback((node: HTMLDivElement | null) => {
        if (isLoading || isFetchingNextPage) return;
        if (observer.current) observer.current.disconnect();

        observer.current = new IntersectionObserver(entries => {
            if (entries[0].isIntersecting && hasNextPage) {
                fetchNextPage();
            }
        });

        if (node) observer.current.observe(node);
    }, [isLoading, isFetchingNextPage, hasNextPage, fetchNextPage]);

    const following = data?.pages.flatMap(page => page.items) || [];
    const isEmpty = following.length === 0 && !isLoading;

    return (
        <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="xs">
            <DialogTitle>Following</DialogTitle>
            <DialogContent dividers sx={{ p: 0, minHeight: 100 }}>
                {isLoading ? (
                    <Box display="flex" justifyContent="center" p={3}>
                        <CircularProgress />
                    </Box>
                ) : isEmpty ? (
                    <Box display="flex" justifyContent="center" p={3}>
                        <Typography color="text.secondary">Not following anyone yet.</Typography>
                    </Box>
                ) : (
                    <List sx={{ pt: 0 }}>
                        {following.map((user, index) => {
                            const isLast = index === following.length - 1;
                            return (
                                <div key={user.id} ref={isLast ? lastElementRef : null}>
                                    <UserListItem
                                        user={user}
                                        currentUsername={profile?.user?.username}
                                        setOpen={setOpen}
                                    />
                                </div>
                            );
                        })}
                        {isFetchingNextPage && (
                            <Box display="flex" justifyContent="center" p={2}>
                                <CircularProgress size={24} />
                            </Box>
                        )}
                    </List>
                )}
            </DialogContent>
        </Dialog>
    );
};

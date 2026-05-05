import { Dialog, DialogTitle, DialogContent, Typography, Box, CircularProgress, List } from "@mui/material";
import { useGetPostLikers } from "../../hooks/like/useGetPostLiker";
import { useRef, useCallback } from "react";
import { UserListItem } from "../follow/UserListItem";
import { useAuth } from "../../hooks/useAuth";
import { UserSummary } from "../../types/follow";

interface LikersDialogProps {
    postId: string;
    open: boolean;
    setOpen: (open: boolean) => void;
}

export function LikersDialog({ postId, open, setOpen }: LikersDialogProps) {
    const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = useGetPostLikers(postId);
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
    const likers = data?.pages.flatMap((page) => page.content);

    return (
        <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="xs">
            <DialogTitle>Likes</DialogTitle>
            <DialogContent dividers sx={{ p: 0, minHeight: 100 }}>
                {isLoading ? (
                    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 100 }}>
                        <CircularProgress />
                    </Box>
                ) : (
                    <List>
                        {likers?.length === 0 && (
                            <Typography sx={{ p: 2, textAlign: 'center', color: 'text.secondary' }}>
                                No likes yet
                            </Typography>
                        )}
                        {likers?.map((user: UserSummary, index: number) => {
                            const isLast = index === likers.length - 1;
                            return (
                                <div key={user.id} ref={isLast ? lastElementRef : null}>
                                    <UserListItem
                                        user={user}
                                        currentUsername={profile?.user?.username}
                                        setOpen={setOpen}
                                    />
                                </div>
                            )
                        })}
                        {isFetchingNextPage && (
                            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 100 }}>
                                <CircularProgress />
                            </Box>
                        )}
                    </List>
                )}
            </DialogContent>
        </Dialog>
    )
}



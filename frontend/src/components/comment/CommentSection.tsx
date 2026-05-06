import { useRef, useCallback } from "react";
import { useComments } from "../../hooks/comment/useComments";
import { Alert, Button, CircularProgress, List, Typography } from "@mui/material";
import { CommentItem } from "./CommentItem";
import { CommentInput } from "./CommentInput";


interface CommentSectionProps {
    postId: string;
}

export function CommentSection({ postId }: CommentSectionProps) {
    const { data, isLoading, error, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useComments(postId);
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


    const comments = data?.pages.flatMap((comment) => comment.content) || [];
    const isEmpty = !isLoading && comments.length === 0;
    return (
        <>

            <List disablePadding>
                {error && (
                <Alert
                    severity="error"
                    action={
                        <Button size="small" color="inherit" onClick={() => refetch()}>
                            Retry
                        </Button>
                    }
                >
                    Failed to load comments.
                </Alert>
            )}
            {comments.map((comment, index) => {
                    const isLastItem = index === comments.length - 1;
                    return (
                        <div ref={isLastItem ? lastElementRef : null} key={comment.id}>
                            <CommentItem comment={comment} postId={postId} />
                        </div>
                    );
                })}
                {isFetchingNextPage && <CircularProgress size={20} />}
            </List>
            {isEmpty && (
                <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', py: 2 }}>
                    No comments yet. Be the first to comment!
                </Typography>
            )}
            <CommentInput postId={postId} parentId={null} />
        </>

    );
}
import { useAuth } from "../../hooks/useAuth";
import { useInfiniteQuery, useQueries } from "@tanstack/react-query";
import { getSavedPosts } from "../../api/savesApi";
import { postApi } from "../../api/postApi";
import { Box, Typography, Skeleton, Alert, AlertTitle, Button, ImageList, ImageListItem } from "@mui/material";
import BookmarkBorderIcon from '@mui/icons-material/BookmarkBorder';
import { useEffect, useRef, useCallback } from "react";
import { Post } from "../../types/post";
import { PostGrid } from "../../components/posts/PostGrid";
import { Navigate } from "react-router-dom";

export default function SavedPostsPage() {
    const { profile } = useAuth();
    const observerTarget = useRef<HTMLDivElement>(null);

    const { data, isLoading, isError, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } =
        useInfiniteQuery({
            queryKey: ['savedPosts', profile?.user?.id],
            queryFn: ({ pageParam = 0 }) => getSavedPosts(pageParam, 20),
            getNextPageParam: (lastPage) => lastPage.last ? undefined : lastPage.page + 1,
            initialPageParam: 0,
            enabled: !!profile?.user?.id,
        });

    const savedPosts = data?.pages.flatMap(p => p.content) || [];

    const postQueries = useQueries({
        queries: savedPosts.map(sp => ({
            queryKey: ['post', sp.postId],
            queryFn: () => postApi.getPostById(sp.postId),
            staleTime: 5 * 60 * 1000,
        }))
    });

    const loadedPosts = postQueries.map(q => q.data).filter((p): p is Post => !!p);

    const handleObserver = useCallback((entries: IntersectionObserverEntry[]) => {
        if (entries[0].isIntersecting && hasNextPage && !isFetchingNextPage) {
            fetchNextPage();
        }
    }, [fetchNextPage, hasNextPage, isFetchingNextPage]);

    useEffect(() => {
        const observer = new IntersectionObserver(handleObserver, { threshold: 0.1 });

        if (observerTarget.current) {
            observer.observe(observerTarget.current);
        }

        return () => observer.disconnect();
    }, [handleObserver]);

    if (!profile?.user?.id) {
        return <Navigate to="/login" replace />;
    }

    return (
        <Box sx={{ p: 2 }}>
            <Typography variant="h4" sx={{ mb: 2 }}>Saved</Typography>

            {isLoading ? (
                <ImageList cols={3} gap={4}>
                    {Array.from(new Array(9)).map((_, index) => (
                        <ImageListItem key={index}>
                            <Skeleton variant="rectangular" sx={{ aspectRatio: '1/1', height: 'auto' }} />
                        </ImageListItem>
                    ))}
                </ImageList>
            ) : isError ? (
                <Alert severity="error">
                    <AlertTitle>Error</AlertTitle>
                    Failed to load saved posts. Please try again.
                    <Button onClick={() => refetch()}>Retry</Button>
                </Alert>
            ) : savedPosts.length === 0 ? (
                <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', mt: 8, gap: 2 }}>
                    <BookmarkBorderIcon sx={{ fontSize: 80, color: 'text.secondary' }} />
                    <Typography variant="h5">No saved posts yet</Typography>
                    <Typography color="text.secondary">Tap the bookmark icon on any post to save it here.</Typography>
                </Box>
            ) : (
                <>
                    <PostGrid posts={loadedPosts} />
                    <div ref={observerTarget} style={{ height: 20 }} />
                </>
            )}
        </Box>
    );
}
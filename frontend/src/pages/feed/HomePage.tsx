import { Navigate } from "react-router-dom";
import { useHomeFeed } from "../../hooks/feed/useHomeFeed";
import { useAuth } from "../../hooks/useAuth";
import { Alert, Box, Container, Typography } from "@mui/material";
import { PostCard } from "../../components/posts/PostCard";
import { InfiniteScroll } from "../../components/common/InfiniteScroll";
import { SuggestedUsers } from "../users/SuggestedUsers";
import { PostSkeletonList } from "../../components/posts/PostSkeletonList";

export const HomePage = () => {
    const { profile } = useAuth();

    const { isError, isLoading, data, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useHomeFeed();

    if (!profile?.user) {
        return <Navigate to="/login" replace />
    }

    const posts = data?.posts ?? [];

    return (
        <Container maxWidth="lg">
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '2fr 1fr' }, gap: 4 }}>
                <Box >
                    {isError && <Alert severity="error" onClick={() => refetch()}>
                        Failed to load feed. Click to retry.
                    </Alert>}
                    {isLoading && <PostSkeletonList />}
                    {!isLoading && !isError && posts.length === 0 && (
                        <Box textAlign="center" py={8}>
                            <Typography variant="h6" gutterBottom>
                                Your feed is empty
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                                Follow some people to see their posts here.
                            </Typography>
                        </Box>
                    )}
                    {posts.length > 0 && (
                        <InfiniteScroll
                            fetchNextPage={fetchNextPage}
                            hasNextPage={hasNextPage}
                            isFetchingNextPage={isFetchingNextPage}
                        >
                            {posts.map((post) => (
                                <PostCard key={post.id} post={post} />
                            ))}
                        </InfiniteScroll>
                    )}
                </Box>

                <Box sx={{ display: { xs: 'none', md: 'block' } }}>
                    <SuggestedUsers />
                </Box>
            </Box>
        </Container>

    );
};
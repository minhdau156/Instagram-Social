import { Navigate } from "react-router-dom";
import { useAuth } from "../../hooks/useAuth";
import { useExploreFeed } from "../../hooks/feed/useExploreFeed";
import { getTrendingHashtags } from "../../api/feedApi";
import { Alert, Box, Button, Chip, Container, ImageList, ImageListItem, Skeleton, Typography } from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { InfiniteScroll } from "../../components/common/InfiniteScroll";
import { useState } from "react";
import { Post } from "../../types/post";
import { PostDetailModal } from "../../components/posts/PostDetailModal";
import ExploreOutlinedIcon from "@mui/icons-material/ExploreOutlined";

export default function ExplorePage() {
    const { profile } = useAuth();

    const [selectedPost, setSelectedPost] = useState<Post | null>(null);

    const { data: hashtags = [] } = useQuery({
        queryKey: ['trendingHashtags'],
        queryFn: () => getTrendingHashtags(10),
        staleTime: 5 * 60_000,
    });

    const { data, isLoading, isError, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useExploreFeed();

    if (!profile) {
        return <Navigate to="/login" replace />;
    }

    const posts = data?.posts ?? [];

    return (
        <Container maxWidth="md" sx={{ py: 3 }}>
            {hashtags.length > 0 && (
                <Box display="flex" gap={1} sx={{ overflowX: 'auto', pb: 1, mb: 3 }}>
                    {hashtags.map((tag) => (
                        <Chip
                            key={tag.id}
                            label={`#${tag.name}`}
                            onClick={() => {/* TODO: navigate to hashtag search in Phase 8 */ }}
                            sx={{ flexShrink: 0 }}
                        />
                    ))}
                </Box>
            )}
            {/* Feed grid */}
            {/* ... */}


            {isLoading && (
                <ImageList variant="masonry" cols={3} gap={4}>
                    {Array.from({ length: 9 }).map((_, i) => (
                        <ImageListItem key={i}>
                            <Skeleton
                                variant="rectangular"
                                width="100%"
                                height={i % 3 === 0 ? 300 : 200}
                            />
                        </ImageListItem>
                    ))}
                </ImageList>
            )}

            {isError && (
                <Alert
                    severity="error"
                    action={<Button size="small" onClick={() => refetch()}>Retry</Button>}
                >
                    Could not load explore feed.
                </Alert>
            )}

            {!isLoading && !isError && posts.length === 0 && (
                <Box textAlign="center" py={8}>
                    <ExploreOutlinedIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
                    <Typography variant="h6">Nothing to explore yet</Typography>
                    <Typography variant="body2" color="text.secondary">
                        Follow more people to personalise your explore feed.
                    </Typography>
                </Box>
            )}

            {posts.length > 0 && (
                <InfiniteScroll
                    fetchNextPage={fetchNextPage}
                    hasNextPage={hasNextPage}
                    isFetchingNextPage={isFetchingNextPage}
                >
                    <ImageList
                        variant="masonry"
                        cols={3}
                        gap={4}
                        sx={{ columnCount: { xs: 2, sm: 3 } }}
                    >
                        {posts.map((post) => (
                            <ImageListItem
                                key={post.id}
                                sx={{ cursor: 'pointer' }}
                                onClick={() => setSelectedPost(post)}
                            >
                                <img
                                    src={post.mediaItems[0]?.mediaUrl}
                                    alt={post.caption ?? ''}
                                    loading="lazy"
                                    style={{ display: 'block', width: '100%' }}
                                />
                            </ImageListItem>
                        ))}
                    </ImageList>
                </InfiniteScroll>
            )}


            {/* Modal */}
            {/* ... */}

            {selectedPost && (
                <PostDetailModal
                    post={selectedPost}
                    onClose={() => setSelectedPost(null)}
                />
            )}
        </Container>
    );
}   

import { useNavigate, useParams } from "react-router-dom";
import { useHashtagPosts } from "../../hooks/search/useHashtagPosts";
import { useEffect, useRef } from "react";
import {
    Avatar,
    Box,
    CircularProgress,
    Divider,
    Skeleton,
    Typography,
} from "@mui/material";
import TagIcon from '@mui/icons-material/Tag';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import FavoriteIcon from '@mui/icons-material/Favorite';
import ChatBubbleIcon from '@mui/icons-material/ChatBubble';

export default function HashtagPage() {
    const { name } = useParams<{ name: string }>();
    const navigate = useNavigate();
    const sentinelRef = useRef<HTMLDivElement>(null);

    const { posts, isLoading, isFetchingNextPage, hasNextPage, fetchNextPage } = useHashtagPosts(name ?? '');

    useEffect(() => {
        if (!sentinelRef.current) return;
        const observer = new IntersectionObserver((entries) => {
            if (entries[0].isIntersecting && hasNextPage && !isFetchingNextPage) {
                fetchNextPage();
            }
        });
        observer.observe(sentinelRef.current);
        return () => observer.disconnect();
    }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

    return (
        <Box sx={{ maxWidth: 900, mx: 'auto', py: 2, px: { xs: 1, sm: 2 } }}>
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 3, gap: 2 }}>
                <Avatar sx={{ width: 80, height: 80, bgcolor: 'primary.main' }}>
                    <TagIcon fontSize="large" />
                </Avatar>
                <Box>
                    <Typography variant="h4" fontWeight={700}>
                        #{name}
                    </Typography>
                    <Typography color="text.secondary">
                        {posts.length} posts loaded
                    </Typography>
                </Box>
            </Box>

            <Divider sx={{ mb: 2 }} />

            {isLoading && (
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(3, 1fr)' }, gap: 0.5 }}>
                    {Array.from({ length: 9 }).map((_, i) => (
                        <Skeleton key={i} variant="rectangular" sx={{ paddingBottom: '100%' }} />
                    ))}
                </Box>
            )}

            {!isLoading && posts.length === 0 && (
                <Box sx={{ py: 8, textAlign: 'center' }}>
                    <Typography color="text.secondary">No posts found for #{name}</Typography>
                </Box>
            )}

            {posts.length > 0 && (
                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(3, 1fr)' }, gap: 0.5 }}>
                    {posts.map((post) => (
                        <Box
                            key={post.id}
                            sx={{ position: 'relative', paddingBottom: '100%', overflow: 'hidden', cursor: 'pointer' }}
                            onClick={() => navigate(`/posts/${post.id}`)}
                        >
                            <Box
                                component="img"
                                src={post.mediaUrl}
                                alt={post.caption ?? ''}
                                loading="lazy"
                                sx={{ position: 'absolute', width: '100%', height: '100%', objectFit: 'cover' }}
                            />
                            {post.mediaType === 'VIDEO' && (
                                <PlayArrowIcon
                                    sx={{ position: 'absolute', top: 4, right: 4, color: 'white', fontSize: 20 }}
                                />
                            )}
                            <Box
                                sx={{
                                    position: 'absolute',
                                    inset: 0,
                                    bgcolor: 'rgba(0,0,0,0.35)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    gap: 2,
                                    opacity: 0,
                                    '&:hover': { opacity: 1 },
                                }}
                            >
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'white' }}>
                                    <FavoriteIcon fontSize="small" />
                                    <Typography variant="body2" color="white">{post.likeCount}</Typography>
                                </Box>
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'white' }}>
                                    <ChatBubbleIcon fontSize="small" />
                                    <Typography variant="body2" color="white">{post.commentCount}</Typography>
                                </Box>
                            </Box>
                        </Box>
                    ))}
                </Box>
            )}

            <div ref={sentinelRef} />

            {isFetchingNextPage && (
                <CircularProgress size={32} sx={{ display: 'block', mx: 'auto', my: 2 }} />
            )}
        </Box>
    );
}

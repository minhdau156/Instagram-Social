import { useState } from "react";
import { Post } from "../../types/post";
import { MoreVert, Favorite, FavoriteBorder, ChatBubbleOutline, BookmarkBorder, ShareOutlined, ChevronLeft, ChevronRight } from "@mui/icons-material";
import { Card, CardHeader, Avatar, Typography, IconButton, CardMedia, CardContent, Stack, Button, Box } from "@mui/material";
import { useAuth } from "../../hooks/useAuth";

export const PostCard: React.FC<{ post: Post }> = ({ post }) => {
    const [liked, setLiked] = useState(false);
    const [expanded, setExpanded] = useState(false);
    const [mediaIndex, setMediaIndex] = useState(0);

    const { profile } = useAuth()

    const hasMultipleMedia = post.mediaItems && post.mediaItems.length > 1;

    const handleNextMedia = () => {
        if (post.mediaItems && mediaIndex < post.mediaItems.length - 1) {
            setMediaIndex(prev => prev + 1);
        }
    };

    const handlePrevMedia = () => {
        if (mediaIndex > 0) {
            setMediaIndex(prev => prev - 1);
        }
    };

    return (
        <Card sx={{ maxWidth: 600, mb: 2 }}>
            <CardHeader
                avatar={<Avatar src={profile?.user?.avatarUrl ? profile.user?.avatarUrl : undefined} />}
                title={<Typography fontWeight="bold">{profile?.user?.username}</Typography>}
                subheader={post.location}
                action={<IconButton><MoreVert /></IconButton>}
            />
            {/* Carousel for multiple media */}
            <Box sx={{ position: 'relative' }}>
                <CardMedia
                    component="img"
                    image={`http://localhost:9000/instagram-media/${post.mediaItems?.[mediaIndex]?.mediaUrl}`}
                    sx={{ aspectRatio: '1/1', objectFit: 'cover' }}
                />
                {hasMultipleMedia && mediaIndex > 0 && (
                    <IconButton
                        onClick={handlePrevMedia}
                        sx={{ position: 'absolute', top: '50%', left: 8, transform: 'translateY(-50%)', bgcolor: 'rgba(255,255,255,0.7)', '&:hover': { bgcolor: 'white' } }}
                    >
                        <ChevronLeft />
                    </IconButton>
                )}
                {hasMultipleMedia && mediaIndex < post.mediaItems.length - 1 && (
                    <IconButton
                        onClick={handleNextMedia}
                        sx={{ position: 'absolute', top: '50%', right: 8, transform: 'translateY(-50%)', bgcolor: 'rgba(255,255,255,0.7)', '&:hover': { bgcolor: 'white' } }}
                    >
                        <ChevronRight />
                    </IconButton>
                )}
            </Box>
            <CardContent>
                {/* Action row */}
                <Stack direction="row" spacing={1} justifyContent="space-between">
                    <Stack direction="row" spacing={1}>
                        <IconButton onClick={() => setLiked(l => !l)}>
                            {liked ? <Favorite color="error" /> : <FavoriteBorder />}
                        </IconButton>
                        <IconButton><ChatBubbleOutline /></IconButton>
                        <IconButton><ShareOutlined /></IconButton>
                    </Stack>
                    <IconButton><BookmarkBorder /></IconButton>
                </Stack>
                <Typography fontWeight="bold" sx={{ mb: 1 }}>{post.likeCount} likes</Typography>

                {/* Truncated caption with toggle */}
                <Box sx={{ mb: 1 }}>
                    <Typography component="span" fontWeight="bold" sx={{ mr: 1 }}>
                        {post.userId}
                    </Typography>
                    <Typography
                        component="span"
                        sx={{
                            display: expanded ? 'block' : '-webkit-box',
                            WebkitLineClamp: expanded ? 'none' : 2,
                            WebkitBoxOrient: 'vertical',
                            overflow: 'hidden',
                            whiteSpace: 'pre-wrap'
                        }}
                    >
                        {post.caption}
                    </Typography>
                    {!expanded && post.caption && post.caption.length > 80 && (
                        <Button size="small" onClick={() => setExpanded(true)} sx={{ p: 0, minWidth: 'auto', textTransform: 'none', color: 'text.secondary' }}>
                            more
                        </Button>
                    )}
                </Box>

                <Typography variant="caption" color="text.secondary">
                    {new Date(post.createdAt).toLocaleDateString()}
                </Typography>
            </CardContent>
        </Card>
    );
};
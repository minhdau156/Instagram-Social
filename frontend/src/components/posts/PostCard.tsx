import { useEffect, useState } from "react";
import { Post } from "../../types/post";
import { MoreVert, ChevronLeft, ChevronRight } from "@mui/icons-material";
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import FlagOutlinedIcon from '@mui/icons-material/FlagOutlined';
import { Card, CardHeader, Avatar, Typography, IconButton, CardMedia, CardContent, Button, Box, Menu, MenuItem, ListItemIcon, ListItemText } from "@mui/material";
import { useAuth } from "../../hooks/useAuth";
import { LikeButton } from "./LikeButton";
import { SaveButton } from "./SaveButton";
import { ShareMenu } from "./ShareMenu";
import { LikersTooltip } from "./LikersTooltip";
import { PostDetailModal } from "./PostDetailModal";
import { ReportDialog } from "../moderation/ReportDialog";
import { usersApi } from "../../api/usersApi";
import { useMutation } from "@tanstack/react-query";


export const PostCard: React.FC<{ post: Post }> = ({ post }) => {
    const [expanded, setExpanded] = useState(false);
    const [mediaIndex, setMediaIndex] = useState(0);
    const [openDetail, setOpenDetail] = useState(false);
    const [autoFocusComment, setAutoFocusComment] = useState(false);
    const [menuAnchorEl, setMenuAnchorEl] = useState<HTMLButtonElement | null>(null);
    const [reportDialogOpen, setReportDialogOpen] = useState(false);

    const { data, mutate } = useMutation({
        mutationFn: () => usersApi.getUserById(post.userId),
    });

    useEffect(() => {
        mutate();
    }, []);
    const user = data;

    const handleOpenDetail = (focusComment = false) => {
        setAutoFocusComment(focusComment);
        setOpenDetail(true);
    };

    const handleCloseDetail = () => setOpenDetail(false);

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
                avatar={<Avatar src={user?.avatarUrl ? user?.avatarUrl : undefined} />}
                title={<Typography fontWeight="bold">{user?.username}</Typography>}
                subheader={post.location}
                action={
                    <IconButton onClick={(e) => setMenuAnchorEl(e.currentTarget)}>
                        <MoreVert />
                    </IconButton>
                }
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
                <Box sx={{ display: 'flex', alignItems: 'center', px: 1, py: 0.5 }}>
                    {/* Left: Like, Comment trigger, Share */}
                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 0.5 }}>
                        <LikeButton
                            postId={post.id}
                            liked={post.likedByCurrentUser || undefined}
                            likeCount={post.likeCount}
                            disabled={!profile?.user}
                        />
                        <IconButton
                            size="small"
                            aria-label="Comment on post"
                            onClick={() => handleOpenDetail(true)}   // opens PostDetailModal with comment auto-focus
                        >
                            <ChatBubbleOutlineIcon />
                        </IconButton>
                        <ShareMenu postId={post.id} disabled={!profile?.user} />
                    </Box>

                    {/* Spacer */}
                    <Box sx={{ flexGrow: 1 }} />

                    {/* Right: Save */}
                    <SaveButton
                        postId={post.id}
                        saved={post.savedByCurrentUser ?? false}
                        disabled={!profile?.user}
                    />
                </Box>
                {post.likeCount > 0 && (
                    <LikersTooltip
                        postId={post.id}
                        likeCount={post.likeCount}
                    />
                )}
                {post.commentCount > 0 && (
                    <Typography
                        variant="body2"
                        color="text.secondary"
                        sx={{ px: 2, cursor: 'pointer' }}
                        onClick={() => handleOpenDetail(false)}
                    >
                        View all {post.commentCount} comments
                    </Typography>
                )}


                {/* Truncated caption with toggle */}
                <Box sx={{ mb: 1 }}>
                    <Typography component="span" fontWeight="bold" sx={{ mr: 1 }}>
                        {user?.username}
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

            {openDetail && (
                <PostDetailModal
                    post={post}
                    onClose={handleCloseDetail}
                    autoFocusComment={autoFocusComment}
                />
            )}

            <Menu
                anchorEl={menuAnchorEl}
                open={Boolean(menuAnchorEl)}
                onClose={() => setMenuAnchorEl(null)}
            >
                {profile?.user && post.userId !== profile.user.id && (
                    <MenuItem onClick={() => { setMenuAnchorEl(null); setReportDialogOpen(true); }}>
                        <ListItemIcon><FlagOutlinedIcon fontSize="small" /></ListItemIcon>
                        <ListItemText>Report</ListItemText>
                    </MenuItem>
                )}
            </Menu>

            <ReportDialog
                open={reportDialogOpen}
                onClose={() => setReportDialogOpen(false)}
                entityType="POST"
                entityId={post.id}
                title="Report this post"
            />
        </Card>
    );
};
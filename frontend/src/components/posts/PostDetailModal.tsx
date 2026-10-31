import React, { useEffect, useState } from 'react';
import {
    Dialog, Box, Typography, IconButton, Avatar, Stack, Divider,
    DialogTitle, DialogContent, DialogContentText, DialogActions, Button, CircularProgress
} from '@mui/material';
import { ChatBubbleOutline, Close, DeleteOutline, ChevronLeft, ChevronRight } from '@mui/icons-material';
import { Post } from '../../types/post';
import { useAuth } from '../../hooks/useAuth';
import { LikeButton } from './LikeButton';
import { SaveButton } from './SaveButton';
import { ShareMenu } from './ShareMenu';
import { LikersTooltip } from './LikersTooltip';
import { CommentSection } from '../comment/CommentSection';
import { useMutation } from '@tanstack/react-query';
import { usersApi } from '../../api/usersApi';
import { mediaUrl } from '../../lib/media';
import { useDeletePost } from '../../hooks/post/useDeletePost';

interface PostDetailModalProps {
    post: Post;
    onClose: () => void;
    autoFocusComment?: boolean;
}


export const PostDetailModal: React.FC<PostDetailModalProps> = ({ post, onClose, autoFocusComment }) => {
    const { profile } = useAuth();
    const isOwner = profile?.user.id === post.userId;
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
    const deleteMutation = useDeletePost();

    const [mediaIndex, setMediaIndex] = useState(0);
    const hasMultipleMedia = post.mediaItems && post.mediaItems.length > 1;
    const currentMedia = post.mediaItems?.[mediaIndex];
    const isVideo = currentMedia?.mediaType === 'VIDEO';

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

    const { data, mutate } = useMutation({
        mutationFn: () => usersApi.getUserById(post.userId),
    });

    useEffect(() => {
        mutate();
    }, []);
    const postUser = data;
    return (
        <Dialog open={true} onClose={onClose} fullScreen>
            {/* Close button — fullScreen dialog has no backdrop to click */}
            <IconButton
                aria-label="Close"
                onClick={onClose}
                sx={{
                    position: 'absolute',
                    top: 8,
                    right: 8,
                    zIndex: 1,
                    bgcolor: 'rgba(0, 0, 0, 0.5)',
                    color: '#fff',
                    '&:hover': { bgcolor: 'rgba(0, 0, 0, 0.7)' },
                }}
            >
                <Close />
            </IconButton>
            <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, height: '100vh' }}>
                {/* Left Side - Media */}
                <Box sx={{
                    flex: { xs: 'none', md: '1 1 60%' },
                    bgcolor: 'black',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderRight: 1,
                    borderColor: 'divider',
                    position: 'relative'
                }}>
                    {isVideo ? (
                        <video
                            src={mediaUrl(currentMedia?.mediaUrl)}
                            poster={currentMedia?.thumbnailUrl ? mediaUrl(currentMedia.thumbnailUrl) : undefined}
                            controls
                            autoPlay
                            muted
                            loop
                            playsInline
                            style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
                        />
                    ) : (
                        <img
                            src={mediaUrl(currentMedia?.mediaUrl)}
                            alt="Post media"
                            style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
                        />
                    )}
                    {/* Carousel for multiple media */}
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

                {/* Right Side - Details & Comments */}
                <Box sx={{
                    flex: { xs: 'none', md: '1 1 40%' },
                    display: 'flex',
                    flexDirection: 'column',
                    bgcolor: 'background.paper'
                }}>


                    {/* Comments Area (Caption + Comments) */}
                    <Box sx={{ p: 2, flexGrow: 1, overflowY: 'auto' }}>
                        <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'flex-start' }}>
                            <Avatar src={postUser?.avatarUrl || undefined}>{String(post.userId).charAt(0)}</Avatar>
                            <Box sx={{ flexGrow: 1 }}>
                                <Typography component="span" fontWeight="bold" sx={{ mr: 1 }}>
                                    {postUser?.username}
                                </Typography>
                                <Typography component="span" sx={{ whiteSpace: 'pre-wrap' }}>
                                    {post.caption}
                                </Typography>
                                <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
                                    {new Date(post.createdAt).toLocaleDateString()}
                                </Typography>
                            </Box>
                            {isOwner && (
                                <IconButton size="small" aria-label="Delete post" onClick={() => setDeleteDialogOpen(true)}>
                                    <DeleteOutline fontSize="small" />
                                </IconButton>
                            )}
                        </Stack>
                        <Divider sx={{ my: 2 }} />
                        <CommentSection postId={post.id} autoFocus={autoFocusComment} />
                    </Box>
                    <Divider />

                    {/* Action Row */}
                    <Box sx={{ p: 2 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', mb: 1, ml: -1 }}>
                            {/* Left: Like, Comment trigger, Share */}
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                                <LikeButton
                                    postId={post.id}
                                    liked={post.likedByCurrentUser ?? false}
                                    likeCount={post.likeCount}
                                    disabled={!profile?.user}
                                />
                                <IconButton
                                    size="small"
                                    aria-label="Comment on post"
                                >
                                    <ChatBubbleOutline />
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
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                            {new Date(post.createdAt).toLocaleDateString()}
                        </Typography>
                    </Box>
                </Box>
            </Box>

            {/* Delete confirmation */}
            <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
                <DialogTitle>Delete Post?</DialogTitle>
                <DialogContent>
                    <DialogContentText>Are you sure you want to delete this post? This action cannot be undone.</DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDeleteDialogOpen(false)} color="inherit">Cancel</Button>
                    <Button
                        color="error"
                        disabled={deleteMutation.isPending}
                        onClick={() => deleteMutation.mutate(post.id, {
                            onSuccess: () => {
                                setDeleteDialogOpen(false);
                                onClose();
                            },
                        })}
                    >
                        {deleteMutation.isPending ? <CircularProgress size={20} /> : 'Delete'}
                    </Button>
                </DialogActions>
            </Dialog>
        </Dialog>
    );
};

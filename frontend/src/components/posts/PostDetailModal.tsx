import React from 'react';
import { Dialog, Box, Typography, IconButton, Avatar, Stack, Divider } from '@mui/material';
import { ChatBubbleOutline } from '@mui/icons-material';
import { Post } from '../../types/post';
import { useAuth } from '../../hooks/useAuth';
import { LikeButton } from './LikeButton';
import { SaveButton } from './SaveButton';
import { ShareMenu } from './ShareMenu';
import { LikersTooltip } from './LikersTooltip';
import { CommentSection } from '../comment/CommentSection';
import { User } from '../../types/user';

interface PostDetailModalProps {
    post: Post;
    onClose: () => void;
    autoFocusComment?: boolean;
    postUser: User | null;
}


export const PostDetailModal: React.FC<PostDetailModalProps> = ({ post, onClose, autoFocusComment, postUser }) => {
    const { profile } = useAuth();
    return (
        <Dialog open={true} onClose={onClose} fullScreen>
            <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, height: '100vh' }}>
                {/* Left Side - Media */}
                <Box sx={{
                    flex: { xs: 'none', md: '1 1 60%' },
                    bgcolor: 'black',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderRight: 1,
                    borderColor: 'divider'
                }}>
                    <img
                        src={`http://localhost:9000/instagram-media/${post.mediaItems?.[0]?.mediaUrl}`}
                        alt="Post media"
                        style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }}
                    />
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
                        <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
                            <Avatar src={postUser?.avatarUrl || undefined}>{String(post.userId).charAt(0)}</Avatar>
                            <Box>
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
        </Dialog>
    );
};

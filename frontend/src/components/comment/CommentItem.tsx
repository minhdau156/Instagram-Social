import { useState } from "react";
import {
    Avatar, IconButton, Link, ListItem, ListItemAvatar, ListItemText,
    Menu, MenuItem, Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Button,
    Box, List, CircularProgress, Collapse
} from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import { Comment } from "../../types/comment";
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import ReplyIcon from '@mui/icons-material/Reply';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import { useEditComment } from "../../hooks/comment/useEditComment";
import { useDeleteComment } from "../../hooks/comment/useDeleteComment";
import { useReplies } from "../../hooks/comment/useReplies";
import { CommentInput } from "./CommentInput";
import { useQueryClient } from "@tanstack/react-query";

interface CommentItemProps {
    comment: Comment;
    postId: string;
    currentUserId?: string;
}

export function CommentItem({ comment, postId, currentUserId }: CommentItemProps) {
    const queryClient = useQueryClient();
    const editMutation = useEditComment(comment.id, postId);
    const deleteMutation = useDeleteComment(comment.id, postId);

    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
    const [isEditing, setIsEditing] = useState(false);
    const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);
    const [showReplies, setShowReplies] = useState(false);
    const [isReplying, setIsReplying] = useState(false);

    const { data: repliesData, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading: isRepliesLoading } = useReplies(comment.id, showReplies);

    const closeMenu = () => setAnchorEl(null);

    return (
        <Box>
            <ListItem alignItems="flex-start">
                <ListItemAvatar>
                    <Avatar src={comment.avatarUrl || undefined} alt={comment.username} />
                </ListItemAvatar>

                {isEditing ? (
                    <Box sx={{ flexGrow: 1, width: '100%', mt: 1 }}>
                        <CommentInput
                            postId={postId} initialValue={comment.content}
                            onSubmit={(content) => editMutation.mutate({ content }, { onSuccess: () => setIsEditing(false) })}
                            onCancel={() => setIsEditing(false)}
                        />
                    </Box>
                ) : (
                    <ListItemText
                        primary={<Link component={RouterLink} to={`/${comment.username}`} underline="none" color="inherit" fontWeight="bold">{comment.username}</Link>}
                        secondary={comment.content}
                        sx={{ mt: 1 }}
                    />
                )}

                {!isEditing && (
                    <Box sx={{ display: 'flex', alignItems: 'center', mt: 0.5 }}>
                        <IconButton size="small"><FavoriteBorderIcon fontSize="small" /></IconButton>
                        <IconButton size="small" onClick={() => {
                            if (!showReplies && comment.replyCount > 0) setShowReplies(true);
                            setIsReplying(!isReplying);
                        }}><ReplyIcon fontSize="small" /></IconButton>

                        {comment.userId === currentUserId && (
                            <>
                                <IconButton size="small" onClick={(e) => setAnchorEl(e.currentTarget)}>
                                    <MoreVertIcon fontSize="small" />
                                </IconButton>
                                <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={closeMenu}>
                                    <MenuItem onClick={() => { closeMenu(); setIsEditing(true); }}>Edit</MenuItem>
                                    <MenuItem onClick={() => { closeMenu(); setIsDeleteDialogOpen(true); }} sx={{ color: 'error.main' }}>Delete</MenuItem>
                                </Menu>
                            </>
                        )}
                    </Box>
                )}
            </ListItem>

            {/* Replies */}
            <Box sx={{ ml: 7 }}>
                {isReplying && (
                    <Box sx={{ mb: 1, pr: 2 }}>
                        <CommentInput
                            postId={postId} parentId={comment.id} placeholder="Add a reply..."
                            onSuccess={() => { setIsReplying(false); setShowReplies(true); queryClient.invalidateQueries({ queryKey: ['replies', comment.id] }); }}
                            onCancel={() => setIsReplying(false)}
                        />
                    </Box>
                )}

                {comment.replyCount > 0 && (
                    <Box>
                        <Button size="small" onClick={() => setShowReplies(!showReplies)} sx={{ textTransform: 'none', color: 'text.secondary', fontWeight: 'bold' }}>
                            {showReplies ? 'Hide replies' : `View ${comment.replyCount} replies`}
                        </Button>
                        <Collapse in={showReplies}>
                            {isRepliesLoading && <CircularProgress size={20} sx={{ mt: 1, ml: 2 }} />}
                            <List disablePadding>
                                {repliesData?.pages.flatMap(page => page.content).map(reply => (
                                    <CommentItem key={reply.id} comment={reply} postId={postId} currentUserId={currentUserId} />
                                ))}
                            </List>
                            {hasNextPage && (
                                <Button size="small" onClick={() => fetchNextPage()} disabled={isFetchingNextPage} sx={{ textTransform: 'none', color: 'text.secondary', ml: 1, mb: 1 }}>
                                    {isFetchingNextPage ? 'Loading...' : 'Show more replies'}
                                </Button>
                            )}
                        </Collapse>
                    </Box>
                )}
            </Box>

            {/* Delete Dialog */}
            <Dialog open={isDeleteDialogOpen} onClose={() => setIsDeleteDialogOpen(false)}>
                <DialogTitle>Delete Comment?</DialogTitle>
                <DialogContent>
                    <DialogContentText>Are you sure you want to delete this comment? This action cannot be undone.</DialogContentText>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setIsDeleteDialogOpen(false)} color="inherit">Cancel</Button>
                    <Button
                        color="error" disabled={deleteMutation.isPending}
                        onClick={() => deleteMutation.mutate(undefined, { onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['comments', postId] }); setIsDeleteDialogOpen(false); } })}
                    >
                        {deleteMutation.isPending ? <CircularProgress size={20} /> : 'Delete'}
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
}
import { ImageList, ImageListItem, Box, Typography } from "@mui/material";
import { Favorite, ChatBubble } from "@mui/icons-material";
import { useState } from "react";
import { Post } from "../../types/post";
import { PostDetailModal } from "./PostDetailModal";

export const PostGrid: React.FC<{ posts: Post[] }> = ({ posts }) => {
    const [selected, setSelected] = useState<Post | null>(null);
    return (
        <>
            <ImageList cols={3} gap={4}>
                {posts.map((post, index) => {
                    const imageUrl = `http://localhost:9000/instagram-media/${post.mediaItems?.[index]?.mediaUrl}` || 'https://via.placeholder.com/300?text=No+Media';
                    return (
                        <ImageListItem
                            key={post.id}
                            onClick={() => setSelected(post)}
                            sx={{
                                cursor: 'pointer',
                                position: 'relative',
                                '&:hover .overlay': { opacity: 1 }
                            }}
                        >
                            <img src={imageUrl} loading="lazy" style={{ aspectRatio: '1/1', objectFit: 'cover' }} alt="Post thumbnail" />
                            <Box
                                className="overlay"
                                sx={{
                                    position: 'absolute',
                                    top: 0, left: 0, right: 0, bottom: 0,
                                    bgcolor: 'rgba(0,0,0,0.4)',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    opacity: 0,
                                    transition: 'opacity 0.2s',
                                    color: 'white',
                                    gap: 2
                                }}
                            >
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                                    <Favorite fontSize="small" />
                                    <Typography fontWeight="bold">{post.likeCount}</Typography>
                                </Box>
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                                    <ChatBubble fontSize="small" />
                                    <Typography fontWeight="bold">{post.commentCount}</Typography>
                                </Box>
                            </Box>
                        </ImageListItem>
                    );
                })}
            </ImageList>
            {selected && <PostDetailModal post={selected} onClose={() => setSelected(null)} />}
        </>
    );
};
import { useEffect, useState } from "react";
import { useLikePost } from "../../hooks/like/useLikePost";
import { Box, CircularProgress, IconButton, Typography } from "@mui/material";
import FavoriteIcon from '@mui/icons-material/Favorite';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import { keyframes } from '@emotion/react';

const heartPop = keyframes`
  0%   { transform: scale(1); }
  50%  { transform: scale(1.35); }
  100% { transform: scale(1); }
`;

interface LikeButtonProps {
    postId: string;
    liked: boolean;           // current liked state from post data
    likeCount: number;        // current count from post data
    disabled?: boolean;       // disable when not authenticated
}

export function LikeButton({ postId, liked, likeCount, disabled }: LikeButtonProps) {
    const likeMutation = useLikePost(postId);
    const [animating, setAnimating] = useState(false);
    const handleClick = () => {
        if (disabled) return;
        if (!liked) setAnimating(true);
        likeMutation.mutate(liked);
    };
    useEffect(() => {
        if (!likeMutation.isSuccess) return;
        const timer = setTimeout(() => setAnimating(false), 300);
        return () => clearTimeout(timer);
    }, [likeMutation.isSuccess]);

    return (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <IconButton
                onClick={handleClick}
                disabled={likeMutation.isPending}
                size="small"
                aria-label={liked ? 'Unlike post' : 'Like post'}
            >
                {likeMutation.isPending ? (
                    <CircularProgress size={16} />
                ) : liked ? (
                    <FavoriteIcon color="error" sx={animating ? { animation: `${heartPop} 0.3s ease` } : {}} />
                ) : (
                    <FavoriteBorderIcon color="inherit" />
                )}
            </IconButton>
            <Typography variant="body2">{likeCount}</Typography>
        </Box>
    )

}
import { Tooltip, Typography } from "@mui/material";
import { useState } from "react";
import { LikersDialog } from "./LikersDialog";

interface LikersTooltipProps {
    postId: string;
    likeCount: number;
    previewUsernames?: string[];  // first 2-3 likers from post data (pre-fetched)
}

export function LikersTooltip({ postId, likeCount, previewUsernames }: LikersTooltipProps) {

    const [open, setOpen] = useState(false);
    return (
        <>
            <Tooltip title={
                likeCount === 0 ? 'Be the first to like this post' :
                    likeCount === 1 ? `Liked by ${previewUsernames?.[0]}` :
                        likeCount === 2 ? `Liked by ${previewUsernames?.[0]} and ${previewUsernames?.[1]}` :
                            `Liked by ${previewUsernames?.[0]}, ${previewUsernames?.[1]} and ${likeCount - 2} others`
            }>
                <Typography onClick={() => setOpen(true)} variant="body2" sx={{ cursor: 'pointer' }}>
                    {likeCount}
                </Typography>
            </Tooltip>

            <LikersDialog postId={postId} open={open} setOpen={setOpen} />
        </>
    )
}
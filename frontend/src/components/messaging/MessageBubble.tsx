import { Box, Card, CardContent, Skeleton, Typography } from "@mui/material";
import { useState } from "react";
import DoneIcon from '@mui/icons-material/Done';
import DoneAllIcon from '@mui/icons-material/DoneAll';
import { Message } from "../../types/messaging";

interface MessageBubbleProps {
    message: Message;
    isOwn: boolean;
}

export function MessageBubble({ message, isOwn }: MessageBubbleProps) {
    const [imgLoaded, setImgLoaded] = useState(false);

    const timestamp = new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    return (
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: isOwn ? 'flex-end' : 'flex-start' }}>
            <Box sx={{
                borderRadius: isOwn ? '18px 18px 4px 18px' : '18px 18px 18px 4px',
                bgcolor: isOwn ? 'primary.main' : 'grey.100',
                color: isOwn ? 'primary.contrastText' : '#000000',
                px: (message.messageType === 'IMAGE' || message.messageType === 'VIDEO') ? 0 : 2,
                py: (message.messageType === 'IMAGE' || message.messageType === 'VIDEO') ? 0 : 1,
                maxWidth: '70%',
                overflow: 'hidden',
            }}>
                {message.messageType === 'TEXT' && (
                    <Typography variant="body2">{message.content}</Typography>
                )}

                {message.messageType === 'IMAGE' && (
                    <>
                        {!imgLoaded && <Skeleton variant="rectangular" width={240} height={160} />}
                        <Box
                            component="img"
                            src={message.mediaUrl ?? undefined}
                            onLoad={() => setImgLoaded(true)}
                            sx={{ maxWidth: 240, width: '100%', display: imgLoaded ? 'block' : 'none' }}
                        />
                    </>
                )}

                {message.messageType === 'VIDEO' && (
                    <Box
                        component="video"
                        src={message.mediaUrl ?? undefined}
                        controls
                        sx={{ maxWidth: 240, width: '100%', display: 'block' }}
                    />
                )}

                {message.messageType === 'POST_SHARE' && (
                    <Card variant="outlined" sx={{ width: 200 }}>
                        <CardContent sx={{ py: 1 }}>
                            <Typography variant="caption" color="text.secondary">Shared post</Typography>
                            <Typography variant="body2" noWrap>#{message.sharedPostId?.slice(0, 8)}</Typography>
                        </CardContent>
                    </Card>
                )}
            </Box>

            {isOwn && (
                <Typography variant="caption" color="text.secondary" sx={{ display: 'flex', alignItems: 'center' }}>
                    {message.status === 'SENT' && <DoneIcon fontSize="small" />}
                    {message.status === 'DELIVERED' && <DoneAllIcon fontSize="small" />}
                    {message.status === 'READ' && <DoneAllIcon fontSize="small" color="primary" />}
                </Typography>
            )}

            <Typography variant="caption" color="text.disabled">
                {timestamp}
            </Typography>
        </Box>
    );
}
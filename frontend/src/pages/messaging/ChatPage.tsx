import { useEffect, useRef, useState } from 'react';
import { Avatar, Box, CircularProgress, IconButton, TextField, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import EmojiEmotionsOutlinedIcon from '@mui/icons-material/EmojiEmotionsOutlined';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import SendIcon from '@mui/icons-material/Send';
import { useQueryClient } from '@tanstack/react-query';
import { messagingApi } from '../../api/messagingApi';
import { useMessages } from '../../hooks/messaging/useMessages';
import { useSendMessage } from '../../hooks/messaging/useSendMessage';
import { useWebSocket } from '../../hooks/useWebSocket';
import { useAuth } from '../../hooks/useAuth';
import { MessageBubble } from '../../components/messaging/MessageBubble';
import { TypingIndicator } from '../../components/messaging/TypingIndicator';

interface ChatPageProps {
    conversationId: string;
    onBack?: () => void;
}

export default function ChatPage({ conversationId, onBack }: ChatPageProps) {
    const { profile } = useAuth();
    const queryClient = useQueryClient();
    const [inputValue, setInputValue] = useState('');
    const sentinelRef = useRef<HTMLDivElement>(null);
    const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const { messages, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = useMessages(conversationId);
    const { sendMessage, isPending } = useSendMessage(conversationId);
    const { sendTyping, typingUserIds } = useWebSocket(conversationId);

    // Build senderId → username map from loaded messages for TypingIndicator
    const senderMap = new Map(messages.map(m => [m.senderId, m.senderUsername]));
    const typingUsernames = typingUserIds
        .filter(id => id !== profile?.user.id)
        .map(id => senderMap.get(id) ?? id);

    const conversationDisplayName = messages.find(m => m.senderId !== profile?.user.id)?.senderUsername ?? 'Conversation';

    // Mark read on mount
    useEffect(() => {
        messagingApi.markRead(conversationId).then(() => {
            queryClient.invalidateQueries({ queryKey: ['conversations'] });
        });
    }, [conversationId, queryClient]);

    // IntersectionObserver: sentinel is at the visual top (DOM bottom in column-reverse)
    useEffect(() => {
        const sentinel = sentinelRef.current;
        if (!sentinel) return;
        const observer = new IntersectionObserver(
            ([entry]) => {
                if (entry.isIntersecting && hasNextPage && !isFetchingNextPage) {
                    fetchNextPage();
                }
            },
            { threshold: 0.1 }
        );
        observer.observe(sentinel);
        return () => observer.disconnect();
    }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

    // Clean up typing timer on unmount
    useEffect(() => {
        return () => {
            if (typingTimerRef.current) clearTimeout(typingTimerRef.current);
        };
    }, []);

    const handleSend = async () => {
        const content = inputValue.trim();
        if (!content || isPending) return;
        setInputValue('');
        await sendMessage({ content, messageType: 'TEXT' });
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setInputValue(e.target.value);
        sendTyping(conversationId, true);
        if (typingTimerRef.current) clearTimeout(typingTimerRef.current);
        typingTimerRef.current = setTimeout(() => sendTyping(conversationId, false), 2000);
    };

    const handleInputBlur = () => {
        sendTyping(conversationId, false);
        if (typingTimerRef.current) clearTimeout(typingTimerRef.current);
    };

    return (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            {/* Header */}
            <Box sx={{ display: 'flex', alignItems: 'center', px: 2, py: 1, borderBottom: 1, borderColor: 'divider' }}>
                <IconButton sx={{ display: { md: 'none' } }} onClick={onBack}>
                    <ArrowBackIcon />
                </IconButton>
                <Avatar sx={{ width: 36, height: 36, mr: 1 }}>
                    {conversationDisplayName[0]?.toUpperCase()}
                </Avatar>
                <Typography variant="subtitle1" fontWeight={600}>{conversationDisplayName}</Typography>
                <Box flex={1} />
                <IconButton>
                    <InfoOutlinedIcon />
                </IconButton>
            </Box>

            {/* Message list — column-reverse keeps newest at bottom without scrollTo */}
            <Box sx={{ flex: 1, overflow: 'auto', p: 2, display: 'flex', flexDirection: 'column-reverse', gap: 1 }}>
                {/* TypingIndicator: first in DOM = visual bottom */}
                <TypingIndicator typingUsernames={typingUsernames} />

                {isLoading ? (
                    <Box display="flex" justifyContent="center" py={4}>
                        <CircularProgress size={24} />
                    </Box>
                ) : (
                    messages.map(message => (
                        <MessageBubble
                            key={message.id}
                            message={message}
                            isOwn={message.senderId === profile?.user.id}
                        />
                    ))
                )}

                {/* Sentinel: last in DOM = visual top — triggers fetchNextPage when scrolled up */}
                <Box ref={sentinelRef} sx={{ minHeight: 1 }}>
                    {isFetchingNextPage && (
                        <Box display="flex" justifyContent="center" py={1}>
                            <CircularProgress size={24} />
                        </Box>
                    )}
                </Box>
            </Box>

            {/* Input bar */}
            <Box sx={{ display: 'flex', alignItems: 'center', px: 2, py: 1, borderTop: 1, borderColor: 'divider', gap: 1 }}>
                <IconButton size="small">
                    <EmojiEmotionsOutlinedIcon />
                </IconButton>
                <IconButton size="small">
                    <AttachFileIcon />
                </IconButton>
                <TextField
                    multiline
                    maxRows={4}
                    fullWidth
                    size="small"
                    placeholder="Message..."
                    variant="outlined"
                    value={inputValue}
                    onChange={handleInputChange}
                    onKeyDown={handleKeyDown}
                    onBlur={handleInputBlur}
                    sx={{ '& .MuiOutlinedInput-root': { borderRadius: 4 } }}
                />
                <IconButton color="primary" onClick={handleSend} disabled={!inputValue.trim() || isPending}>
                    <SendIcon />
                </IconButton>
            </Box>
        </Box>
    );
}

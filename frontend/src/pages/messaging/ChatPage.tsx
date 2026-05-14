import { useEffect, useRef, useState } from 'react';
import { Avatar, Box, CircularProgress, IconButton, TextField, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import EmojiEmotionsOutlinedIcon from '@mui/icons-material/EmojiEmotionsOutlined';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import CloseIcon from '@mui/icons-material/Close';
import SendIcon from '@mui/icons-material/Send';
import { useQueryClient } from '@tanstack/react-query';
import { messagingApi } from '../../api/messagingApi';
import { mediaApi } from '../../api/mediaApi';
import { useMessages } from '../../hooks/messaging/useMessages';
import { useSendMessage } from '../../hooks/messaging/useSendMessage';
import { useWebSocketContext } from '../../context/WebSocketContext';
import { useAuth } from '../../hooks/useAuth';
import { MessageBubble } from '../../components/messaging/MessageBubble';
import { TypingIndicator } from '../../components/messaging/TypingIndicator';

const MINIO_BASE = 'http://localhost:9000/instagram-media';

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
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [pendingFile, setPendingFile] = useState<File | null>(null);
    const [previewUrl, setPreviewUrl] = useState<string | null>(null);
    const [isUploading, setIsUploading] = useState(false);

    const { messages, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = useMessages(conversationId);
    const { sendMessage, isPending } = useSendMessage(conversationId);
    const { sendTyping, typingUserIds, setActiveConversationId } = useWebSocketContext();

    // Tells the WebSocket context which conversation is open so incoming messages are routed correctly
    useEffect(() => {
        setActiveConversationId(conversationId);
        return () => setActiveConversationId(null);
    }, [conversationId, setActiveConversationId]);

    const senderMap = new Map(messages.map(m => [m.senderId, m.senderUsername]));
    const typingUsernames = typingUserIds
        .filter(id => id !== profile?.user.id)
        .map(id => senderMap.get(id) ?? id);

    const conversationDisplayName = messages.find(m => m.senderId !== profile?.user.id)?.senderUsername ?? 'Conversation';

    const latestMessage = messages[0];
    // Marks the conversation as read whenever a new incoming message arrives (covers both initial load and live arrivals)
    useEffect(() => {
        if (!latestMessage || latestMessage.senderId === profile?.user.id) return;
        messagingApi.markRead(conversationId, latestMessage.id).then(() => {
            queryClient.invalidateQueries({ queryKey: ['conversations'] });
        });
    }, [conversationId, latestMessage?.id, profile?.user.id, queryClient]);

    // Watches a sentinel element at the visual top of the list; triggers the next page fetch when it scrolls into view
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

    // Cleanup on unmount: cancels the pending typing timer and releases any object URL held for file preview
    useEffect(() => {
        return () => {
            if (typingTimerRef.current) clearTimeout(typingTimerRef.current);
            if (previewUrl) URL.revokeObjectURL(previewUrl);
        };
    }, []);

    // Called when the user picks a file — stores the File object and creates a local object URL for preview
    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        setPendingFile(file);
        setPreviewUrl(URL.createObjectURL(file));
        e.target.value = ''; // reset so the same file can be selected again
    };

    // Discards the pending file selection and releases the object URL to free memory
    const clearPendingFile = () => {
        if (previewUrl) URL.revokeObjectURL(previewUrl);
        setPendingFile(null);
        setPreviewUrl(null);
    };

    // Sends either a media message (if a file is pending) or a text message.
    // For media: gets a pre-signed PUT URL from the backend, uploads directly to MinIO,
    // then sends the message with the public MinIO URL and the derived messageType.
    const handleSend = async () => {
        if (isPending || isUploading) return;

        if (pendingFile) {
            setIsUploading(true);
            try {
                const { presignedUrl, mediaKey } = await mediaApi.getUploadUrl(pendingFile.name, pendingFile.type);
                await mediaApi.uploadToMinio(presignedUrl, pendingFile);
                const messageType = pendingFile.type.startsWith('video/') ? 'VIDEO' : 'IMAGE';
                await sendMessage({ content: null, messageType, mediaUrl: `${MINIO_BASE}/${mediaKey}` });
                clearPendingFile();
            } finally {
                setIsUploading(false);
            }
            return;
        }

        const content = inputValue.trim();
        if (!content) return;
        setInputValue('');
        await sendMessage({ content, messageType: 'TEXT' });
    };

    // Submits the message on Enter; Shift+Enter inserts a newline instead
    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    // Updates input value and broadcasts a typing event; stops the indicator after 2 s of inactivity
    const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setInputValue(e.target.value);
        sendTyping(conversationId, true);
        if (typingTimerRef.current) clearTimeout(typingTimerRef.current);
        typingTimerRef.current = setTimeout(() => sendTyping(conversationId, false), 2000);
    };

    // Stops the typing indicator immediately when the user leaves the input field
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

                <Box ref={sentinelRef} sx={{ minHeight: 1 }}>
                    {isFetchingNextPage && (
                        <Box display="flex" justifyContent="center" py={1}>
                            <CircularProgress size={24} />
                        </Box>
                    )}
                </Box>
            </Box>

            {/* File preview strip */}
            {pendingFile && previewUrl && (
                <Box sx={{ px: 2, pb: 1, display: 'flex', alignItems: 'center', gap: 1, borderTop: 1, borderColor: 'divider' }}>
                    {pendingFile.type.startsWith('video/') ? (
                        <Typography variant="caption" sx={{ flex: 1 }} noWrap>{pendingFile.name}</Typography>
                    ) : (
                        <Box
                            component="img"
                            src={previewUrl}
                            sx={{ height: 64, borderRadius: 1, objectFit: 'cover' }}
                        />
                    )}
                    {isUploading && <CircularProgress size={16} />}
                    <IconButton size="small" onClick={clearPendingFile} disabled={isUploading}>
                        <CloseIcon fontSize="small" />
                    </IconButton>
                </Box>
            )}

            {/* Input bar */}
            <Box sx={{ display: 'flex', alignItems: 'center', px: 2, py: 1, borderTop: 1, borderColor: 'divider', gap: 1 }}>
                <IconButton size="small">
                    <EmojiEmotionsOutlinedIcon />
                </IconButton>
                <IconButton size="small" onClick={() => fileInputRef.current?.click()}>
                    <AttachFileIcon />
                </IconButton>
                <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*,video/*"
                    style={{ display: 'none' }}
                    onChange={handleFileChange}
                />
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
                <IconButton
                    color="primary"
                    onClick={handleSend}
                    disabled={(!inputValue.trim() && !pendingFile) || isPending || isUploading}
                >
                    <SendIcon />
                </IconButton>
            </Box>
        </Box>
    );
}

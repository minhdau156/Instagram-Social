import { Box, Divider, IconButton, List, Skeleton, Typography } from "@mui/material"
import EditIcon from "@mui/icons-material/Edit";
import GroupAddIcon from "@mui/icons-material/GroupAdd";
import { useConversations } from "../../hooks/messaging/useConversations";
import { ConversationListItem } from "../../components/messaging/ConversationListItem";
import { NewConversationDialog } from "../../components/messaging/NewConversationDialog";
import { GroupChatDialog } from "../../components/messaging/GroupChatDialog";
import { useState } from "react";
import ChatPage from "./ChatPage";

export default function InboxPage() {
    const { conversations, isLoading, isError } = useConversations();
    const [selectedConversationId, setSelectedConversationId] = useState<string | null>(null);
    const [isClick, setIsClick] = useState(false);
    const [newConvOpen, setNewConvOpen] = useState(false);
    const [groupChatOpen, setGroupChatOpen] = useState(false);

    return (
        <Box sx={{ display: 'flex', height: 'calc(100vh - 64px)', overflow: 'hidden' }}>
            <Box sx={{ width: { xs: '100%', md: 360 }, borderRight: 1, borderColor: 'divider', overflow: 'auto' }}>
                <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Typography variant="h6">Messages</Typography>
                    <IconButton onClick={() => setNewConvOpen(true)}>
                        <EditIcon />
                    </IconButton>
                    <IconButton onClick={() => setGroupChatOpen(true)}>
                        <GroupAddIcon />
                    </IconButton>
                </Box>

                <NewConversationDialog open={newConvOpen} onClose={() => setNewConvOpen(false)} />
                <GroupChatDialog open={groupChatOpen} onClose={() => setGroupChatOpen(false)} />
                <Divider />

                {isLoading ? (
                    Array.from({ length: 5 }).map((_, idx) => (
                        <Skeleton key={idx} variant="rectangular" height={72} sx={{ mb: 1 }} />
                    ))
                ) : isError ? (
                    <Box sx={{ p: 2 }}>
                        <Typography>Error loading conversations</Typography>
                    </Box>
                ) : conversations?.length === 0 ? (
                    <Box sx={{ p: 2 }}>
                        <Typography>No conversations yet</Typography>
                    </Box>
                ) : (
                    <List>
                        {conversations?.map((conversation) => (
                            <ConversationListItem
                                conversation={conversation}
                                onClick={() => {
                                    setSelectedConversationId(conversation.id);
                                    setIsClick(true);

                                }}
                                isClick={isClick}
                                isSelected={selectedConversationId === conversation.id}
                            />
                        ))}
                    </List>
                )}
            </Box>
            <Box sx={{ flex: 1, display: { xs: 'none', md: 'flex' } }}>
                {selectedConversationId === null ? (
                    <Box sx={{ p: 2 }}>
                        <Typography>Select a conversation to start chatting</Typography>
                    </Box>
                ) : (
                    <ChatPage conversationId={selectedConversationId || ""} />
                )}
            </Box>
        </Box >
    )
}
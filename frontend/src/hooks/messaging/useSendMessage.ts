import { InfiniteData, useMutation, useQueryClient } from "@tanstack/react-query"
import { messagingApi } from "../../api/messagingApi"
import { Message, SendMessagePayload } from "../../types/messaging";
import { useAuth } from "../useAuth";

export const useSendMessage = (conversationId: string) => {
    const { profile } = useAuth()
    const queryClient = useQueryClient();
    const { mutateAsync: sendMessage, isPending } = useMutation({
        mutationFn: (payload: SendMessagePayload) => {
            return messagingApi.sendMessage(conversationId, payload);
        },
        onMutate: async (payload: SendMessagePayload) => {
            await queryClient.cancelQueries({ queryKey: ['messages', conversationId] });
            const snapshot = queryClient.getQueryData<InfiniteData<Message[]>>(['messages', conversationId]);

            const optimisticMessage: Message = {
                id: `optimistic-${Date.now()}`,
                conversationId,
                senderId: profile?.user.id ?? '',
                senderUsername: profile?.user.username ?? '',
                senderAvatarUrl: profile?.user.avatarUrl ?? null,
                content: payload.content,
                messageType: payload.messageType,
                mediaUrl: payload.mediaUrl ?? null,
                sharedPostId: payload.sharedPostId ?? null,
                status: 'SENT',
                createdAt: new Date().toISOString(),
            };

            queryClient.setQueryData<InfiniteData<Message[]>>(['messages', conversationId], (old) => {
                if (!old) return old;
                const [firstPage, ...rest] = old.pages;
                return {
                    ...old,
                    pages: [[optimisticMessage, ...(firstPage ?? [])], ...rest],
                };
            });

            return { snapshot };
        },
        onError: (_err, _payload, context) => {
            if (context?.snapshot) {
                queryClient.setQueryData(['messages', conversationId], context.snapshot);
            }
        },
        onSuccess: (realMessage) => {
            queryClient.setQueryData<InfiniteData<Message[]>>(['messages', conversationId], (old) => {
                if (!old) return old;
                return {
                    ...old,
                    pages: old.pages.map(page =>
                        page.map(msg => msg.id.startsWith('optimistic-') ? realMessage : msg)
                    ),
                };
            });
        },
    });
    return { sendMessage, isPending };
}

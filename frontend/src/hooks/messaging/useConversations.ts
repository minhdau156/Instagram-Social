
import { useQuery } from "@tanstack/react-query";
import { messagingApi } from "../../api/messagingApi";

export const useConversations = () => {
    const { data: conversations, isLoading, isError } = useQuery({
        queryKey: ['conversations'],
        queryFn: async () => {
            return messagingApi.getConversations();
        },
        staleTime: 30 * 1000,
    })

    return { conversations, isLoading, isError }
}

import { useQuery } from "@tanstack/react-query";
import { messagingApi } from "../../api/messagingApi";
import { useAuth } from "../useAuth";

export const useConversations = () => {
    const { isAuthenticated } = useAuth();
    const { data: conversations, isLoading, isError } = useQuery({
        queryKey: ['conversations'],
        queryFn: async () => {
            return messagingApi.getConversations();
        },
        staleTime: 30 * 1000,
        enabled: isAuthenticated,
    })

    return { conversations, isLoading, isError }
}
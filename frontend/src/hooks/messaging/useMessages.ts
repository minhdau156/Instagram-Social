import { messagingApi } from "../../api/messagingApi";
import { useInfiniteQuery } from "@tanstack/react-query";

export const useMessages = (conversationId: string) => {
    const { data, isLoading, isError, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
        queryKey: ['messages', conversationId],
        queryFn: async ({ pageParam }) => {
            return messagingApi.getMessages(conversationId, pageParam);
        },
        initialPageParam: undefined as string | undefined,
        getNextPageParam: (lastPage) => {
            if (lastPage.length < 20) {
                return undefined;
            }
            return lastPage[lastPage.length - 1].id;
        },
        staleTime: 30 * 1000,
        enabled: !!conversationId,
    })
    const messages = data?.pages.flatMap(page => page) ?? [];
    return { messages, isLoading, isError, fetchNextPage, hasNextPage, isFetchingNextPage }
}
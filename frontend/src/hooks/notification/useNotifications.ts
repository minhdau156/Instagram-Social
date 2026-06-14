import { useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { notificationsApi } from "../../api/notificationsApi";
import { useAuth } from "../useAuth";
import { useUnreadNotifications } from "./useUnreadNotifications";

export const useNotifications = () => {
    const { isAuthenticated } = useAuth();
    const { setUnreadCountNotification } = useUnreadNotifications();
    const queryClient = useQueryClient();
    const { data, isError, isLoading, isFetchingNextPage, fetchNextPage, hasNextPage } = useInfiniteQuery({
        queryKey: ['notifications'],
        queryFn: async ({ pageParam = null }) => {
            return notificationsApi.getNotifications(pageParam as string | null)
        },
        initialPageParam: null as string | null,
        getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
        enabled: isAuthenticated,
    });
    const notifications = data?.pages.flatMap(page => page.items) || [];

    const markRead = useMutation({
        mutationFn: ({ id }: { id: string }) => notificationsApi.markRead(id),

        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['notifications'] });
            setUnreadCountNotification(prevCount => prevCount - 1);
        },
    })

    const markAllRead = useMutation({
        mutationFn: () => notificationsApi.markAllRead(),
        onMutate: async () => {
            await queryClient.cancelQueries({ queryKey: ['notifications'] });
            const previous = queryClient.getQueryData(['notifications']);
            queryClient.setQueryData(['notifications'], (old: any) => {
                return {
                    pages: old.pages.map((page: any) => ({
                        ...page,
                        items: page.items.map((notification: any) => ({ ...notification, isRead: true }))
                    })),
                    pageParams: old.pageParams
                };
            });
            return { previous };
        },
        onError: (_, context: any) => {
            queryClient.setQueryData(['notifications'], context?.previous);
        },
        onSuccess: () => {
            setUnreadCountNotification(0);
            queryClient.invalidateQueries({ queryKey: ['notifications'] });
        },
    })

    return {
        notifications,
        isError,
        isLoading,
        isFetchingNextPage,
        fetchNextPage,
        hasNextPage,
        markRead,
        markAllRead
    }
}
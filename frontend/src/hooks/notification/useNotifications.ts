import { useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { notificationsApi } from "../../api/notificationsApi";

export const useNotifications = () => {
    const queryClient = useQueryClient();
    const { data, isError, isLoading, isFetchingNextPage, fetchNextPage, hasNextPage } = useInfiniteQuery({
        queryKey: ['notifications'],
        queryFn: async ({ pageParam = 0 }) => {
            return notificationsApi.getNotifications(pageParam)
        },
        initialPageParam: 0,
        getNextPageParam: (lastPage, allPages) =>
            lastPage.length < 20 ? undefined : allPages.length
    });
    const notifications = data?.pages.flat() || [];

    const markRead = useMutation({
        mutationFn: ({ id }: { id: string }) => notificationsApi.markRead(id),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['notifications'] });
        },
    })

    const markAllRead = useMutation({
        mutationFn: () => notificationsApi.markAllRead(),
        onMutate: async () => {
            await queryClient.cancelQueries({ queryKey: ['notifications'] });
            const previous = queryClient.getQueryData(['notifications']);
            queryClient.setQueryData(['notifications'], (old: any) => {
                return {
                    pages: old.pages.map((page: any) =>
                        page.map((notification: any) => ({ ...notification, isRead: true }))
                    ),
                    pageParams: old.pageParams
                };
            });
            return { previous };
        },
        onError: (_, context: any) => {
            queryClient.setQueryData(['notifications'], context?.previous);
        },
        onSuccess: () => {
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
import { useInfiniteQuery } from "@tanstack/react-query";
import { getSavedPosts } from "../../api/savesApi";

/**
 * @param userId - used only to scope the cache key per account;
 *                 the API always fetches the authenticated user's saves.
 */
export function useGetSavedPosts(userId: string) {
    return useInfiniteQuery({
        // ['savedPosts', user_<userId>] — scoped per-user to bust cache on account switch
        queryKey: ['savedPosts', `user_${userId}`],
        queryFn: ({ pageParam = 0 }) => getSavedPosts(pageParam, 20),
        getNextPageParam: (lastPage) =>
            lastPage.last ? undefined : lastPage.page + 1,
        initialPageParam: 0,
    });
}
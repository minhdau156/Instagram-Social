import { useInfiniteQuery } from "@tanstack/react-query"
import { postApi } from "../../api/postApi";

export const usePosts = (userId: string) => {
    return useInfiniteQuery({
        queryKey: ['posts', userId],
        queryFn: ({ pageParam = 0 }) => postApi.getUserPosts(userId, pageParam, 12),
        getNextPageParam: (lastPage) => {
            if (lastPage.last) return undefined;
            return lastPage.page + 1;
        },
        initialPageParam: 0,
    })
}
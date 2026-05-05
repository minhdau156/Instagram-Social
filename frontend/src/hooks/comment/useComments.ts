import { useInfiniteQuery } from "@tanstack/react-query";
import { getComments } from "../../api/commentsApi";

export function useComments(postId: string) {
    return useInfiniteQuery({
        // ['comments', postId] — all root-level comments for a single post
        queryKey: ['comments', postId],
        queryFn: ({ pageParam = 0 }) => getComments(postId, pageParam, 20),
        getNextPageParam: (lastPage) =>
            lastPage.last ? undefined : lastPage.page + 1,
        initialPageParam: 0,
    });
}
import { useInfiniteQuery } from "@tanstack/react-query";
import { getComments } from "../../api/commentsApi";

export function useComments(postId: string) {
    return useInfiniteQuery({
        // ['comments', postId] — all root-level comments for a single post
        queryKey: ['comments', postId],
        queryFn: ({ pageParam }) => getComments(postId, pageParam as string | null),
        getNextPageParam: (lastPage) =>
            lastPage.nextCursor ?? undefined,
        initialPageParam: null as string | null,
    });
}
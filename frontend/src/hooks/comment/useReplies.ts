import { useInfiniteQuery } from "@tanstack/react-query";
import { getReplies } from "../../api/commentsApi";

export function useReplies(commentId: string, enabled: boolean = true) {
    return useInfiniteQuery({
        // ['replies', commentId] — all replies for a single comment
        queryKey: ['replies', commentId],
        queryFn: ({ pageParam = 0 }) => getReplies(commentId, pageParam, 10),
        getNextPageParam: (lastPage) =>
            lastPage.last ? undefined : lastPage.page + 1,
        initialPageParam: 0,
        enabled,
    });
}

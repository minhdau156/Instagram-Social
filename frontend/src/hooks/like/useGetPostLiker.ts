import { useInfiniteQuery } from "@tanstack/react-query";
import { getPostLikers } from "../../api/likesApi";

export function useGetPostLikers(postId: string) {
    return useInfiniteQuery({
        queryKey: ["post", postId, "likers"],
        queryFn: ({ pageParam = 0 }) => getPostLikers(postId, pageParam),
        getNextPageParam: (lastPage) =>
            lastPage.last ? undefined : lastPage.page + 1,
        initialPageParam: 0,
    });
}
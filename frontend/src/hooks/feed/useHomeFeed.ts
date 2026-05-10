import { useInfiniteQuery } from "@tanstack/react-query";
import { getHomeFeed } from "../../api/feedApi";
import type { FeedPage } from "../../types/post";

export function useHomeFeed() {
    return useInfiniteQuery<FeedPage, Error, FeedPage, string[], string | undefined>({
        queryKey: ['homeFeed'],
        queryFn: ({ pageParam }) => getHomeFeed(pageParam),
        initialPageParam: undefined,
        getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
        select: (data) => ({
            pages: data.pages,
            posts: data.pages.flatMap(p => p.posts),
            nextCursor: data.pages[data.pages.length - 1]?.nextCursor ?? null,
        }),
        staleTime: 60_000,
        refetchOnWindowFocus: true,
    });
}

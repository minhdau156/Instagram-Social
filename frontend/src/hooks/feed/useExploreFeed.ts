import { useInfiniteQuery } from "@tanstack/react-query";
import type { FeedPage } from "../../types/post";
import { getExploreFeed } from "../../api/feedApi";

export function useExploreFeed() {
    return useInfiniteQuery<FeedPage, Error, FeedPage, string[], string | undefined>({
        queryKey: ['exploreFeed'],
        queryFn: ({ pageParam }) => getExploreFeed(pageParam),
        initialPageParam: undefined,
        getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
        select: (data) => ({
            posts: data.pages.flatMap(p => p.posts),
            nextCursor: data.pages.at(-1)?.nextCursor ?? null,
        }),
        staleTime: 120_000,
        refetchOnWindowFocus: false,
    });
}

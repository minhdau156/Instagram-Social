import { useInfiniteQuery } from "@tanstack/react-query";
import type { FeedPage } from "../../types/post";
import { getExploreFeed } from "../../api/feedApi";

export function useExploreFeed() {
    return useInfiniteQuery<FeedPage, Error, FeedPage, string[], string | undefined>({
        queryKey: ['exploreFeed'],
        queryFn: ({ pageParam }) => getExploreFeed(pageParam),

        // pageParam is the cursor from the previous page's response.
        // undefined means "first page" (no cursor).
        initialPageParam: undefined,

        // Return undefined to stop fetching when there's no more cursor.
        getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,

        staleTime: 120_000,        // 120 seconds — explore content changes less frequently — 2 min is fine
        refetchOnWindowFocus: false, // explore doesn't need live updates
    });
}
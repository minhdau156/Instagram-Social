import { useInfiniteQuery } from "@tanstack/react-query";
import { getHomeFeed } from "../../api/feedApi";
import type { FeedPage } from "../../types/post";

export function useHomeFeed() {
    return useInfiniteQuery<FeedPage, Error, FeedPage, string[], string | undefined>({
        queryKey: ['homeFeed'],
        queryFn: ({ pageParam }) => getHomeFeed(pageParam),

        // pageParam is the cursor from the previous page's response.
        // undefined means "first page" (no cursor).
        initialPageParam: undefined,

        // Return undefined to stop fetching when there's no more cursor.
        getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,

        staleTime: 60_000,        // 60 seconds — don't refetch if data is fresh
        refetchOnWindowFocus: true,
    });
} 
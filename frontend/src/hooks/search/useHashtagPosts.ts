import { useInfiniteQuery } from "@tanstack/react-query"
import { searchApi } from "../../api/searchApi";

export const useHashtagPosts = (hashtagName: string) => {
    const { data, isLoading, isFetchingNextPage, hasNextPage, fetchNextPage, isError } = useInfiniteQuery({
        queryKey: ['hashtag-posts', hashtagName],
        queryFn: ({ pageParam = 0 }) => searchApi.getPostsByHashtag(hashtagName, pageParam, 20),
        initialPageParam: 0,
        getNextPageParam: (lastPage, allPages) => {
            return lastPage.length === 20 ? allPages.length : undefined;
        },
        enabled: hashtagName.length > 0,
        staleTime: 60_000,
    })

    const posts = data?.pages.flat() ?? [];
    return { posts, isLoading, isFetchingNextPage, hasNextPage, fetchNextPage, isError };
}
import { useEffect, useState } from "react";
import { searchApi } from "../../api/searchApi";
import type { HashtagSearchResult, PostSearchResult, SearchType, UserSearchResult } from "../../types/search";
import { useQuery } from "@tanstack/react-query";

export const useSearch = (q: string, type: SearchType, page = 0, size = 20) => {
    const [debouncedQ, setDebouncedQ] = useState(q);

    useEffect(() => {
        const timer = setTimeout(() => setDebouncedQ(q), 300);
        return () => clearTimeout(timer);
    }, [q])

    const { data, isLoading, isError, isFetching } = useQuery<UserSearchResult[] | HashtagSearchResult[] | PostSearchResult[]>({
        queryKey: ['search', debouncedQ, type, page],
        queryFn: () => {
            if (type === 'users') {
                return searchApi.searchUsers(debouncedQ, page, size)
            } else if (type === 'hashtags') {
                return searchApi.searchHashtags(debouncedQ, page, size)
            } else {
                return searchApi.searchPosts(debouncedQ, page, size)
            }
        },
        enabled: debouncedQ.trim().length > 0,
        staleTime: 30_000,
    })

    const results = data ?? [];
    return { results, isLoading, isError, isFetching }
}
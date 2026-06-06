import { useInfiniteQuery } from "@tanstack/react-query";
import { getFollowers } from "../../api/followApi";
import { followKeys } from "./queryKeys";

export function useFollowers(username: string) {
    return useInfiniteQuery({
        queryKey: followKeys.followers(username),
        queryFn: ({ pageParam = null }) => getFollowers(username, pageParam as string | null),
        getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
        initialPageParam: null as string | null,
    });
}
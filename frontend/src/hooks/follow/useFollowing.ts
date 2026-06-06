import { useInfiniteQuery } from "@tanstack/react-query";
import { getFollowing } from "../../api/followApi";
import { followKeys } from "./queryKeys";

export function useFollowing(username: string) {
    return useInfiniteQuery({
        queryKey: followKeys.following(username),
        queryFn: ({ pageParam = null }) => getFollowing(username, pageParam as string | null),
        getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
        initialPageParam: null as string | null,
    });
}
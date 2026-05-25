import { useQuery } from "@tanstack/react-query";
import { moderationApi } from "../../api/moderationApi";
import type { UserBlock } from "../../types/moderation";

export const useBlockedUsers = () => {
    const { data, isLoading, isError } = useQuery<UserBlock[]>({
        queryKey: ['blocked-users'],
        queryFn: () => moderationApi.getBlockedUsers(0, 100),
        staleTime: 60_000,
    });
    return { blockedUsers: data ?? [], isLoading, isError };
};

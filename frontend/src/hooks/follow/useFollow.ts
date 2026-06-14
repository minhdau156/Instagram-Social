import { InfiniteData, useMutation, useQueryClient } from "@tanstack/react-query";
import { followUser, unfollowUser } from "../../api/followApi";
import type { UserProfile } from "../../types/user";
import { followKeys } from "./queryKeys";
import { FollowStatus, UserSummary } from "../../types/follow";

export const useFollow = (username: string) => {
    const queryClient = useQueryClient();
    const profileKey = followKeys.profile(username);

    const followMutation = useMutation({
        mutationFn: () => followUser(username),
        onMutate: async () => {
            await queryClient.cancelQueries({ queryKey: profileKey });
            const snapshot = queryClient.getQueryData<UserProfile>(profileKey);

            // Apply optimistic update to profile data
            if (snapshot) {
                const newStatus = snapshot.user.isPrivate ? FollowStatus.PENDING : FollowStatus.ACCEPTED;
                queryClient.setQueryData<UserProfile>(profileKey, {
                    ...snapshot,
                    isFollowing: newStatus === FollowStatus.ACCEPTED,
                    followStatus: newStatus,
                    followerCount: newStatus === FollowStatus.ACCEPTED ? snapshot.followerCount + 1 : snapshot.followerCount,
                });

            }

            // Optimistically update the user's status in ALL follower and following lists
            const updateList = (oldData: any | undefined) => {
                if (!oldData) return oldData;

                const items = oldData.items || [];
                return {
                    ...oldData,
                    items: items.map((user: UserSummary) => {
                        if (user.username === username) {
                            return {
                                ...user,
                                followStatus: user.isPrivate ? FollowStatus.PENDING : FollowStatus.ACCEPTED,
                            };
                        }
                        return user;
                    }
                    ),
                };
            };
            queryClient.setQueriesData({ queryKey: ['followers'] }, updateList);
            queryClient.setQueriesData({ queryKey: ['following'] }, updateList);

            return { snapshot };
        },
        onError: (_err, _vars, context) => {
            queryClient.setQueryData(profileKey, context?.snapshot);
        },
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: profileKey });
            queryClient.invalidateQueries({ queryKey: followKeys.followers(username) });
            queryClient.invalidateQueries({ queryKey: followKeys.following(username) });
        },

    });

    const unfollowMutation = useMutation({
        mutationFn: () => unfollowUser(username),
        onMutate: async () => {
            await queryClient.cancelQueries({ queryKey: profileKey });
            const snapshot = queryClient.getQueryData<UserProfile>(profileKey);

            // Apply optimistic update to profile data
            if (snapshot) {
                queryClient.setQueryData<UserProfile>(profileKey, {
                    ...snapshot,
                    isFollowing: false,
                    followStatus: null,
                    followerCount: snapshot.followStatus === FollowStatus.ACCEPTED ? Math.max(0, snapshot.followerCount - 1) : snapshot.followerCount,
                });
            }

            // Optimistically update the user's status in ALL follower and following lists
            const updateList = (oldData: any | undefined) => {
                if (!oldData) return oldData;

                const items = oldData.items || [];

                return {
                    ...oldData,
                    items: items.map((user: UserSummary) => {
                        if (user.username === username) {
                            return {
                                ...user,
                                followStatus: user.isPrivate ? FollowStatus.PENDING : FollowStatus.ACCEPTED,
                            };
                        }
                        return user;
                    }
                    ),
                };
            };
            queryClient.setQueriesData({ queryKey: ['followers'] }, updateList);
            queryClient.setQueriesData({ queryKey: ['following'] }, updateList);

            return { snapshot };
        },
        onError: (_err, _vars, context) => {
            queryClient.setQueryData(profileKey, context?.snapshot);
        },
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: profileKey });
            queryClient.invalidateQueries({ queryKey: followKeys.followers(username) });
            queryClient.invalidateQueries({ queryKey: followKeys.following(username) });
        },
    });

    return { followMutation, unfollowMutation };
};
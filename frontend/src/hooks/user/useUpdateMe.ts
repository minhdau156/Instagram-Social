import { useMutation } from "@tanstack/react-query";
import { usersApi } from "../../api/usersApi";
import { useAuth } from "../useAuth";
import type { UpdateProfilePayload, UserProfile } from "../../types/user";

export const useUpdateMe = () => {
    const { profile, setProfile } = useAuth();

    return useMutation({
        mutationFn: (payload: UpdateProfilePayload) => usersApi.updateMe(payload),
        onMutate: (payload) => {
            const previousProfile = profile;
            if (previousProfile) {
                setProfile({ ...previousProfile, user: { ...previousProfile.user, ...payload } });
            }
            return { previousProfile };
        },
        onError: (_err, _payload, context) => {
            if (context?.previousProfile) {
                setProfile(context.previousProfile);
            }
        },
        onSettled: (updatedUser) => {
            if (updatedUser) {
                setProfile((prev: UserProfile | null) => prev ? { ...prev, user: updatedUser } : prev);
            }
        },
    });
};

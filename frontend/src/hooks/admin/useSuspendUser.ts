import { useMutation, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "../../api/adminApi";
import type { SuspendUserPayload } from "../../types/moderation";
import { toast } from "react-toastify";

export const useSuspendUser = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ id, payload }: { id: string; payload: SuspendUserPayload }) =>
            adminApi.suspendUser(id, payload),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['admin-users'] });
            toast.success("User suspended successfully.");
        },
        onError: () => {
            toast.error("Failed to suspend user.");
        },
    });
};

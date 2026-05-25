import { useMutation, useQueryClient } from "@tanstack/react-query";
import { moderationApi } from "../../api/moderationApi";
import { toast } from "react-toastify";

export const useUnblockUser = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (username: string) => moderationApi.unblockUser(username),
        onSuccess: (_, username) => {
            queryClient.invalidateQueries({ queryKey: ['blocked-users'] });
            queryClient.invalidateQueries({ queryKey: ['users', username] });
            toast.success("User unblocked successfully.");
        },
        onError: () => {
            toast.error("Failed to unblock user.");
        },
    });
}
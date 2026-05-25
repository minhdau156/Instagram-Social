import { useMutation, useQueryClient } from "@tanstack/react-query";
import { moderationApi } from "../../api/moderationApi";
import { toast } from "react-toastify";

export const useBlockUser = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (username: string) => moderationApi.blockUser(username),
        onSuccess: (_, username) => {
            queryClient.invalidateQueries({ queryKey: ['users', username] });
            toast.success("User blocked successfully.");
        },
        onError: () => {
            toast.error("Failed to block user.");
        },
    });
}

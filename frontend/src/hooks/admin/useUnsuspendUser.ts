import { useMutation, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "../../api/adminApi";
import { toast } from "react-toastify";

export const useUnsuspendUser = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (id: string) => adminApi.unsuspendUser(id),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['admin-users'] });
            toast.success("User unsuspended successfully.");
        },
        onError: () => {
            toast.error("Failed to unsuspend user.");
        },
    });
};

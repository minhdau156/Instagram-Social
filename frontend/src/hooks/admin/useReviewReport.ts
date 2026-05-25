import { useMutation, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "../../api/adminApi";
import type { ReviewReportPayload } from "../../types/moderation";
import { toast } from "react-toastify";

export const useReviewReport = () => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ id, payload }: { id: string; payload: ReviewReportPayload }) =>
            adminApi.reviewReport(id, payload),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['admin-reports'] });
            toast.success("Report reviewed successfully.");
        },
        onError: () => {
            toast.error("Failed to review report.");
        },
    });
};

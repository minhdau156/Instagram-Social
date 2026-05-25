import { useMutation } from "@tanstack/react-query";
import { moderationApi } from "../../api/moderationApi";
import { toast } from "react-toastify";
import type { SubmitReportPayload } from "../../types/moderation";

export const useSubmitReport = () => {
    return useMutation({
        mutationFn: (payload: SubmitReportPayload) => moderationApi.submitReport(payload),
        onSuccess: () => {
            toast.success("Report submitted. Thank you.");
        },
        onError: () => {
            toast.error("Something went wrong. Please try again.");
        },
    });
}
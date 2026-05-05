import { useQueryClient, useMutation } from "@tanstack/react-query";
import { editComment } from "../../api/commentsApi";
import { EditCommentPayload } from "../../types/comment";

export function useEditComment(commentId: string, postId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (payload: EditCommentPayload) => editComment(commentId, payload),

        onSuccess: () => {
            // Invalidate to refresh the comment list
            queryClient.invalidateQueries({ queryKey: ['comments', postId] });
        },
    });
}
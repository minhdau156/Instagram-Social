import { useQueryClient, useMutation } from "@tanstack/react-query";
import { addComment } from "../../api/commentsApi";
import { AddCommentPayload } from "../../types/comment";

export function useAddComment(postId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (payload: AddCommentPayload) => addComment(postId, payload),

        onSuccess: () => {
            // Invalidate to refresh the comment list
            queryClient.invalidateQueries({ queryKey: ['comments', postId] });
            // Also invalidate post to update comment_count
            queryClient.invalidateQueries({ queryKey: ['post', postId] });
        },
    });
}

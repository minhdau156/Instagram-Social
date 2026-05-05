import { useQueryClient, useMutation } from "@tanstack/react-query";

import { deleteComment } from "../../api/commentsApi";

export function useDeleteComment(commentId: string, postId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: () => deleteComment(commentId),

        onSuccess: () => {
            // Invalidate to refresh the comment list
            queryClient.invalidateQueries({ queryKey: ['comments', postId] });
            // Also invalidate post to update comment_count
            queryClient.invalidateQueries({ queryKey: ['post', postId] });
        },
    });
}
import { useQueryClient, useMutation } from "@tanstack/react-query";
import { unsavePost, savePost } from "../../api/savesApi";
import { Post } from "../../types/post";

export function useSavePost(postId: string, userId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (saved: boolean) =>
            saved ? unsavePost(postId) : savePost(postId),

        onMutate: async (saved: boolean) => {
            await queryClient.cancelQueries({ queryKey: ['post', postId] });
            const previous = queryClient.getQueryData<Post>(['post', postId]);

            queryClient.setQueryData<Post>(['post', postId], (old) =>
                old ? { ...old, savedByCurrentUser: !saved } : old
            );

            return { previous };
        },

        onError: (_err, _saved, context) => {
            if (context?.previous) {
                queryClient.setQueryData(['post', postId], context.previous);
            }
        },

        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ['post', postId] });
            // Also invalidate saved posts list
            queryClient.invalidateQueries({ queryKey: ['savedPosts', `user_${userId}`] });
        },
    });
}
import { useQueryClient, useMutation } from "@tanstack/react-query";
import { unlikePost, likePost } from "../../api/likesApi";
import { Post } from "../../types/post";

export function useLikePost(postId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (liked: boolean) =>
            liked ? unlikePost(postId) : likePost(postId),

        // Optimistic update
        onMutate: async (liked: boolean) => {
            await queryClient.cancelQueries({ queryKey: ['post', postId] });
            const previous = queryClient.getQueryData<Post>(['post', postId]);

            queryClient.setQueryData<Post>(['post', postId], (old) =>
                old
                    ? {
                        ...old,
                        likedByCurrentUser: !liked,
                        likeCount: liked ? old.likeCount - 1 : old.likeCount + 1,
                    }
                    : old
            );

            return { previous };
        },

        // Roll back on error
        onError: (_err, _liked, context) => {
            if (context?.previous) {
                queryClient.setQueryData(['post', postId], context.previous);
            }
        },

        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ['post', postId] });
        },
    });
}
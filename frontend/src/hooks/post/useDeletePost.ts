import { useMutation, useQueryClient } from '@tanstack/react-query';
import { postApi } from '../../api/postApi';

export const useDeletePost = () => {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (postId: string) => postApi.deletePost(postId),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['posts'] });
            queryClient.invalidateQueries({ queryKey: ['homeFeed'] });
        },
    });
};

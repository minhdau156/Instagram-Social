import { useQueryClient, useMutation, InfiniteData } from "@tanstack/react-query";
import { unlikePost, likePost } from "../../api/likesApi";
import { Post, FeedPage } from "../../types/post";

function patchFeedPages(
    old: InfiniteData<FeedPage> | undefined,
    postId: string,
    liked: boolean
): InfiniteData<FeedPage> | undefined {
    if (!old) return old;
    return {
        ...old,
        pages: old.pages.map((page) => ({
            ...page,
            posts: page.posts.map((post) =>
                post.id === postId
                    ? {
                        ...post,
                        likedByCurrentUser: !liked,
                        likeCount: liked ? post.likeCount - 1 : post.likeCount + 1,
                    }
                    : post
            ),
        })),
    };
}

export function useLikePost(postId: string) {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: (liked: boolean) =>
            liked ? unlikePost(postId) : likePost(postId),

        onMutate: async (liked: boolean) => {
            await queryClient.cancelQueries({ queryKey: ['post', postId] });
            await queryClient.cancelQueries({ queryKey: ['homeFeed'] });

            const previousPost = queryClient.getQueryData<Post>(['post', postId]);
            const previousFeed = queryClient.getQueryData<InfiniteData<FeedPage>>(['homeFeed']);

            queryClient.setQueryData<Post>(['post', postId], (old) =>
                old
                    ? {
                        ...old,
                        likedByCurrentUser: !liked,
                        likeCount: liked ? old.likeCount - 1 : old.likeCount + 1,
                    }
                    : old
            );

            queryClient.setQueryData<InfiniteData<FeedPage>>(['homeFeed'], (old) =>
                patchFeedPages(old, postId, liked)
            );

            return { previousPost, previousFeed };
        },

        onError: (_err, _liked, context) => {
            if (context?.previousPost) {
                queryClient.setQueryData(['post', postId], context.previousPost);
            }
            if (context?.previousFeed) {
                queryClient.setQueryData(['homeFeed'], context.previousFeed);
            }
        },

        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ['post', postId] });
            queryClient.invalidateQueries({ queryKey: ['homeFeed'] });
        },
    });
}

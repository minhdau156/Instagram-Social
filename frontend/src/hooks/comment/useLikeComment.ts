import {
  useQueryClient,
  useMutation,
  InfiniteData,
} from "@tanstack/react-query";
import { unlikeComment, likeComment } from "../../api/likesApi";
import type { CursorPage } from "../../api/commentsApi";
import { Comment, CommentPage } from "../../types/comment";

function patchComment(comment: Comment, commentId: string, liked: boolean): Comment {
  if (comment.id !== commentId) return comment;
  return {
    ...comment,
    isLikedByCurrentUser: !liked,
    likeCount: liked ? comment.likeCount - 1 : comment.likeCount + 1,
  };
}

function patchRootComments(
  old: InfiniteData<CursorPage<Comment>> | undefined,
  commentId: string,
  liked: boolean,
): InfiniteData<CursorPage<Comment>> | undefined {
  if (!old) return old;
  return {
    ...old,
    pages: old.pages.map((page) => ({
      ...page,
      items: page.items.map((comment) => patchComment(comment, commentId, liked)),
    })),
  };
}

function patchReplies(
  old: InfiniteData<CommentPage> | undefined,
  commentId: string,
  liked: boolean,
): InfiniteData<CommentPage> | undefined {
  if (!old) return old;
  return {
    ...old,
    pages: old.pages.map((page) => ({
      ...page,
      content: page.content.map((comment) => patchComment(comment, commentId, liked)),
    })),
  };
}

export function useLikeComment(commentId: string, postId: string, parentId: string | null) {
  const queryClient = useQueryClient();
  const queryKey = parentId ? ["replies", parentId] : ["comments", postId];

  return useMutation({
    mutationFn: (liked: boolean) =>
      liked ? unlikeComment(commentId) : likeComment(commentId),

    onMutate: async (liked: boolean) => {
      await queryClient.cancelQueries({ queryKey });

      const previous = queryClient.getQueryData(queryKey);

      if (parentId) {
        queryClient.setQueryData<InfiniteData<CommentPage>>(queryKey, (old) =>
          patchReplies(old, commentId, liked),
        );
      } else {
        queryClient.setQueryData<InfiniteData<CursorPage<Comment>>>(queryKey, (old) =>
          patchRootComments(old, commentId, liked),
        );
      }

      return { previous };
    },

    onError: (_err, _liked, context) => {
      if (context?.previous) {
        queryClient.setQueryData(queryKey, context.previous);
      }
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey });
    },
  });
}

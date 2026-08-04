import { Typography, Container } from "@mui/material";
import { useParams } from "react-router-dom";
import { PostCard } from "../../components/posts/PostCard";
import { usePost } from "../../hooks/post/usePost";
import { PostSkeleton } from "../../components/posts/PostSkeleton";
import { Post } from "../../types/post";

export const PostPage: React.FC = () => {
  const { postId } = useParams<{ postId: string }>();
  const { data: post, isLoading, isError } = usePost(postId!);
  if (isError) {
    return <Typography>Post not found.</Typography>;
  }

  if (isLoading || !post) {
    return <PostSkeleton />;
  }

  return (
    <>
      <Container maxWidth="sm" sx={{ py: 4 }}>
        <PostCard post={post} />
      </Container>
    </>
  );
};

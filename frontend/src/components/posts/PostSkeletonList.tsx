import { PostSkeleton } from "./PostSkeleton";

interface PostSkeletonListProps {
    count?: number;
}

export function PostSkeletonList({ count = 3 }: PostSkeletonListProps) {
    return (
        <>
            {Array.from({ length: count }).map((_, i) => (
                <PostSkeleton key={i} />
            ))}
        </>
    );
}
import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import { Box, CircularProgress } from '@mui/material';

interface InfiniteScrollProps {
    /** Call this when the sentinel enters the viewport */
    fetchNextPage: () => void;
    /** False when the last page has nextCursor = null */
    hasNextPage: boolean;
    /** True while the next page is being fetched */
    isFetchingNextPage: boolean;
    /** The already-loaded content to display above the sentinel */
    children: ReactNode;
    /** Optional custom loader shown at the bottom. Defaults to a simple CircularProgress. */
    loader?: ReactNode;
}

export function InfiniteScroll({
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    children,
    loader,
}: InfiniteScrollProps) {
    const sentinelRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const sentinel = sentinelRef.current;
        if (!sentinel) return;

        const observer = new IntersectionObserver(
            (entries) => {
                // When the sentinel is visible and there are more pages, fetch
                if (entries[0].isIntersecting && hasNextPage && !isFetchingNextPage) {
                    fetchNextPage();
                }
            },
            {
                // Start loading slightly before the user reaches the very bottom
                rootMargin: '200px',
            }
        );

        observer.observe(sentinel);

        // Cleanup when component unmounts or deps change
        return () => observer.disconnect();
    }, [fetchNextPage, hasNextPage, isFetchingNextPage]);

    return (
        <>
            {children}

            {/* Sentinel: invisible div that triggers loading when visible */}
            <div ref={sentinelRef} style={{ height: 1 }} />

            {/* Loading indicator shown at the bottom of the list */}
            {isFetchingNextPage && (
                <Box display="flex" justifyContent="center" py={3}>
                    {loader ?? <CircularProgress size={28} />}
                </Box>
            )}
        </>
    );
}
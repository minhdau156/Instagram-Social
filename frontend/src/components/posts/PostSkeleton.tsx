import { Avatar, Box, Card, CardContent, CardHeader, Skeleton } from '@mui/material';

export function PostSkeleton() {
    return (
        <Card sx={{ mb: 2, maxWidth: 614, mx: 'auto' }}>
            {/* Header: avatar circle + two text lines (username + timestamp) */}
            <CardHeader
                avatar={<Skeleton variant="circular"><Avatar /></Skeleton>}
                title={<Skeleton width="40%" height={16} />}
                subheader={<Skeleton width="25%" height={14} />}
            />

            {/* Image area */}
            <Skeleton variant="rectangular" width="100%" height={400} />

            {/* Action buttons row */}
            <CardContent>
                <Box display="flex" gap={1} mb={1}>
                    <Skeleton variant="circular" width={28} height={28} />
                    <Skeleton variant="circular" width={28} height={28} />
                    <Skeleton variant="circular" width={28} height={28} />
                </Box>

                {/* Like count line */}
                <Skeleton width="20%" height={16} sx={{ mb: 0.5 }} />

                {/* Caption: two lines */}
                <Skeleton width="90%" height={14} />
                <Skeleton width="60%" height={14} />
            </CardContent>
        </Card>
    );
}
import { Box, Typography, List, ListItem, ListItemAvatar, Avatar, ListItemText, Button } from "@mui/material";

export function SuggestedUsers() {

    // const { data: users, isLoading } = useQuery({
    //   queryKey: ['suggestedUsers'],
    //   queryFn: () => usersApi.getSuggestedUsers(5),   // top 5 by follower count
    //   staleTime: 5 * 60_000,  // refresh every 5 minutes
    // });
    // if (isLoading) {
    //     return (
    //         <Box>
    //             <Typography variant="subtitle2" sx={{ mb: 1 }}>Suggested for you</Typography>
    //             {Array.from({ length: 3 }).map((_, i) => (
    //                 <Box key={i} display="flex" alignItems="center" gap={1} mb={1}>
    //                     <Skeleton variant="circular" width={36} height={36} />
    //                     <Skeleton width={120} height={16} />
    //                 </Box>
    //             ))}
    //         </Box>
    //     );
    // }

    // if (!users || users.length === 0) return null;
    const users: any[] = [];
    return (<Box>
        <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
            Suggested for you
        </Typography>
        <List disablePadding>
            {users.map((user) => (
                <ListItem key={user.id} disablePadding sx={{ mb: 1 }}>
                    <ListItemAvatar>
                        <Avatar src={undefined} sx={{ width: 36, height: 36 }}>
                            empty
                        </Avatar>
                    </ListItemAvatar>
                    <ListItemText
                        primary="empty"
                        primaryTypographyProps={{ variant: 'body2', fontWeight: 600 }}
                    />
                    <Button size="small" variant="text">
                        Follow
                    </Button>
                </ListItem>
            ))}
        </List>
    </Box>
    );
}
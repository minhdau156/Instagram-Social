import { useQuery } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import { usersApi } from "../../api/usersApi";
import { Container, Alert, Avatar, Box, Stack, Typography } from "@mui/material";
import { PageLoader } from "../../components/common/PageLoader";
import { followKeys } from "../../hooks/follow/queryKeys";
import { useAuth } from "../../hooks/useAuth";
import { FollowButton } from "../../components/follow/FollowButton";
import { BlockButton } from "../../components/moderation/BlockButton";
import { useBlockedUsers } from "../../hooks/moderation/useBlockedUsers";
import { useState } from "react";
import { FollowingDialog } from "../../components/follow/FollowingDialog";
import { FollowerDialog } from "../../components/follow/FollowerDialog";

export const PublicProfilePage = () => {
    const { username } = useParams();

    const profileKey = followKeys.profile(username!);
    const { data, isLoading, error } = useQuery({
        queryKey: profileKey,
        queryFn: () => usersApi.getUserByUsername(username!),
    });



    const { profile } = useAuth();
    const isOwnProfile = profile?.user?.username === data?.user?.username;
    const { blockedUsers } = useBlockedUsers();
    const isBlocked = blockedUsers.some(b => b.blockedUserId === data?.user?.id);

    const [followersOpen, setFollowersOpen] = useState(false);
    const [followingOpen, setFollowingOpen] = useState(false);
    return (
        <Container maxWidth={"md"}>
            {isLoading ? (
                <PageLoader />
            ) : error ? (
                <Alert severity="error">{error.message}</Alert>
            ) : (
                <>
                    <Container maxWidth={"sm"} style={{ marginTop: "2rem" }}>
                        <Stack direction="row" style={{ marginTop: "2rem" }} spacing={4}>
                            <Avatar src={data?.user?.avatarUrl ?? undefined} sx={{ width: 150, height: 150 }} imgProps={{ loading: 'lazy' }} />

                            <Stack spacing={1}>
                                <Typography variant="h6" >{data?.user?.username}</Typography>
                                <Typography variant="body1" >{data?.user?.fullName}</Typography>
                                <Stack direction="row" spacing={2}>
                                    <Typography variant="body2" >{data?.postCount ?? 0} posts</Typography>
                                    <Typography variant="body2" onClick={() => { setFollowersOpen(true) }} style={{ cursor: "pointer" }}>{data?.followerCount ?? 0} followers</Typography>
                                    <Typography variant="body2" onClick={() => { setFollowingOpen(true) }} style={{ cursor: "pointer" }}>{data?.followingCount ?? 0} following</Typography>
                                </Stack>

                                {!isOwnProfile && (
                                    <Stack direction="row" spacing={1}>
                                        <FollowButton
                                            username={username!}
                                            status={data?.followStatus ?? null}
                                        />
                                        <BlockButton
                                            username={username!}
                                            isBlocked={isBlocked}
                                        />
                                    </Stack>
                                )}
                            </Stack>

                        </Stack>
                    </Container>

                    <Box style={{ marginTop: "5rem", borderTop: "1px solid #e5e5e5" }}>
                        No post yet.
                    </Box>
                    <FollowerDialog
                        username={username!}
                        open={followersOpen}
                        setOpen={setFollowersOpen}
                    />
                    <FollowingDialog
                        username={username!}
                        open={followingOpen}
                        setOpen={setFollowingOpen}
                    />

                </>
            )}
        </Container>

    );
};
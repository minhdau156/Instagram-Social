export interface User {
    id: string;
    username: string;
    email: string;
    fullName: string;
    bio: string | null;
    avatarUrl: string | null;
    isPrivate: boolean;
    isVerified: boolean;
    role: string;
}

export interface UserProfile {
    user: User;
    postCount: number;
    followerCount: number;
    followingCount: number;
    isFollowing: boolean;
    followStatus?: import("./follow").FollowStatus | null;
}

export interface UpdateProfilePayload {
    fullName?: string;
    bio?: string | null;
    isPrivate?: boolean;
}
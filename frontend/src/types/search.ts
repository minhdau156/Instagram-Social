export type SearchType = 'users' | 'hashtags' | 'posts';

export interface UserSearchResult {
    id: string;
    username: string;
    fullName: string;
    avatarUrl: string | null;
    isPrivate: boolean;
    followerCount: number;
}

export interface HashtagSearchResult {
    id: string;
    name: string;
    postCount: number;
}

export interface PostSearchResult {
    id: string;
    authorUsername: string;
    authorAvatarUrl: string | null;
    caption: string | null;
    mediaUrl: string;
    mediaType: 'IMAGE' | 'VIDEO';
    likeCount: number;
    commentCount: number;
    createdAt: string;
}

export interface SearchHistoryItem {
    id: string;
    query: string;
    searchedAt: string;
}

export type SearchResult =
    | { type: 'users'; items: UserSearchResult[] }
    | { type: 'hashtags'; items: HashtagSearchResult[] }
    | { type: 'posts'; items: PostSearchResult[] };
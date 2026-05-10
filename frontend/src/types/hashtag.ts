export interface TrendingHashtag {
    id: string;
    name: string;       // without '#' prefix — add '#' in the UI
    postCount: number;
}
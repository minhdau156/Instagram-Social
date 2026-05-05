export interface SavedPost {
    id: string;
    postId: string;
    userId: string;
    savedAt: string;
}

export interface SavedPostPage {
    content: SavedPost[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    last: boolean;
}

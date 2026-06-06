CREATE INDEX IF NOT EXISTS idx_posts_not_deleted
    ON posts (user_id, created_at DESC)
    WHERE deleted_at IS NULL;


CREATE INDEX IF NOT EXISTS idx_posts_cursor
    ON posts (created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_follows_following_approved
    ON follows (following_id, is_approved, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_stats_followers
    ON user_stats (follower_count DESC);
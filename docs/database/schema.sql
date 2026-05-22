-- =============================================================================
-- Social Media Platform - Database Schema
-- Version: 1.0
-- Date: 2026-04-12
-- Description: Full schema derived from BRD v1.0 and User Stories
-- Database: PostgreSQL 15+
-- =============================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";   -- for fast LIKE/ILIKE search on usernames
CREATE EXTENSION IF NOT EXISTS "citext";     -- case-insensitive text

-- =============================================================================
-- ENUMS
-- =============================================================================

CREATE TYPE account_status     AS ENUM ('active', 'suspended', 'deactivated', 'pending_verification');
CREATE TYPE auth_provider      AS ENUM ('email', 'phone', 'google', 'facebook');
CREATE TYPE privacy_level      AS ENUM ('public', 'followers_only', 'private');
CREATE TYPE media_type         AS ENUM ('image', 'video');
CREATE TYPE post_status        AS ENUM ('draft', 'published', 'archived', 'deleted');
CREATE TYPE reaction_type      AS ENUM ('like');           -- extensible for future emoji reactions
CREATE TYPE notification_type  AS ENUM (
    'like_post', 'like_comment',
    'comment_post', 'reply_comment',
    'follow', 'follow_request', 'follow_accepted',
    'mention_post', 'mention_comment',
    'direct_message', 'group_message',
    'post_shared'
);
CREATE TYPE message_type       AS ENUM ('text', 'image', 'video', 'post_share');
CREATE TYPE report_entity_type AS ENUM ('user', 'post', 'comment', 'message');
CREATE TYPE report_status      AS ENUM ('pending', 'reviewed', 'resolved', 'dismissed');


-- =============================================================================
-- 1. USERS & AUTHENTICATION
-- =============================================================================

-- Core user account table
CREATE TABLE users (
    id                  UUID            PRIMARY KEY DEFAULT uuid_generate_v4(),
    username            CITEXT          NOT NULL UNIQUE,
    email               CITEXT          UNIQUE,                 -- nullable if registered via phone
    phone_number        VARCHAR(20)     UNIQUE,                 -- nullable if registered via email
    password_hash       VARCHAR(255),                           -- null for OAuth-only accounts
    full_name           VARCHAR(150)    NOT NULL DEFAULT '',
    bio                 VARCHAR(150),
    profile_picture_url TEXT,
    website_url         TEXT,
    account_status      account_status  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    privacy_level       privacy_level   NOT NULL DEFAULT 'PUBLIC',
    is_verified         BOOLEAN         NOT NULL DEFAULT FALSE, -- blue-tick badge
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_login_at       TIMESTAMPTZ,
    search_tsv   tsvector  GENERATED ALWAYS AS (to_tsvector('simple', coalesce(username::text, '') || ' ' || coalesce(full_name, ''))) STORED,

    CONSTRAINT chk_contact CHECK (email IS NOT NULL OR phone_number IS NOT NULL)
);

CREATE INDEX idx_users_username  ON users USING GIN (username gin_trgm_ops);
CREATE INDEX idx_users_email     ON users (email);
CREATE INDEX idx_users_status    ON users (account_status);
CREATE INDEX idx_users_search_fts ON users USING GIN (search_tsv);

-- Third-party OAuth provider links (FR-001)
CREATE TABLE user_auth_providers (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider        auth_provider NOT NULL,
    provider_uid    VARCHAR(255) NOT NULL,          -- UID from the OAuth provider
    access_token    TEXT,                            -- stored encrypted at application layer
    refresh_token   TEXT,
    token_expires_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE (provider, provider_uid)
);

CREATE INDEX idx_auth_providers_user ON user_auth_providers (user_id);

-- Password reset tokens (FR-003)
CREATE TABLE password_reset_tokens (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pwd_reset_user ON password_reset_tokens (user_id);

-- Active sessions / refresh tokens (FR-002, NFR-005)
CREATE TABLE user_sessions (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(255) NOT NULL UNIQUE,
    device_info     JSONB,                          -- {"os":"iOS","app_version":"1.0","device_id":"..."}
    ip_address      INET,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sessions_user ON user_sessions (user_id);


-- =============================================================================
-- 2. FOLLOW GRAPH (FR-0016)
-- =============================================================================

CREATE TABLE follows (
    follower_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    following_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    is_approved     BOOLEAN     NOT NULL DEFAULT TRUE,   -- FALSE = pending (private accounts)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (follower_id, following_id),
    CONSTRAINT chk_no_self_follow CHECK (follower_id <> following_id)
);

CREATE INDEX idx_follows_following ON follows (following_id);
CREATE INDEX idx_follows_follower  ON follows (follower_id);

-- Denormalized counters (updated via triggers / application layer)
CREATE TABLE user_stats (
    user_id         UUID    PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    post_count      INT     NOT NULL DEFAULT 0,
    follower_count  INT     NOT NULL DEFAULT 0,
    following_count INT     NOT NULL DEFAULT 0
);


-- =============================================================================
-- 3. POSTS & MEDIA (FR-005 → FR-008)
-- =============================================================================

CREATE TABLE posts (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    caption         TEXT,
    location        VARCHAR(255),
    status          post_status NOT NULL DEFAULT 'PUBLISHED',
    view_count      BIGINT      NOT NULL DEFAULT 0,
    like_count      INT         NOT NULL DEFAULT 0,     -- denormalized
    comment_count   INT         NOT NULL DEFAULT 0,     -- denormalized
    save_count      INT         NOT NULL DEFAULT 0,     -- denormalized
    share_count     INT         NOT NULL DEFAULT 0,     -- denormalized
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,                         -- soft delete
    caption_tsv  tsvector  GENERATED ALWAYS AS (to_tsvector('english', coalesce(caption, ''))) STORED
);

CREATE INDEX idx_posts_user       ON posts (user_id, created_at DESC);
CREATE INDEX idx_posts_status     ON posts (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_posts_created    ON posts (created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_posts_caption_fts  ON posts USING GIN (caption_tsv);
CREATE INDEX idx_posts_caption_trgm ON posts USING GIN (caption gin_trgm_ops);

-- Each post can have multiple media items (carousel / album)
CREATE TABLE post_media (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    post_id         UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    media_type      media_type  NOT NULL,
    media_url       TEXT        NOT NULL,
    thumbnail_url   TEXT,                               -- for videos
    width           INT,
    height          INT,
    duration_secs   NUMERIC(8,2),                       -- for videos
    file_size_bytes BIGINT,
    sort_order      SMALLINT    NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_post_media_post ON post_media (post_id, sort_order);

-- Content editing metadata (FR-006)
CREATE TABLE post_media_edits (
    id          UUID    PRIMARY KEY DEFAULT uuid_generate_v4(),
    media_id    UUID    NOT NULL REFERENCES post_media (id) ON DELETE CASCADE,
    filter_name VARCHAR(100),
    edit_params JSONB,     -- {"brightness":10,"contrast":5,"crop":{"x":0,"y":0,"w":1080,"h":1080}}
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- =============================================================================
-- 4. HASHTAGS (FR-007, FR-0011)
-- =============================================================================

CREATE TABLE hashtags (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        CITEXT      NOT NULL UNIQUE,     -- stored without '#'
    post_count  INT         NOT NULL DEFAULT 0,  -- denormalized
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hashtags_name ON hashtags USING GIN (name gin_trgm_ops);

CREATE TABLE post_hashtags (
    post_id     UUID    NOT NULL REFERENCES posts    (id) ON DELETE CASCADE,
    hashtag_id  UUID    NOT NULL REFERENCES hashtags (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (post_id, hashtag_id)
);

CREATE INDEX idx_post_hashtags_hashtag ON post_hashtags (hashtag_id);

-- User/account mentions inside captions or comments
CREATE TABLE mentions (
    id              UUID    PRIMARY KEY DEFAULT uuid_generate_v4(),
    mentioned_user_id UUID  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id         UUID    REFERENCES posts    (id) ON DELETE CASCADE,
    comment_id      UUID,                               -- FK added after comments table
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_mention_context CHECK (
        (post_id IS NOT NULL AND comment_id IS NULL) OR
        (post_id IS NULL    AND comment_id IS NOT NULL)
    )
);

CREATE INDEX idx_mentions_user    ON mentions (mentioned_user_id);
CREATE INDEX idx_mentions_post    ON mentions (post_id);


-- =============================================================================
-- 5. REACTIONS / LIKES (FR-0012)
-- =============================================================================

CREATE TABLE post_likes (
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id     UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (user_id, post_id)
);

CREATE INDEX idx_post_likes_post ON post_likes (post_id);


-- =============================================================================
-- 6. COMMENTS (FR-0013)
-- =============================================================================

CREATE TABLE comments (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    post_id         UUID        NOT NULL REFERENCES posts    (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users    (id) ON DELETE CASCADE,
    parent_id       UUID        REFERENCES comments (id) ON DELETE CASCADE,  -- NULL = top-level comment
    body            TEXT        NOT NULL,
    like_count      INT         NOT NULL DEFAULT 0,     -- denormalized
    reply_count     INT         NOT NULL DEFAULT 0,     -- denormalized
    is_deleted      BOOLEAN     NOT NULL DEFAULT FALSE, -- soft delete
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comments_post      ON comments (post_id, created_at DESC) WHERE NOT is_deleted;
CREATE INDEX idx_comments_parent    ON comments (parent_id)               WHERE parent_id IS NOT NULL;
CREATE INDEX idx_comments_user      ON comments (user_id);

-- Add deferred FK for mentions.comment_id now that comments table exists
ALTER TABLE mentions
    ADD CONSTRAINT fk_mentions_comment
    FOREIGN KEY (comment_id) REFERENCES comments (id) ON DELETE CASCADE;

CREATE INDEX idx_mentions_comment ON mentions (comment_id);

CREATE TABLE comment_likes (
    user_id     UUID        NOT NULL REFERENCES users    (id) ON DELETE CASCADE,
    comment_id  UUID        NOT NULL REFERENCES comments (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (user_id, comment_id)
);

CREATE INDEX idx_comment_likes_comment ON comment_likes (comment_id);


-- =============================================================================
-- 7. SAVES / BOOKMARKS (FR-0015)
-- =============================================================================

CREATE TABLE saved_posts (
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id     UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    collection  VARCHAR(100),           -- optional named collection (future)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (user_id, post_id)
);

CREATE INDEX idx_saved_posts_user ON saved_posts (user_id, created_at DESC);


-- =============================================================================
-- 8. SHARES (FR-0014)
-- =============================================================================

CREATE TABLE post_shares (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    post_id         UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    shared_by_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    shared_to_id    UUID        REFERENCES users (id) ON DELETE SET NULL,   -- NULL = external share
    platform        VARCHAR(50),        -- 'internal' | 'twitter' | 'whatsapp' | etc.
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_post_shares_post ON post_shares (post_id);
CREATE INDEX idx_post_shares_user ON post_shares (shared_by_id);


-- =============================================================================
-- 9. DIRECT MESSAGING (FR-0017, FR-0018)
-- =============================================================================

-- A conversation can be a 1-to-1 DM or a group chat
CREATE TABLE conversations (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    is_group        BOOLEAN     NOT NULL DEFAULT FALSE,
    name            VARCHAR(150),               -- group name; NULL for 1-to-1
    avatar_url      TEXT,                       -- group avatar
    created_by_id   UUID        NOT NULL REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()   -- set on new message
);

-- Members of a conversation
CREATE TABLE conversation_members (
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users          (id) ON DELETE CASCADE,
    is_admin        BOOLEAN     NOT NULL DEFAULT FALSE,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    left_at         TIMESTAMPTZ,
    last_read_at    TIMESTAMPTZ,                        -- for unread badge

    PRIMARY KEY (conversation_id, user_id)
);

CREATE INDEX idx_conv_members_user ON conversation_members (user_id);

CREATE TABLE messages (
    id                  UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    conversation_id     UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id           UUID        NOT NULL REFERENCES users         (id) ON DELETE CASCADE,
    message_type        message_type NOT NULL DEFAULT 'TEXT',
    body                TEXT,                       -- text content
    media_url           TEXT,                       -- image/video URL
    shared_post_id      UUID        REFERENCES posts (id) ON DELETE SET NULL,  -- post_share type
    reply_to_message_id UUID        REFERENCES messages (id) ON DELETE SET NULL,
    is_deleted          BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_conv ON messages (conversation_id, created_at DESC);
CREATE INDEX idx_messages_sender ON messages (sender_id);

-- Per-message read receipts
CREATE TABLE message_reads (
    message_id  UUID        NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users    (id) ON DELETE CASCADE,
    read_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (message_id, user_id)
);


-- =============================================================================
-- 10. NOTIFICATIONS (FR-0019, FR-0020)
-- =============================================================================

CREATE TABLE notifications (
    id              UUID                PRIMARY KEY DEFAULT uuid_generate_v4(),
    recipient_id    UUID                NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    actor_id        UUID                REFERENCES users (id) ON DELETE SET NULL, -- who triggered it
    type            notification_type   NOT NULL,
    entity_id       UUID,               -- polymorphic: post_id / comment_id / conversation_id
    entity_type     VARCHAR(50),        -- 'post' | 'comment' | 'conversation' | ...
    is_read         BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, created_at DESC);
CREATE INDEX idx_notifications_unread    ON notifications (recipient_id) WHERE NOT is_read;

-- Per-user notification preference settings (FR-0020)
CREATE TABLE notification_settings (
    user_id                 UUID    PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    likes                   BOOLEAN NOT NULL DEFAULT TRUE,
    comments                BOOLEAN NOT NULL DEFAULT TRUE,
    new_followers           BOOLEAN NOT NULL DEFAULT TRUE,
    follow_requests         BOOLEAN NOT NULL DEFAULT TRUE,
    direct_messages         BOOLEAN NOT NULL DEFAULT TRUE,
    mentions                BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled           BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- =============================================================================
-- 11. SEARCH & DISCOVERY (FR-0010, FR-0011)
-- =============================================================================

-- Tracks trending hashtags & search signals
CREATE TABLE hashtag_stats (
    hashtag_id      UUID    PRIMARY KEY REFERENCES hashtags (id) ON DELETE CASCADE,
    weekly_count    INT     NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Captures interest signals for personalised feed ranking
CREATE TABLE user_interests (
    user_id     UUID    NOT NULL REFERENCES users    (id) ON DELETE CASCADE,
    hashtag_id  UUID    NOT NULL REFERENCES hashtags (id) ON DELETE CASCADE,
    score       NUMERIC(5,2) NOT NULL DEFAULT 1.0,       -- affinity score
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (user_id, hashtag_id)
);

-- Stores search history per user (for autocomplete & personalisation)
CREATE TABLE search_history (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    query       TEXT        NOT NULL,
    searched_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_search_history_user ON search_history (user_id, searched_at DESC);


-- =============================================================================
-- 12. CONTENT MODERATION / REPORTS (NFR-007, Admin Stories)
-- =============================================================================

CREATE TABLE reports (
    id              UUID                PRIMARY KEY DEFAULT uuid_generate_v4(),
    reporter_id     UUID                NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    entity_type     report_entity_type  NOT NULL,
    entity_id       UUID                NOT NULL,
    reason          VARCHAR(255)        NOT NULL,
    details         TEXT,
    status          report_status       NOT NULL DEFAULT 'PENDING',
    reviewed_by_id  UUID                REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reports_status    ON reports (status);
CREATE INDEX idx_reports_entity    ON reports (entity_type, entity_id);
CREATE INDEX idx_reports_reporter  ON reports (reporter_id);

-- Blocked users (anti-harassment)
CREATE TABLE user_blocks (
    blocker_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT chk_no_self_block CHECK (blocker_id <> blocked_id)
);

CREATE INDEX idx_blocks_blocked ON user_blocks (blocked_id);


-- =============================================================================
-- 13. DEVICE PUSH TOKENS (NFR — real-time notifications)
-- =============================================================================

CREATE TABLE device_tokens (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token       TEXT        NOT NULL UNIQUE,
    platform    VARCHAR(20) NOT NULL,   -- 'ios' | 'android' | 'web'
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);


-- =============================================================================
-- 14. AUDIT LOG (security, NFR-004)
-- =============================================================================

CREATE TABLE audit_logs (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     UUID        REFERENCES users (id) ON DELETE SET NULL,
    action      VARCHAR(100) NOT NULL,       -- 'login', 'post_delete', 'report_submit', ...
    entity_type VARCHAR(50),
    entity_id   UUID,
    metadata    JSONB,
    ip_address  INET,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user   ON audit_logs (user_id, created_at DESC);
CREATE INDEX idx_audit_action ON audit_logs (action, created_at DESC);


-- =============================================================================
-- TRIGGERS — keep updated_at fresh automatically
-- =============================================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_posts_updated_at
    BEFORE UPDATE ON posts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_comments_updated_at
    BEFORE UPDATE ON comments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_conversations_updated_at
    BEFORE UPDATE ON conversations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_messages_updated_at
    BEFORE UPDATE ON messages
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_notification_settings_updated_at
    BEFORE UPDATE ON notification_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

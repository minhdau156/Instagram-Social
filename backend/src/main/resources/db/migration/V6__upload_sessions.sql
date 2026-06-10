CREATE TABLE upload_sessions (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    upload_id       TEXT        NOT NULL UNIQUE,  -- MinIO-assigned multipart upload ID
    object_key      TEXT        NOT NULL,          -- MinIO object key
    content_type    VARCHAR(100) NOT NULL,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    total_parts     INT,                           -- expected part count (nullable until client knows)
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    -- status: IN_PROGRESS | COMPLETED | ABORTED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '24 hours'
);

CREATE INDEX idx_upload_sessions_user   ON upload_sessions (user_id);
CREATE INDEX idx_upload_sessions_status ON upload_sessions (status, expires_at);
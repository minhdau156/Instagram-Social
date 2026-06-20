-- Stores the result of idempotent POST operations.
-- The `key` is supplied by the client as a UUID header value.
-- `request_hash` detects whether the same key was sent with a different body (conflict).
-- `response_body` is the JSON response body stored on first execution.
-- `http_status` is the HTTP status code of the first response.
-- `created_at` is used for TTL cleanup (TASK-10.48 scheduled job can purge old rows).

CREATE TABLE idempotency_keys (
    key             UUID        NOT NULL PRIMARY KEY,
    user_id         UUID        NOT NULL,
    endpoint        VARCHAR(200) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    response_body   TEXT        NOT NULL,
    http_status     INT         NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for efficient cleanup of old keys
CREATE INDEX idx_idempotency_created_at ON idempotency_keys (created_at);

-- Index for user-scoped lookups (optional, for auditing)
CREATE INDEX idx_idempotency_user ON idempotency_keys (user_id, created_at);
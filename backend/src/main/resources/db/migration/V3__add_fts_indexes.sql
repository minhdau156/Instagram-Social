ALTER TABLE users ADD COLUMN IF NOT EXISTS follower_count INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

ALTER TABLE users
    ADD COLUMN search_tsv tsvector
      GENERATED ALWAYS AS (
        to_tsvector('simple',
          coalesce(username::text, '') || ' ' || coalesce(full_name, ''))
      ) STORED;

  CREATE INDEX idx_users_search_fts ON users USING GIN (search_tsv);

ALTER TABLE posts
    ADD COLUMN caption_tsv tsvector
      GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(caption, ''))
      ) STORED;

  CREATE INDEX idx_posts_caption_fts ON posts USING GIN (caption_tsv);


  CREATE INDEX idx_posts_caption_trgm ON posts USING GIN (caption gin_trgm_ops);
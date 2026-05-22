# TASK-8.16 — Full-Text Search Integration

## Overview

Replace the `ILIKE '%pattern%'` sequential scan on `posts.caption` and `users` with PostgreSQL native full-text search (`tsvector` / `tsquery`). A `tsvector` generated column is added to both tables, backed by GIN indexes, so the search engine uses index scans instead of full table scans.

| Method | Current | After |
|---|---|---|
| `searchPosts` | `caption ILIKE '%q%'` — full table scan | `caption_tsv @@ plainto_tsquery(...)` — GIN index scan + `ts_rank` relevance ordering |
| `searchUsers` | `username ILIKE '%q%' OR full_name ILIKE '%q%'` — touches the trigram GIN index but does a mid-string scan | `search_tsv @@ plainto_tsquery(...)` — GIN FTS index scan |
| `searchHashtags` | `name ILIKE 'q%'` — prefix on trigram GIN | **unchanged** — trigram prefix is optimal for short autocomplete tokens |
| `findPostsByHashtag` | `LOWER(h.name) = LOWER(:name)` — exact CITEXT match | **unchanged** |

## Why This Matters

- `ILIKE '%pattern%'` forces a sequential scan on `posts`; PostgreSQL cannot use any B-tree index when the leading character is a wildcard.
- On a dataset of millions of posts, every search request reads every row. With FTS the cost is O(log n).
- PostgreSQL FTS adds stemming (`run` matches `running`, `ran`) and relevance ranking (`ts_rank`) so results are ordered by how well they match, not just chronologically.
- `users` already has a trigram GIN index but FTS on a generated column is faster for full-word token matching and allows relevance scoring.

## Files to Create / Modify

```
backend/src/main/resources/db/migration/V3__add_fts_indexes.sql   ← new
backend/src/main/java/com/instagram/adapter/out/persistence/SearchJpaAdapter.java  ← modify
docs/database/schema.sql                                            ← modify (add generated columns)
backend/src/test/java/com/instagram/adapter/out/persistence/SearchJpaAdapterIT.java ← modify
```

---

## Checklist

### 1. Flyway Migration `V3__add_fts_indexes.sql`

- [ ] Add a `STORED` generated `tsvector` column to `posts`:
  ```sql
  -- Drop the index if it exists (Postgres 16+ supports NOT EXISTS, but old versions may error)
  DROP INDEX IF EXISTS idx_posts_caption_trgm;

  -- Add the generated column for FTS
  ALTER TABLE posts
    ADD COLUMN caption_tsv tsvector
      GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(caption, ''))
      ) STORED;

  CREATE INDEX idx_posts_caption_fts ON posts USING GIN (caption_tsv);
  ```

- [ ] Add a `STORED` generated `tsvector` column to `users` combining `username` and `full_name`:
  ```sql
  ALTER TABLE users
    ADD COLUMN search_tsv tsvector
      GENERATED ALWAYS AS (
        to_tsvector('simple',
          coalesce(username::text, '') || ' ' || coalesce(full_name, ''))
      ) STORED;

  CREATE INDEX idx_users_search_fts ON users USING GIN (search_tsv);
  ```
  - Use `'simple'` dictionary (no stemming) so usernames are indexed as-is.
  - Cast `username` from `CITEXT` to `TEXT` — `to_tsvector` cannot resolve overloads on `CITEXT` directly.

- [ ] (Recommended) Add a trigram GIN index on `posts.caption` as a short-query fallback:
  ```sql
  CREATE INDEX idx_posts_caption_trgm ON posts USING GIN (caption gin_trgm_ops);
  ```
  This allows the service to fall back to `ILIKE` for 1–2 character inputs where FTS returns nothing.

---

### 2. Update `SearchJpaAdapter.java`

#### `searchPosts` — replace ILIKE with FTS

Old query:
```sql
SELECT p.* FROM posts p
WHERE  p.caption ILIKE :pattern AND p.deleted_at IS NULL
ORDER  BY p.created_at DESC
LIMIT  :limit OFFSET :offset
```

New query:
```sql
SELECT p.*
FROM   posts p
WHERE  p.caption_tsv @@ plainto_tsquery('english', :query)
  AND  p.deleted_at IS NULL
ORDER  BY ts_rank(p.caption_tsv, plainto_tsquery('english', :query)) DESC,
          p.created_at DESC
LIMIT  :limit OFFSET :offset
```

- [ ] Replace the native SQL string in `searchPosts` with the FTS query above.
- [ ] Change parameter binding: bind `:query` directly (no `%` wrapping needed for FTS).
- [ ] Add a short-query fallback: if `query.length() < 3`, keep the original `ILIKE '%' + query + '%'` path so single-letter searches still return results.
  ```java
  private static final int FTS_MIN_LENGTH = 3;

  // Inside searchPosts, after the blank guard:
  String sql = query.length() < FTS_MIN_LENGTH
      ? "SELECT p.* FROM posts p WHERE p.caption ILIKE :pattern AND p.deleted_at IS NULL ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset"
      : "SELECT p.* FROM posts p WHERE p.caption_tsv @@ plainto_tsquery('english', :query) AND p.deleted_at IS NULL ORDER BY ts_rank(p.caption_tsv, plainto_tsquery('english', :query)) DESC, p.created_at DESC LIMIT :limit OFFSET :offset";

  Query nativeQuery = em.createNativeQuery(sql, PostJpaEntity.class);
  if (query.length() < FTS_MIN_LENGTH) {
      nativeQuery.setParameter("pattern", "%" + query + "%");
  } else {
      nativeQuery.setParameter("query", query);
  }
  nativeQuery.setParameter("limit", pageable.getPageSize());
  nativeQuery.setParameter("offset", pageable.getOffset());
  ```
- [ ] Keep `@SuppressWarnings("unchecked")` and the blank-query guard.

#### `searchUsers` — replace ILIKE with FTS

Old query:
```sql
SELECT * FROM users
WHERE  (username ILIKE :pattern OR full_name ILIKE :pattern) AND deleted_at IS NULL
ORDER  BY follower_count DESC
LIMIT  :limit OFFSET :offset
```

New query:
```sql
SELECT *
FROM   users
WHERE  search_tsv @@ plainto_tsquery('simple', :query)
  AND  deleted_at IS NULL
ORDER  BY follower_count DESC
LIMIT  :limit OFFSET :offset
```

- [ ] Replace the native SQL string in `searchUsers` with the FTS query above.
- [ ] Bind `:query` directly (no `%` wrapping).
- [ ] Apply the same short-query fallback (< 3 chars → fall back to original ILIKE):
  ```sql
  -- fallback
  SELECT * FROM users
  WHERE  (username ILIKE :pattern OR full_name ILIKE :pattern) AND deleted_at IS NULL
  ORDER  BY follower_count DESC LIMIT :limit OFFSET :offset
  ```
- [ ] Keep `@SuppressWarnings("unchecked")` and the blank-query guard.

#### `searchHashtags` and `findPostsByHashtag`

- [ ] Leave both methods **unchanged**. Trigram prefix match is optimal for hashtag autocomplete, and the hashtag-page browse uses an exact CITEXT equality match.

---

### 3. Update `docs/database/schema.sql`

- [ ] In the `posts` table block, add the generated column and its index:
  ```sql
  caption_tsv  tsvector  GENERATED ALWAYS AS (to_tsvector('english', coalesce(caption, ''))) STORED
  ```
  ```sql
  CREATE INDEX idx_posts_caption_fts  ON posts USING GIN (caption_tsv);
  CREATE INDEX idx_posts_caption_trgm ON posts USING GIN (caption gin_trgm_ops);
  ```
- [ ] In the `users` table block, add the generated column and its index:
  ```sql
  search_tsv   tsvector  GENERATED ALWAYS AS (to_tsvector('simple', coalesce(username::text, '') || ' ' || coalesce(full_name, ''))) STORED
  ```
  ```sql
  CREATE INDEX idx_users_search_fts ON users USING GIN (search_tsv);
  ```

---

### 4. Update `SearchJpaAdapterIT.java`

The existing tests run on H2 in PostgreSQL compatibility mode, but H2 does not support `tsvector` or `plainto_tsquery`. Two options:

**Option A — Testcontainers (recommended if already used elsewhere in the project)**

- [ ] Check whether `pom.xml` already declares `org.testcontainers:postgresql` in test scope. If not, add it.
- [ ] Annotate `SearchJpaAdapterIT` with `@Testcontainers` and add:
  ```java
  @Container
  static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine");

  @DynamicPropertySource
  static void pgProps(DynamicPropertyRegistry r) {
      r.add("spring.datasource.url", pg::getJdbcUrl);
      r.add("spring.datasource.username", pg::getUsername);
      r.add("spring.datasource.password", pg::getPassword);
  }
  ```
- [ ] Remove the `@AutoConfigureTestDatabase` override that pointed to H2.
- [ ] The existing `@Sql` fixup for `follower_count`/`deleted_at` DDL should now be removed or simplified — the real schema migration handles it.

**Option B — Separate FTS test class (fallback if Testcontainers is not in use)**

- [ ] Leave the existing H2-based `SearchJpaAdapterIT` as-is for the ILIKE fallback path tests.
- [ ] Create `SearchFtsAdapterIT` that extends from the shared Testcontainers base class (if one exists) or configures its own container. Run only the FTS-specific assertions here.

#### FTS Test Cases (add to whichever class runs against real PostgreSQL)

**`searchPosts` FTS**
- [ ] **Basic token match:** Insert post with caption `"Beautiful sunset in Bali"`. Search `"sunset"`. Verify post is returned.
- [ ] **Stemming:** Insert post with caption `"Running along the beach"`. Search `"run"`. Verify post is returned (English FTS stems `running` → `run`).
- [ ] **Multi-word:** Insert post with caption `"Golden Gate Bridge"`. Search `"golden gate"`. Verify post is returned.
- [ ] **Soft-delete excluded:** Insert post captioned `"sunset beach"` with `deleted_at` set. Search `"sunset"`. Verify it does NOT appear.
- [ ] **No match:** Search `"pineapple"` against captions that don't contain that word. Verify empty list.
- [ ] **Relevance ordering:** Insert two posts — one mentioning `"sunset"` once and one mentioning `"sunset sunset"` twice. Verify the higher-density post ranks first.
- [ ] **Short-query fallback (< 3 chars):** Search `"ba"`. Verify the ILIKE path returns the `"Bali"` post (contains `"ba"`).

**`searchUsers` FTS**
- [ ] **Token match on full_name:** Insert user with `full_name = "Alexander Johnson"`. Search `"alexander"`. Verify user is returned.
- [ ] **Token match on username:** Insert user with `username = "travellover"`. Search `"travellover"`. Verify user is returned.
- [ ] **No cross-token partial match:** Search `"alexand"` (not a full token). Verify no result — document this as expected FTS behaviour. The short-query fallback covers short inputs; partial suffixes are a known FTS limitation.
- [ ] **follower_count ordering preserved:** Insert two matching users with different follower counts. Verify higher count appears first.

---

## Notes

- **FTS is token-based, not substring-based.** `plainto_tsquery('english', 'sun')` does NOT match `"sunset"` — it matches only the exact token `sun` after stemming. Use the short-query ILIKE fallback for inputs shorter than 3 characters to cover prefix autocomplete scenarios.
- **`plainto_tsquery` vs `to_tsquery`:** Use `plainto_tsquery` for user-facing input — it converts any raw string to a safe tsquery without exposing syntax operators (`&`, `|`, `!`, `:*`). Do NOT use `to_tsquery` directly with user input as it will throw a syntax error on queries like `hello world`.
- **`websearch_to_tsquery` (PostgreSQL 11+):** An alternative that supports quoted phrases (`"golden gate"`) and minus exclusion (`-spam`). Consider upgrading to this for a richer search UX.
- **`CITEXT` cast:** PostgreSQL will not resolve `to_tsvector('simple', username)` when `username` is `CITEXT` — it causes an "ambiguous function" error. The `username::text` cast in the generated column expression resolves this.
- **Flyway migration order:** The generated columns `caption_tsv` and `search_tsv` are computed at INSERT/UPDATE time. Rows inserted before `V3` runs will have their `tsvector` values populated automatically because `GENERATED ALWAYS AS ... STORED` backfills all existing rows when the `ALTER TABLE` executes.
- **`@DataJpaTest` + H2:** Because `tsvector` is PostgreSQL-specific, any `@DataJpaTest` that loads `V3` migration via Flyway will fail on H2. Suppress Flyway in those tests (`@AutoConfigureTestDatabase(replace=REPLACE.ANY)` + manual DDL via `@Sql`) or switch to Testcontainers.

## Acceptance Criteria

- [ ] `V3__add_fts_indexes.sql` applies cleanly against PostgreSQL 15 with no errors.
- [ ] `searchPosts("sunset")` returns posts whose caption contains "sunset" or stems of it, ordered by `ts_rank` DESC.
- [ ] `searchPosts("ba")` (short fallback) returns posts whose caption contains `"ba"` via ILIKE.
- [ ] `searchUsers("alexander")` returns users whose `full_name` contains the token `"alexander"`.
- [ ] `searchHashtags` and `findPostsByHashtag` behaviour and query plans are unchanged.
- [ ] All existing `SearchControllerIT` tests pass — no changes to any port, DTO, or controller interfaces.
- [ ] At least the FTS-specific integration tests run against a real PostgreSQL instance.

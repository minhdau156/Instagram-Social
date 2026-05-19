# TASK-8.1 — Domain Model: SearchHistory

## Overview

Create the `SearchHistory` domain model. This represents a record of a search query performed by a user, stored to power autocomplete and personalisation. Follow the same hand-written Builder pattern used in `Post.java` and `Notification.java` — no Lombok, no framework annotations.

## Requirements

- Lives in `domain/model/` — no Spring, JPA, or Lombok imports.
- Use `Post.java` as the reference for the Builder pattern.
- The domain model must match the `search_history` table in `docs/database/schema.sql` exactly.

## File Location

```
backend/src/main/java/com/instagram/domain/model/SearchHistory.java
```

---

## Checklist

### Schema Verification

- [ ] Open `docs/database/schema.sql` and locate the `search_history` table before writing any code.
  - Columns: `id UUID`, `user_id UUID`, `query TEXT`, `searched_at TIMESTAMPTZ`
  - **Note:** The schema does NOT have a `search_type` column. Do NOT add one to the JPA entity or persistence adapter. If a `SearchType` concept is needed for the domain model (e.g., to distinguish user vs. hashtag searches in future), add it as a transient / domain-only field that is not persisted — but for the current scope, omit it entirely to stay aligned with the schema.

### `SearchHistory.java`

- [ ] Fields:
  - `UUID id`
  - `UUID userId` — the user who performed the search
  - `String query` — the raw search string, stored without normalisation
  - `OffsetDateTime searchedAt` — when the search was performed
- [ ] Hand-written `Builder` inner class with all four fields.
- [ ] Static `builder()` factory method that returns a new `Builder`.
- [ ] `Builder.build()` returns a new `SearchHistory` instance.
- [ ] No setters — the object is immutable after construction.
- [ ] No business-behaviour methods needed (SearchHistory is a value record, not an entity with behaviour).
- [ ] All fields accessible via plain getter methods (`getId()`, `getUserId()`, `getQuery()`, `getSearchedAt()`).

## Notes

- `searchedAt` maps to `searched_at TIMESTAMPTZ` in the schema — use `OffsetDateTime` consistently with other domain models.
- This model has no `withXxx()` mutation methods — search history entries are write-once; they are never updated after creation.
- The `Hashtag` domain model already exists from Phase 2 (TASK-2.2). Do NOT re-create it. Reference `Hashtag.java` if needed.

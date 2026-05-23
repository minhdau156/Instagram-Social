# TASK-9.8 — Block Filtering: Feed & Search Integration

## Overview

Integrate user-block relationships into the Feed and Search features so that blocked users' content is invisible to both parties. When user A blocks user B, neither A nor B should see each other's posts in the feed or appear in each other's search results.

This task modifies two existing adapters (`FeedJpaQueryAdapter` and `SearchJpaAdapter`) and introduces a lightweight utility (`BlockFilter`) that loads the complete set of excluded user IDs for the current user.

---

## Requirements

- Modifications to existing adapters must be **surgical** — change only the queries and constructor injections necessary. Do not refactor unrelated logic.
- The block filter must not make a database call on every individual feed item or search result row. It must load the excluded IDs once per request and pass them as a set to the query.
- If the excluded ID set is empty (the user has not blocked anyone and nobody has blocked them), take the fast path — skip the filter entirely to avoid adding an unnecessary `NOT IN ()` clause.
- Do not add `@Transactional` to the read methods in these adapters unless they are already transactional — read-only queries do not require explicit transaction boundaries here.

---

## Files to Create / Modify

```
backend/src/main/java/com/instagram/infrastructure/util/BlockFilter.java           ← create
backend/src/main/java/com/instagram/adapter/out/persistence/FeedJpaQueryAdapter.java ← modify
backend/src/main/java/com/instagram/adapter/out/persistence/SearchJpaAdapter.java   ← modify
```

---

## Checklist

### `BlockFilter.java` (new)

- [ ] Place in `infrastructure/util/` — this is a cross-cutting infrastructure concern, not domain logic.
- [ ] Annotate with `@Component`.
- [ ] Inject `ModerationRepository` via constructor.
- [ ] Declare a single public method: `Set<UUID> getExcludedUserIds(UUID currentUserId)`.
  - Call `moderationRepository.findBlockedUserIdsByBlockerId(currentUserId)` to get the users that `currentUserId` has blocked.
  - Call `moderationRepository.findBlockerIdsByBlockedId(currentUserId)` to get the users who have blocked `currentUserId`.
  - Merge both lists into a single `Set<UUID>` (use `HashSet`, add all elements from both lists).
  - Return the set. If both lists are empty, return `Collections.emptySet()`.
- [ ] The method does NOT cache results between requests. Caching is a Phase 10 concern. Document this in a comment.
- [ ] The method should be fast — it makes exactly two indexed queries (`idx_blocks_blocked` and the PK index on `user_blocks`).

---

### `FeedJpaQueryAdapter.java` — Modifications

- [ ] Open the file and read all existing feed query methods before making any changes. Identify all native SQL queries that select posts.
- [ ] Add `BlockFilter` as a new constructor parameter. Inject it and store as a `final` field.
- [ ] For the home feed method (which fetches posts from followed users):
  - Before executing the query, call `blockFilter.getExcludedUserIds(currentUserId)` to get the set of excluded IDs.
  - If the set is empty, execute the original query unchanged.
  - If the set is not empty, append `AND posts.user_id NOT IN (:excludedIds)` to the native SQL WHERE clause, and bind the excluded ID set as a parameter. Be aware that binding a `Set<UUID>` as a native query parameter requires passing a list or collection — check how other native queries in the project handle collection parameters.
- [ ] For the explore feed method (which fetches trending or popular posts):
  - Apply the same excluded-ID filter.
- [ ] Do not change the ordering, limit, offset, or any other aspect of the queries.

---

### `SearchJpaAdapter.java` — Modifications

- [ ] Open the file and read all existing search methods before making any changes.
- [ ] Add `BlockFilter` as a new constructor parameter.
- [ ] For the `searchUsers` method:
  - Before building the native SQL, call `blockFilter.getExcludedUserIds(currentUserId)`. The `SearchUsersUseCase.Query` carries `currentUserId` — trace how it is passed from the use case to this adapter and ensure `currentUserId` is available at the point where you call `blockFilter`.
  - If the excluded set is not empty, append `AND users.id NOT IN (:excludedIds)` to the WHERE clause of the user-search native query.
  - If empty, execute the original query unchanged.
- [ ] For the `searchPosts` method:
  - Apply the same `AND posts.user_id NOT IN (:excludedIds)` filter to exclude posts authored by blocked users.
- [ ] For `searchHashtags` and `findPostsByHashtag`:
  - `searchHashtags` does not involve users — no filter needed.
  - `findPostsByHashtag`: apply the same post-user-id filter so posts from blocked users do not appear on a hashtag's page.

---

### Cross-Cutting Concern: Passing `currentUserId` to Adapters

- [ ] Verify that `currentUserId` is already propagated from the use case through to the persistence adapter for the feed queries. If `FeedRepository` methods do not currently accept `currentUserId` as a parameter, you will need to add it. This is an interface change that cascades from `FeedRepository` (out-port) → `FeedJpaQueryAdapter` (adapter) → `FeedService` (service) → `GetHomeFeedUseCase.Query` (in-port). Document the scope of this change and trace through each layer before modifying.
- [ ] The same concern applies to `searchPosts` and `searchUsers` — check that `currentUserId` is already being received by the adapter. Based on Phase 8, `SearchJpaAdapter` does receive the query object from the service, but the native SQL methods may not currently accept `currentUserId`. Add it as a method parameter if needed, and update `ModerationRepository` and `SearchRepository` accordingly.

---

### Flyway Migration (if needed)

- [ ] The `user_blocks` table and its indexes (`idx_blocks_blocked`) already exist in the initial Flyway migration. No new migration is needed for this task unless you are adding a database-level function or view for block filtering.
- [ ] Confirm that `idx_blocks_blocked ON user_blocks (blocked_id)` exists — this index is critical for the performance of `findBlockerIdsByBlockedId`. If it does not exist in the current migration file, create a new migration `V4__add_missing_block_index.sql` that adds it.

---

## Notes

- The `NOT IN (:excludedIds)` clause in native SQL requires that `:excludedIds` is bound as a `java.util.List` (or converted to one from the `Set`). PostgreSQL's JDBC driver accepts `List<UUID>` for this binding.
- An empty `NOT IN ()` clause causes a SQL syntax error in PostgreSQL. Always guard with `if (!excludedIds.isEmpty())` before appending the clause.
- For very large block lists (e.g., a celebrity blocking thousands of users), `NOT IN` with thousands of UUIDs is slow. For Phase 9 this is acceptable. A join-based exclusion or a temporary table approach is a Phase 10 optimisation.
- The `BlockFilter` injects `ModerationRepository` (a domain port), not `UserBlockJpaRepository` directly. This keeps the utility within the allowed dependency direction: infrastructure can reference domain ports, not persistence repositories.

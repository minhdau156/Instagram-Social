# TASK-8.8 — Unit & Integration Tests

## Overview

Write tests covering the search service, persistence adapter, and REST controller. Follow the naming conventions and patterns established in Phases 4, 6, and 7.

The persistence integration tests verify `ILIKE`-based query behaviour — matching, exclusion of soft-deleted rows, and ordering.

## Requirements

- Unit tests: JUnit 5 + Mockito. No Spring context loaded.
- Integration tests: `@DataJpaTest` for the persistence adapter. `@SpringBootTest` + `MockMvc` for the controller.
- Aim for ≥ 80% coverage of new code.

## File Locations

```
backend/src/test/java/com/instagram/application/service/SearchServiceTest.java
backend/src/test/java/com/instagram/adapter/out/persistence/SearchJpaAdapterIT.java
backend/src/test/java/com/instagram/adapter/in/web/SearchControllerIT.java
```

---

## Checklist

### `SearchServiceTest.java` (unit)

- [x] Mock all dependencies: `SearchRepository`, `SearchHistoryRepository`.
- [x] `searchUsers` — non-blank query: verifies `searchRepository.searchUsers(...)` is called with correct `PageRequest` params.
- [x] `searchUsers` — blank query (`""` or `"   "`): verifies `searchRepository.searchUsers` is **NOT** called, returns empty list.
- [x] `searchHashtags` — non-blank query: verifies `searchRepository.searchHashtags(...)` called.
- [x] `searchHashtags` — blank query: returns empty list without DB call.
- [x] `searchPosts` — non-blank query: verifies `searchRepository.searchPosts(...)` called and history save triggered.
- [x] `searchPosts` — blank query: returns empty list without DB call, no history save.
- [x] `getPostsByHashtag` — valid hashtag name: verifies `searchRepository.findPostsByHashtag(...)` called.
- [x] `getSearchHistory` — verifies `searchHistoryRepository.findByUserIdOrderBySearchedAtDesc(...)` called with correct userId and limit.
- [x] `clearSearchHistory` — verifies `searchHistoryRepository.deleteByUserId(...)` called with correct userId.
- [x] `saveHistoryAsync` (called indirectly) — if `@Async` makes it hard to verify synchronously, use `@SpyBean` or verify via `ArgumentCaptor` that `searchHistoryRepository.save(...)` is eventually called.

### `SearchJpaAdapterIT.java` (`@DataJpaTest`)

- [x] `@BeforeEach`: insert a `users` row to satisfy FK constraints on all test entities. Flush and clear the EntityManager after inserts to ensure the DB state is consistent.

#### `searchPosts` — ILIKE on `caption`

- [x] **Basic match:** Insert a post with caption `"Beautiful sunset in Bali"`. Search `"sunset"`. Verify the post is returned.
- [x] **No match:** Search `"pineapple"` against a dataset with no matching captions. Verify an empty list is returned.
- [x] **Soft-deleted exclusion:** Insert a post with caption `"Sunset beach holiday"` and set `deleted_at` to a past timestamp. Search `"sunset"`. Verify the soft-deleted post does NOT appear in results.
- [x] **Case-insensitive match:** Insert a post with caption `"GOLDEN GATE"`. Search `"golden"`. Verify the post is returned.

#### `searchUsers` — ILIKE on `username` and `full_name`

- [x] **Match on full_name:** Insert a user with `username="xyz123"` and `full_name="Alexander Johnson"`. Search `"alexander"`. Verify the user is returned.
- [x] **Match on username:** Insert a user with `username="travel_lover"` and `full_name="Some Person"`. Search `"travel"`. Verify the user is returned.
- [x] **Partial match on username:** Search `"trav"` against `username="travel_lover"`. Verify the user is returned.
- [x] **Ordering by follower_count:** Insert two matching users with different `follower_count` values. Verify the one with higher follower_count appears first.
- [x] **Soft-deleted user exclusion:** Insert a user with `deleted_at` set. Verify they do NOT appear in search results.

#### `searchHashtags` — `pg_trgm` prefix (unchanged)

- [x] Insert hashtags `"travel"`, `"travelblog"`, `"food"`. Search `"travel"`. Verify both `"travel"` and `"travelblog"` are returned (prefix match). Verify `"food"` is NOT returned.
- [x] `postCount` ordering: insert `"travel"` with `post_count=50` and `"travelblog"` with `post_count=200`. Search `"travel"`. Verify `"travelblog"` appears first.

#### `findPostsByHashtag` (join — unchanged)

- [x] Insert a hashtag `"travel"`, two posts, link both via `post_hashtags`. Insert one post with a different hashtag. Call `findPostsByHashtag("travel", ...)`. Verify only the two linked posts are returned.

#### `SearchHistoryPersistenceAdapter`

- [x] `save`: save a `SearchHistory` and reload via `findByUserIdOrderBySearchedAtDesc`. Verify all fields round-trip correctly.
- [x] `deleteByUserId`: insert 3 history entries for the same user. Call `deleteByUserId`. Verify 0 remain for that user.

### `SearchControllerIT.java` (`@SpringBootTest` + `MockMvc`)

- [x] Use `@MockBean` for all six use-case interfaces so no real DB is hit.
- [x] `GET /api/v1/search?q=john&type=users` — authenticated: returns `200` with a list.
- [x] `GET /api/v1/search?q=travel&type=hashtags` — authenticated: returns `200` with a list.
- [x] `GET /api/v1/search?q=sunset&type=posts` — authenticated: returns `200` with a list.
- [x] `GET /api/v1/search?q=x&type=invalid_type` — returns `400 BAD REQUEST`.
- [x] `GET /api/v1/search?q=&type=users` — empty `q` param: returns `200` with an empty list (service handles blank input, controller does not reject it).
- [x] `GET /api/v1/search/history` — authenticated: returns `200` with a list.
- [x] `DELETE /api/v1/search/history` — authenticated: returns `204 NO CONTENT`.
- [x] `GET /api/v1/hashtags/travel/posts` — authenticated: returns `200` with a list.
- [x] Any endpoint without authentication — returns `401 UNAUTHORIZED`.

## Notes

- For the `@DataJpaTest`, ensure `@BeforeEach` inserts satisfy all FK constraints (`post_hashtags` needs both `posts` and `hashtags` rows; `posts` needs a `users` row for the `user_id` FK).
- For the controller IT, stub use-case methods with `Mockito.when(...).thenReturn(...)` using sensible default domain objects (e.g., a list with one `User`, one `Hashtag`, or one `Post`).
- The `@Async` method in `SearchService` may require the test context to use a synchronous task executor. Add `@TestConfiguration` with `@Bean TaskExecutor taskExecutor() { return new SyncTaskExecutor(); }` in the test class if async history saves interfere with assertions.

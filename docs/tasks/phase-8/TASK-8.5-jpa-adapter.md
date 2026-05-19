# TASK-8.5 — JPA Entities, Repositories & Persistence Adapters

## Overview

Create all persistence-layer artefacts for the search feature. The search strategy uses `ILIKE` for all entity types:

| Entity | Strategy | Order |
|---|---|---|
| `posts` (caption) | `caption ILIKE '%query%'`, soft-deleted excluded | `created_at DESC` |
| `users` (username + full_name) | `username ILIKE '%query%' OR full_name ILIKE '%query%'`, soft-deleted excluded | `follower_count DESC` |
| `hashtags` (name) | `name ILIKE 'query%'` prefix match | `post_count DESC` |

## Requirements

- Check `docs/database/schema.sql` for exact column names and types.
- Lombok allowed: `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.
- `@Table(name = ...)` must match the schema exactly.
- Never expose JPA entities outside the `persistence` package.
- `@Component`, constructor injection only.
- The `hashtags`, `posts`, and `users` JPA entities already exist — do NOT recreate them. Add `@Query` methods to the existing Spring Data repositories where needed.

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/entity/SearchHistoryJpaEntity.java
backend/src/main/java/com/instagram/adapter/out/persistence/repository/SearchHistoryJpaRepository.java
backend/src/main/java/com/instagram/adapter/out/persistence/SearchJpaAdapter.java
backend/src/main/java/com/instagram/adapter/out/persistence/SearchHistoryPersistenceAdapter.java
```

---

## Checklist

### `SearchHistoryJpaEntity.java`

- [x] `@Entity @Table(name = "search_history")`.
- [x] Lombok: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`.
- [x] Fields (verify against schema):
  - `@Id @GeneratedValue(strategy = GenerationType.UUID) UUID id`
  - `@Column(name = "user_id", nullable = false) UUID userId`
  - `@Column(nullable = false, columnDefinition = "TEXT") String query`
  - `@Column(name = "searched_at", nullable = false) OffsetDateTime searchedAt`
- [x] `@PrePersist` sets `searchedAt = OffsetDateTime.now()` if null.
- [x] No `@ManyToOne` join to `users` — use the raw `UUID` to avoid unnecessary joins.

### `SearchHistoryJpaRepository.java`

- [x] Extends `JpaRepository<SearchHistoryJpaEntity, UUID>`.
- [x] `Page<SearchHistoryJpaEntity> findByUserIdOrderBySearchedAtDesc(UUID userId, Pageable pageable)`.
- [x] `@Modifying @Query("DELETE FROM SearchHistoryJpaEntity s WHERE s.userId = :userId") void deleteByUserId(@Param("userId") UUID userId)` — bulk delete without loading entities into memory.

---

### `SearchJpaAdapter.java`

- [x] `@Component`. Implements `SearchRepository`.
- [x] Inject `EntityManager` via constructor — used for native queries (Spring Data method derivation cannot express multi-column `ILIKE` conditions cleanly).
- [x] All native queries use `entityManager.createNativeQuery(sql, EntityClass.class)` to map results back to the existing JPA entity classes, which are then converted to domain models via private `toDomain` methods.

---

#### Implement `searchPosts` (ILIKE on `caption`)

Native SQL to use:
```sql
SELECT p.*
FROM   posts p
WHERE  p.caption ILIKE :pattern
  AND  p.deleted_at IS NULL
ORDER  BY p.created_at DESC
LIMIT  :limit
OFFSET :offset
```

- [x] Extract `limit` and `offset` from the `Pageable` argument: `pageable.getPageSize()` and `pageable.getOffset()`.
- [x] Bind `:pattern` to `'%' + query + '%'`.
- [x] Create the query: `entityManager.createNativeQuery(sql, PostJpaEntity.class)`.
- [x] Cast the result list to `List<PostJpaEntity>` and map each via `toPostDomain(entity)`.

---

#### Implement `searchUsers` (ILIKE on `username` and `full_name`)

Native SQL to use:
```sql
SELECT *
FROM   users
WHERE  (username ILIKE :pattern OR full_name ILIKE :pattern)
  AND  deleted_at IS NULL
ORDER  BY follower_count DESC
LIMIT  :limit
OFFSET :offset
```

- [x] Bind `:pattern` to `'%' + query + '%'`.
- [x] Create the query: `entityManager.createNativeQuery(sql, UserJpaEntity.class)`.
- [x] Map each result entity to a domain `User` via `toUserDomain(entity)`.

---

#### Implement `searchHashtags` (ILIKE prefix on `name`)

Native SQL to use:
```sql
SELECT *
FROM   hashtags
WHERE  name ILIKE :pattern
ORDER  BY post_count DESC
LIMIT  :limit
OFFSET :offset
```

- [x] Bind `:pattern` to `query + '%'` for prefix match. If you want substring match instead, use `'%' + query + '%'`.
- [x] `entityManager.createNativeQuery(sql, HashtagJpaEntity.class)`.
- [x] Map each result to `Hashtag` via `toHashtagDomain(entity)`.

---

#### Implement `findPostsByHashtag` (join query)

This is a browse-by-hashtag query, not a text search. Uses a join approach.

Native SQL to use:
```sql
SELECT p.*
FROM   posts p
JOIN   post_hashtags ph ON ph.post_id = p.id
JOIN   hashtags h       ON h.id = ph.hashtag_id
WHERE  LOWER(h.name) = LOWER(:name)
  AND  p.deleted_at IS NULL
ORDER  BY p.created_at DESC
LIMIT  :limit
OFFSET :offset
```

- [x] `entityManager.createNativeQuery(sql, PostJpaEntity.class)`.
- [x] Bind `:name` to the raw hashtag name (without `#`). `LOWER(h.name) = LOWER(:name)` handles case-insensitivity; alternatively, since `h.name` is `CITEXT`, just `h.name = :name` is sufficient.
- [x] Map each result to `Post` via `toPostDomain(entity)`.

---

#### Private `toDomain` mapper methods

- [x] `private User toUserDomain(UserJpaEntity e)` — maps fields from the existing Phase 1 entity to the `User` domain model. Reuse the mapping logic from `UserPersistenceAdapter` if it is accessible; otherwise write an inline mapper.
- [x] `private Hashtag toHashtagDomain(HashtagJpaEntity e)` — maps from Phase 2 entity.
- [x] `private Post toPostDomain(PostJpaEntity e)` — maps from Phase 2 entity. Reuse `PostPersistenceAdapter`'s `toDomain` if it is accessible.

---

### `SearchHistoryPersistenceAdapter.java`

- [x] `@Component`. Implements `SearchHistoryRepository`.
- [x] Inject `SearchHistoryJpaRepository` via constructor.
- [x] `save` → call `toEntity(searchHistory)`, `jpaRepo.save(entity)`, return `toDomain(saved)`.
- [x] `findByUserIdOrderBySearchedAtDesc` → call JPA repo method, map each entity with `toDomain`, return as `List`.
- [x] `deleteByUserId` → call `jpaRepo.deleteByUserId(userId)`.
- [x] Private `toEntity(SearchHistory) : SearchHistoryJpaEntity` — maps all four fields.
- [x] Private `toDomain(SearchHistoryJpaEntity) : SearchHistory` — builds via `SearchHistory.builder()`.

---

## Notes

- **`@Modifying @Query` on `deleteByUserId`** must be called from within a `@Transactional` method. Ensure `SearchService.clearSearchHistory` is annotated `@Transactional` (TASK-8.4).
- **Empty query guard:** Even though `SearchService` short-circuits blank queries, the adapter should handle them gracefully. Add a check: if `query.isBlank()`, return `Collections.emptyList()` immediately.
- **`SuppressWarnings("unchecked")`:** The `entityManager.createNativeQuery(sql, EntityClass.class)` call returns a raw `List`. Suppress the unchecked cast warning with `@SuppressWarnings("unchecked")` on the method — this is a known JPA API limitation.


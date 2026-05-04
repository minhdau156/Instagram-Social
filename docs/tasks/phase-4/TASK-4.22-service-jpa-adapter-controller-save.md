# TASK-4.22 — Service, JPA, Adapter & Controller: Saves

## Overview

Implement the complete vertical slice for the Save (bookmark) feature: domain service, JPA entity & repository, persistence adapter, and REST controller. This task bundles all four layers for the smaller Save feature to keep the task count manageable.

## Requirements

- Follow the same layered patterns established in Phase 3 and in TASK-4.5/4.6/4.7/4.8.
- Each class lives in its appropriate layer (`application/service/`, `adapter/out/persistence/`, `adapter/in/web/`).
- All layers use interfaces — no direct coupling between them.

---

## Checklist

### `SavedPostService.java` (Domain Service)

- [ ] Create `SavedPostService.java` annotated with `@Service`
- [ ] Inject via constructor: `SavedPostRepository`
- [ ] Implement `SavePostUseCase`:
  - [ ] Check `savedPostRepository.existsByPostIdAndUserId(...)` → throw `AlreadySavedException` if already saved
  - [ ] Create `SavedPost.of(command.postId(), command.userId())`
  - [ ] Call `savedPostRepository.save(savedPost)` and return it

- [ ] Implement `UnsavePostUseCase`:
  - [ ] Check `existsByPostIdAndUserId(...)` → throw `NotSavedException` if not saved
  - [ ] Call `savedPostRepository.delete(command.postId(), command.userId())`

- [ ] Implement `GetSavedPostsUseCase`:
  - [ ] Build `Pageable` from `query.page()` / `query.size()`
  - [ ] Return `savedPostRepository.findByUserId(query.userId(), pageable)`

---

### `SavedPostJpaEntity.java` (JPA Entity)

- [ ] Create `SavedPostJpaEntity.java`:
  ```java
  @Entity
  @Table(name = "saved_posts",
         uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
  public class SavedPostJpaEntity {

      @Id
      @Column(name = "id", updatable = false, nullable = false)
      private UUID id;

      @Column(name = "post_id", nullable = false)
      private UUID postId;

      @Column(name = "user_id", nullable = false)
      private UUID userId;

      @Column(name = "saved_at", nullable = false, updatable = false)
      @CreationTimestamp
      private Instant savedAt;

      // no-arg constructor, all-args constructor, getters, toDomain(), fromDomain()
  }
  ```

---

### `SavedPostJpaRepository.java`

- [ ] Create repository interface:
  ```java
  public interface SavedPostJpaRepository extends JpaRepository<SavedPostJpaEntity, UUID> {

      boolean existsByPostIdAndUserId(UUID postId, UUID userId);

      void deleteByPostIdAndUserId(UUID postId, UUID userId);

      Page<SavedPostJpaEntity> findByUserIdOrderBySavedAtDesc(UUID userId, Pageable pageable);
  }
  ```

---

### `SavedPostPersistenceAdapter.java`

- [ ] Create `SavedPostPersistenceAdapter.java` annotated with `@Component`
- [ ] Implement `SavedPostRepository`
- [ ] Inject: `SavedPostJpaRepository`
- [ ] Implement all five methods delegating to the JPA repository
- [ ] Annotate write operations (`save`, `delete`) with `@Transactional`

---

### `SaveController.java` (REST Controller)

- [ ] Annotate with `@RestController`, `@RequiredArgsConstructor`, `@Tag(name = "Saves")`
- [ ] Inject: `SavePostUseCase`, `UnsavePostUseCase`, `GetSavedPostsUseCase`

- [ ] Implement `POST /api/v1/posts/{id}/save`:
  - `@PreAuthorize("isAuthenticated()")`
  - Returns `204 No Content` on success

- [ ] Implement `DELETE /api/v1/posts/{id}/save`:
  - `@PreAuthorize("isAuthenticated()")`
  - Returns `204 No Content`

- [ ] Implement `GET /api/v1/users/me/saved`:
  - `@PreAuthorize("isAuthenticated()")`
  - Query params: `page` (default 0), `size` (default 20)
  - Returns `200 OK` with `ApiResponse<Page<SavedPostResponse>>`

- [ ] Create `SavedPostResponse.java` DTO:
  ```java
  public record SavedPostResponse(UUID id, UUID postId, UUID userId, Instant savedAt) {
      public static SavedPostResponse from(SavedPost savedPost) { ... }
  }
  ```

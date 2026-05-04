# TASK-4.21 — In-Ports: Save Use Cases

## Overview

Define all use-case interfaces for the Save (bookmark) feature. Each use case is a single-method interface with a nested `Command` or `Query` record.

## Requirements

- Lives in `domain/port/in/` — no Spring or JPA annotations.
- Each interface declares exactly **one** method.
- Follow naming and structure of existing use cases.

## File Locations

```
backend/src/main/java/com/instagram/domain/port/in/
├── SavePostUseCase.java
├── UnsavePostUseCase.java
└── GetSavedPostsUseCase.java
```

---

## Checklist

### `SavePostUseCase.java`

- [x] Create interface:
  ```java
  public interface SavePostUseCase {
      SavedPost save(Command command);

      record Command(UUID postId, UUID userId) {}
  }
  ```

### `UnsavePostUseCase.java`

- [x] Create interface:
  ```java
  public interface UnsavePostUseCase {
      void unsave(Command command);

      record Command(UUID postId, UUID userId) {}
  }
  ```

### `GetSavedPostsUseCase.java`

- [x] Create interface:
  ```java
  public interface GetSavedPostsUseCase {
      Page<SavedPost> getSavedPosts(Query query);

      record Query(UUID userId, int page, int size) {}
  }
  ```
  - The returned `Page<SavedPost>` contains `postId` references; the frontend or a separate post-enrichment step resolves the full `Post` objects.

- [x] Add domain exceptions (if needed):
  - `AlreadySavedException` — when a user tries to save a post they've already saved
  - `NotSavedException` — when a user tries to unsave a post they haven't saved
  - These can be lightweight inner classes or separate files in `domain/exception/` following the same pattern as `AlreadyLikedException`

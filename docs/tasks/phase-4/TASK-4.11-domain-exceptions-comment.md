# TASK-4.11 — Domain Exceptions: Comment

## Overview

Create domain-layer exceptions that represent business rule violations for the Comment feature. These are thrown by `CommentService` and caught by the `GlobalExceptionHandler` to produce appropriate HTTP error responses.

## Requirements

- Must live in `domain/exception/` — no Spring annotations.
- Extend `RuntimeException`.
- Follow the naming and structure of existing exceptions (e.g., `AlreadyLikedException`, `AlreadyFollowingException`).

## File Locations

```
backend/src/main/java/com/instagram/domain/exception/CommentNotFoundException.java
backend/src/main/java/com/instagram/domain/exception/UnauthorizedCommentAccessException.java
```

## Notes

- `CommentNotFoundException` is thrown when a comment with the given `commentId` does not exist.
- `UnauthorizedCommentAccessException` is thrown when a user tries to edit or delete a comment they do not own (and is not an admin/moderator).
- The `GlobalExceptionHandler` must be extended to handle these.

---

## Checklist

### `CommentNotFoundException.java`

- [x] Create `CommentNotFoundException extends RuntimeException`:

### `UnauthorizedCommentAccessException.java`

- [x] Create `UnauthorizedCommentAccessException extends RuntimeException`:

### `GlobalExceptionHandler` Update

- [x] In the existing `GlobalExceptionHandler.java`, add handlers:
  ```java
  @ExceptionHandler(CommentNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleCommentNotFound(CommentNotFoundException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(UnauthorizedCommentAccessException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnauthorizedCommentAccess(
          UnauthorizedCommentAccessException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(ApiResponse.error(ex.getMessage()));
  }
  ```

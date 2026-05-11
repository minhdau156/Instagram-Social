# TASK-6.2 — Domain Exceptions

## Overview

Create the three domain exceptions for the Direct Messaging feature and register their HTTP mappings in `GlobalExceptionHandler`.

## Requirements

- Exception classes live in `domain/exception/` — **no** Spring annotations.
- All exceptions extend `RuntimeException`.
- `GlobalExceptionHandler` already exists at `adapter/in/web/GlobalExceptionHandler.java` — add new `@ExceptionHandler` methods to it; do not create a new handler class.

## File Locations

```
backend/src/main/java/com/instagram/domain/exception/ConversationNotFoundException.java
backend/src/main/java/com/instagram/domain/exception/NotConversationMemberException.java
backend/src/main/java/com/instagram/domain/exception/MessageNotFoundException.java
backend/src/main/java/com/instagram/adapter/in/web/GlobalExceptionHandler.java  ← modify
```

---

## Checklist

### `ConversationNotFoundException.java`

- [x] Extends `RuntimeException`.
- [x] Constructor: `public ConversationNotFoundException(UUID conversationId)` — message: `"Conversation not found: " + conversationId`.

### `NotConversationMemberException.java`

- [x] Extends `RuntimeException`.
- [x] Constructor: `public NotConversationMemberException(UUID userId, UUID conversationId)` — message: `"User " + userId + " is not a member of conversation " + conversationId`.

### `MessageNotFoundException.java`

- [x] Extends `RuntimeException`.
- [x] Constructor: `public MessageNotFoundException(UUID messageId)` — message: `"Message not found: " + messageId`.

### `GlobalExceptionHandler.java` — add mappings

- [x] `@ExceptionHandler(ConversationNotFoundException.class)` → `404 NOT_FOUND` with `error` field set to the exception message.
- [x] `@ExceptionHandler(NotConversationMemberException.class)` → `403 FORBIDDEN`.
- [x] `@ExceptionHandler(MessageNotFoundException.class)` → `404 NOT_FOUND`.

## Notes

- Follow the exact response format already established in the existing `GlobalExceptionHandler` — look at how `PostNotFoundException` is mapped and replicate the pattern.
- Do **not** change any existing exception mappings.

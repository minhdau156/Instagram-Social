# TASK-6.11 — REST Controller: MessageController

## Overview

Create the REST controller for conversation and message management. These endpoints serve as the primary HTTP API and as a REST fallback for sending messages when WebSocket is unavailable.

## Requirements

- Lives in `adapter/in/web/`.
- Annotate with `@RestController @RequestMapping("/api/v1/conversations")`.
- Return `ResponseEntity<T>` with explicit status codes.
- No business logic — delegate entirely to use-case interfaces.
- Authenticated user's ID is extracted from the security context (follow the pattern used in `PostController`).

## File Location

```
backend/src/main/java/com/instagram/adapter/in/web/MessageController.java
```

---

## Checklist

### Class setup

- [ ] `@RestController @RequestMapping("/api/v1/conversations")`
- [ ] `@Tag(name = "Messaging")` for Swagger grouping.
- [ ] Constructor injects all seven use-case interfaces from TASK-6.4.

### Endpoints

| Method | Path | Use case | Response |
|--------|------|----------|----------|
| `GET` | `/` | `GetConversationsUseCase` | `200 OK` — `List<ConversationResponse>` |
| `POST` | `/` | `CreateConversationUseCase` | `201 CREATED` — `ConversationResponse` |
| `GET` | `/{id}/messages` | `GetMessagesUseCase` | `200 OK` — `List<MessageResponse>` |
| `POST` | `/{id}/messages` | `SendMessageUseCase` | `201 CREATED` — `MessageResponse` |
| `PUT` | `/{id}/read` | `MarkReadUseCase` | `204 NO_CONTENT` |
| `POST` | `/{id}/members` | `AddGroupMemberUseCase` | `204 NO_CONTENT` |
| `DELETE` | `/{id}/members/me` | `LeaveConversationUseCase` | `204 NO_CONTENT` |

### Query parameters

- `GET /` — `page` (default 0), `size` (default 20)
- `GET /{id}/messages` — `cursor` (optional UUID), `limit` (default 30, max 50)

### Validation

- [ ] `@Valid` on all `@RequestBody` parameters.
- [ ] Cap `limit` to 50 in the controller: `int effectiveLimit = Math.min(limit, 50)`.

## Notes

- For `GET /{id}/messages`, validate that the authenticated user is a member of the conversation — this is handled by `GetMessagesUseCase` (which calls `conversationRepository.isMember`), so the controller does not need an extra check.
- Follow the `PostController` pattern for extracting `currentUserId()` from `SecurityContextHolder`.
- All response DTOs are defined in TASK-6.12.

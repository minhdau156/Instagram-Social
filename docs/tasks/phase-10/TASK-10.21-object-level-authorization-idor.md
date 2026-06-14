# TASK-10.21 — Object-level authorization & IDOR audit

## Overview

Audit every endpoint that mutates a user-owned resource to confirm it checks that the requesting user actually owns the resource before acting. This class of bug is called IDOR (Insecure Direct Object Reference) and is consistently ranked the most common API vulnerability in the OWASP API Security Top 10. The fix is a single conditional in each service method; the real work is the systematic inventory — making sure no endpoint is missed.

---

## Level

**Core** — Pairs with [TASK-10.18 (Input validation hardening)](TASK-10.18-input-validation-hardening.md), which audits the input side of the adapter boundary, and [TASK-10.16 (JWT hardening)](TASK-10.16-jwt-hardening.md), which ensures the user identity carried in the JWT is trustworthy.

---

## Why

Authentication proves who you are; it does not prove that the thing you are asking to change belongs to you. If a user can delete any post by guessing its UUID — even though they are authenticated as a different user — that is an IDOR. In this project all primary keys are UUIDs, which are hard to guess, but they are not secret: they appear in URLs, in API responses, and in the browser address bar. An attacker who knows the UUID of another user's post can send `DELETE /api/v1/posts/{thatId}` with their own valid JWT and the post is gone. The fix is one line in the service: `if (!post.getUserId().equals(currentUserId)) throw new UnauthorizedPostAccessException(...)`. The absence of that line is the vulnerability.

---

## Prerequisites

- All domain services from Phases 1–9 are in place.
- Existing domain exceptions: `UnauthorizedPostAccessException`, `UnauthorizedCommentAccessException`, `UnauthorizedModerationAccessException`, `UnauthorizedNotificationAccessException` — already in `domain/exception/` and mapped in `GlobalExceptionHandler`.
- **Concept gloss:**
  - **IDOR** — Insecure Direct Object Reference. An API endpoint that accepts a resource ID and acts on it without checking whether the caller owns it.
  - **Owner check** — verifying that `resource.getOwnerId().equals(currentUserId)` (or that the caller is an admin) before any mutating operation.
  - **`403 Forbidden` vs `404 Not Found`** — the correct response for an ownership violation is `403`, not `404`. Returning `404` leaks whether the resource exists at all; `403` says "it exists but you can't touch it."
  - **`ROLE_ADMIN`** — the authority that bypasses ownership checks for moderation purposes.

---

## Files to Create / Modify

```
backend/src/main/java/com/instagram/domain/service/PostService.java                      (verify — check exists)
backend/src/main/java/com/instagram/domain/service/CommentService.java                   (verify — check exists)
backend/src/main/java/com/instagram/domain/service/UserService.java                      (verify — check exists)
backend/src/main/java/com/instagram/domain/service/FollowService.java                    (verify — check exists)
backend/src/main/java/com/instagram/adapter/in/web/PostController.java                   (verify — passes currentUserId)
backend/src/main/java/com/instagram/adapter/in/web/CommentController.java                (verify — passes currentUserId)
backend/src/test/java/com/instagram/adapter/in/web/PostControllerIT.java                 (modify — add cross-user 403 test)
backend/src/test/java/com/instagram/adapter/in/web/CommentControllerIT.java              (modify — add cross-user 403 test)
```

---

## Step-by-Step

### 1. Build an inventory of every mutating endpoint

Go through each controller file in `adapter/in/web/` and list every endpoint that mutates a user-owned resource. "User-owned" means the operation affects an entity that was created by a specific user and should not be modifiable by others.

Open each controller file and record every `@PutMapping`, `@PatchMapping`, `@DeleteMapping`, and any `@PostMapping` that creates on behalf of a user:

```
PostController:
  PUT    /api/v1/posts/{id}           → UpdatePostUseCase      owner = post.getUserId()
  DELETE /api/v1/posts/{id}           → DeletePostUseCase      owner = post.getUserId()

CommentController:
  PUT    /api/v1/comments/{id}        → UpdateCommentUseCase   owner = comment.getUserId()
  DELETE /api/v1/comments/{id}        → DeleteCommentUseCase   owner = comment.getUserId()

UserController:
  PUT    /api/v1/users/me             → UpdateProfileUseCase   owner = currentUserId (no id lookup needed)
  POST   /api/v1/users/me/avatar      → AvatarUploadUseCase    owner = currentUserId

SaveController:
  DELETE /api/v1/saves/{postId}       → RemoveSavedPostUseCase  owner = currentUserId

MessageController:
  DELETE /api/v1/messages/{id}        → DeleteMessageUseCase   owner = message.getSenderId()

FollowController:
  DELETE /api/v1/follows/requests/{id} → DenyFollowRequestUseCase owner = request.getTargetUserId()
```

Work through each one in Steps 2–5.

### 2. Verify the ownership check in each domain service

For each mutating operation, open the corresponding service class and confirm the check is there. The pattern used in this project is:

```java
// Correct pattern — UnauthorizedPostAccessException is a named domain exception
// already mapped to 403 in GlobalExceptionHandler
if (!post.getUserId().equals(command.currentUserId())) {
    throw new UnauthorizedPostAccessException(
        "User " + command.currentUserId() + " does not own post " + post.getId());
}
```

If the check is missing, add it immediately after loading the resource and before any modification.

**Example — checking `PostService.deletePost`:**

```java
// In PostService.deletePost (or DeletePostService if split by use-case)
public void deletePost(DeletePostUseCase.Command command) {
    Post post = postRepository.findById(command.postId())
            .orElseThrow(() -> new PostNotFoundException(command.postId()));

    // ← ownership check must be here
    if (!post.getUserId().equals(command.currentUserId())) {
        throw new UnauthorizedPostAccessException(
            "Cannot delete post " + command.postId() + ": not owned by " + command.currentUserId());
    }

    postRepository.deleteById(command.postId());
}
```

**Example — checking `CommentService.deleteComment`:**

```java
public void deleteComment(DeleteCommentUseCase.Command command) {
    Comment comment = commentRepository.findById(command.commentId())
            .orElseThrow(() -> new CommentNotFoundException(command.commentId()));

    if (!comment.getUserId().equals(command.currentUserId())) {
        throw new UnauthorizedCommentAccessException(
            "Cannot delete comment " + command.commentId() + ": not owned by " + command.currentUserId());
    }

    commentRepository.deleteById(command.commentId());
}
```

### 3. Ensure the controller passes `currentUserId` to every command

The service check is useless if the controller does not pass the actual authenticated user's ID. Verify each controller method extracts the user ID from `@AuthenticationPrincipal UserDetails`:

```java
// Correct — extracts the real authenticated user's ID
@DeleteMapping("/{id}")
public ResponseEntity<ApiResponse<Void>> deletePost(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails) {

    UUID currentUserId = UUID.fromString(userDetails.getUsername());
    deletePostUseCase.deletePost(new DeletePostUseCase.Command(id, currentUserId));
    return ResponseEntity.noContent().build();
}
```

The `@AuthenticationPrincipal` is set by the `JwtAuthenticationFilter` and cannot be spoofed by a client. Never accept a `userId` from the request body or query parameter for an ownership-sensitive operation.

### 4. Return `403 Forbidden`, not `404 Not Found`

Verify that all the `Unauthorized*AccessException` classes in `domain/exception/` do NOT extend `RuntimeException` with any notion of "not found". They should be distinct exception types mapped to `403` in `GlobalExceptionHandler`.

Check the existing mappings:

```java
// GlobalExceptionHandler — these must already be present from Phase 2 / Phase 4
@ExceptionHandler(UnauthorizedPostAccessException.class)
public ResponseEntity<ApiResponse<Void>> handleUnauthorizedPostAccess(UnauthorizedPostAccessException ex) {
    log.warn(ex.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage()));
}
```

If any ownership exception is mapped to `404`, change it to `403`.

### 5. Confirm admin/moderation endpoints require `ROLE_ADMIN`

The Phase 9 moderation endpoints (if implemented) should be restricted to admin users. In `SecurityConfig`, verify that admin routes require the role:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
    .requestMatchers("/api/v1/moderation/**").hasRole("ADMIN")
    // ... other rules
)
```

If these patterns are missing, add them before the `.anyRequest().authenticated()` line.

### 6. Add a cross-user authorization test for each resource type

For each owner-scoped resource, add a test that:
1. Authenticates as User A.
2. Creates a resource owned by User A.
3. Authenticates as User B (a different user).
4. Attempts to mutate User A's resource using User B's credentials.
5. Asserts `403 Forbidden`.

**Example — `PostControllerIT` cross-user delete test:**

```java
@Test
@DisplayName("DELETE /api/v1/posts/{id} — returns 403 when caller does not own the post")
void deletePost_anotherUsersPost_returns403() throws Exception {
    // Arrange: create a post as userA
    UUID postId = createPostAsUser(userAToken, "User A's post");

    // Act: attempt to delete it as userB
    mockMvc.perform(delete("/api/v1/posts/" + postId)
                    .header("Authorization", "Bearer " + userBToken))
            // Assert
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").isNotEmpty());
}
```

Add equivalent tests for comment delete, profile update (should be impossible by design since it uses `/me`, but verify), and message delete.

---

## Checklist

- [x] Inventory every endpoint that mutates a user-owned resource (posts, comments, messages, profile, saved posts, follow requests)
  - [x] Create a written list in a code comment or PR description so the audit is documented
- [x] Verify each one checks `resource.ownerId == currentUserId()` (or admin) before acting — add the check where missing
  - [x] `PostService` — `updatePost` and `deletePost` have ownership checks
  - [x] `CommentService` — `updateComment` and `deleteComment` have ownership checks
  - [x] `MessageService` (if delete is implemented) — ownership check on sender
  - [x] `FollowService` — follow request approval/denial checks target user
- [x] Return `403 Forbidden` (not `404`) for owned-resource access violations, via a named domain exception
  - [x] All `Unauthorized*AccessException` classes mapped to `403` in `GlobalExceptionHandler`
- [x] Add an authorization test per resource asserting the cross-user `403`
  - [x] `PostControllerIT` — `deletePost` cross-user test
  - [x] `CommentControllerIT` — `deleteComment` cross-user test
  - [x] (Any additional resources found in the inventory)
- [x] Confirm admin/moderation endpoints require `ROLE_ADMIN` (cross-check the Phase 9 moderation work)
  - [x] `SecurityConfig` has `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`

---

## How to Verify

**Manual `curl` test (post owned by one user, deleted by another):**

```powershell
# 1. Register two users
$userA = Invoke-RestMethod "http://localhost:8080/api/v1/auth/register" `
    -Method POST -ContentType "application/json" `
    -Body '{"username":"usera","email":"a@test.com","password":"password123","fullName":"User A"}'

$userB = Invoke-RestMethod "http://localhost:8080/api/v1/auth/register" `
    -Method POST -ContentType "application/json" `
    -Body '{"username":"userb","email":"b@test.com","password":"password123","fullName":"User B"}'

# 2. Login as User A and create a post (get a mediaUrl from /api/v1/media/upload first)
$tokenA = (Invoke-RestMethod "http://localhost:8080/api/v1/auth/login" `
    -Method POST -ContentType "application/json" `
    -Body '{"identifier":"usera","password":"password123"}').data.accessToken

$post = Invoke-RestMethod "http://localhost:8080/api/v1/posts" `
    -Method POST -ContentType "application/json" `
    -Headers @{ Authorization = "Bearer $tokenA" } `
    -Body '{"caption":"A post","mediaItems":[{"mediaUrl":"http://x/y","mediaType":"IMAGE","displayOrder":0}]}'
$postId = $post.data.id

# 3. Login as User B
$tokenB = (Invoke-RestMethod "http://localhost:8080/api/v1/auth/login" `
    -Method POST -ContentType "application/json" `
    -Body '{"identifier":"userb","password":"password123"}').data.accessToken

# 4. Attempt delete as User B
$r = Invoke-WebRequest "http://localhost:8080/api/v1/posts/$postId" `
    -Method DELETE `
    -Headers @{ Authorization = "Bearer $tokenB" } `
    -SkipHttpErrorCheck
Write-Host "Status: $($r.StatusCode)"   # Expected: 403
```

**Automated test (run the full test suite):**

```powershell
cd backend; mvn test -pl . -Dtest="PostControllerIT,CommentControllerIT"
# Expected: all tests pass including the new cross-user 403 tests
```

---

## Notes / Gotchas

**"Why not just return `404` to avoid leaking that the resource exists?"**
This is a common debate. In most social media contexts, post IDs and comment IDs are visible in API responses (e.g. when fetching another user's public profile). Returning `404` provides false obscurity — an attacker who already has the ID knows the resource exists. More importantly, `404` masks authorization bugs during testing; `403` makes the boundary explicit. Use `403` for ownership violations and `404` for genuinely missing resources.

**Admin bypass must be checked after the ownership check, not instead of it.**
The pattern should be:

```java
if (!resource.getOwnerId().equals(currentUserId)) {
    if (!isAdmin(currentUserId)) {
        throw new UnauthorizedException(...);
    }
}
```

Do not skip the ownership check entirely for admins — only skip the rejection step. This keeps the audit log complete (an admin touching a resource is still notable).

**`/api/v1/users/me` endpoints cannot have an IDOR problem by definition.**
If the controller always uses `UUID.fromString(userDetails.getUsername())` as the user ID and ignores any ID in the path, no IDOR is possible. Verify that there is no `/api/v1/users/{id}` mutation endpoint that accepts the `id` from the path rather than the JWT.

**UUID guessing is hard but not impossible.**
UUIDs are version 4 (random) in this project, making guessing practically impossible. However, an attacker can harvest UUIDs from public API responses (e.g. fetching a user's post feed exposes all the post IDs). Never rely on ID secrecy as a substitute for authorization checks.

**Reference docs:**
- [OWASP API Security Top 10 — API1:2023 Broken Object Level Authorization](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/)
- [OWASP Cheat Sheet: Authorization](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **IDOR explained** — accessing another user's object by guessing its id — https://cheatsheetseries.owasp.org/cheatsheets/Insecure_Direct_Object_Reference_Prevention_Cheat_Sheet.html
- **Broken Access Control (OWASP Top 10 #1)** — the most common web risk — https://owasp.org/Top10/A01_2021-Broken_Access_Control/
- **Authorization vs authentication** — who you are vs what you may do — https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html

### Official docs (code reference)
- **Spring Security method security (`@PreAuthorize`)** — https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html
- **Spring Security authorization** — https://docs.spring.io/spring-security/reference/servlet/authorization/index.html

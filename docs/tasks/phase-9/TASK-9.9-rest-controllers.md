# TASK-9.9 — REST Controllers: ModerationController, AdminController

## Overview

Create two REST controllers. `ModerationController` exposes user-facing moderation endpoints (submit report, block, unblock, view blocked users). `AdminController` exposes admin-only endpoints for reviewing reports and managing user accounts. Both controllers follow the project's existing controller conventions — they contain zero business logic and delegate entirely to use-case interfaces.

This task also covers the required changes to `SecurityConfig` to secure the `/api/v1/admin/**` path family with role-based access control.

---

## Requirements

- `@RestController` + `@RequestMapping`.
- `@Tag(name = "...")` for Swagger grouping.
- Return `ResponseEntity<ApiResponse<T>>` on all endpoints — consistent with existing controllers.
- `@Valid` on all `@RequestBody` parameters.
- Use the existing `currentUserId()` helper pattern from other controllers to resolve the authenticated user's UUID from the security context.
- No business logic in controllers — delegate entirely to use-case interfaces via constructor-injected `final` fields.

---

## File Locations

```
backend/src/main/java/com/instagram/adapter/in/web/ModerationController.java   ← create
backend/src/main/java/com/instagram/adapter/in/web/AdminController.java         ← create
backend/src/main/java/com/instagram/infrastructure/security/SecurityConfig.java  ← modify
```

---

## Checklist

### SecurityConfig.java — Prerequisites

- [x] Before creating `AdminController`, add a `requestMatchers("/api/v1/admin/**").hasRole("ADMIN")` rule to the `authorizeHttpRequests` chain in `SecurityConfig.java`. This ensures any request to an admin endpoint without a valid `ROLE_ADMIN` JWT claim is rejected at the filter chain level with `403 FORBIDDEN`, before the request ever reaches the controller.
- [x] Add `@EnableMethodSecurity` to `SecurityConfig` (or a nearby `@Configuration` class) if it is not already present. This activates `@PreAuthorize` annotations on service methods as a second layer of defence.
- [x] Verify that the JWT token generation (in `JwtTokenProvider` or the equivalent class) includes the user's role as a claim in the token payload (e.g., `"role": "ADMIN"` or `"roles": ["ROLE_ADMIN"]`). If it does not, update the JWT generation and the `JwtAuthenticationFilter` parsing logic to load the role and convert it into a Spring `GrantedAuthority`. This is a prerequisite for both `hasRole("ADMIN")` in `SecurityConfig` and `@PreAuthorize` in `AdminService` to function correctly.

---

### `ModerationController.java`

#### Class-level setup

- [x] Annotate with `@RestController`.
- [x] Annotate with `@RequestMapping` — no base path at class level because the endpoints span two resource paths (`/api/v1/reports` and `/api/v1/users`).
- [x] Annotate with `@Tag(name = "Moderation")` for Swagger.
- [x] Inject via constructor (all `final`):
  - `ReportContentUseCase`
  - `BlockUserUseCase`
  - `UnblockUserUseCase`
  - `GetBlockedUsersUseCase`

#### `POST /api/v1/reports`

- [x] Accepts a `@Valid @RequestBody ReportRequest request`.
- [x] Resolves `currentUserId()` from the security context.
- [x] Builds `ReportContentUseCase.Command` using `currentUserId`, `request.entityType()`, `request.entityId()`, `request.reason()`, `request.details()`.
- [x] Calls `reportContentUseCase.reportContent(command)`.
- [x] Maps the returned `Report` domain object to `ReportResponse.from(report)`.
- [x] Returns `ResponseEntity` with status `201 CREATED` and the response body wrapped in `ApiResponse`.

#### `POST /api/v1/users/{username}/block`

- [x] Path variable: `@PathVariable String username`.
- [x] No request body.
- [x] Resolves `currentUserId()`.
- [x] Builds `BlockUserUseCase.Command` using `currentUserId` and `username`.
- [x] Calls `blockUserUseCase.blockUser(command)`.
- [x] Returns `ResponseEntity` with status `200 OK` and a body containing a success message (or `204 NO CONTENT` — choose one and be consistent with the unblock endpoint).

#### `DELETE /api/v1/users/{username}/block`

- [x] Path variable: `@PathVariable String username`.
- [x] Resolves `currentUserId()`.
- [x] Builds `UnblockUserUseCase.Command` using `currentUserId` and `username`.
- [x] Calls `unblockUserUseCase.unblockUser(command)`.
- [x] Returns `ResponseEntity` with status `204 NO CONTENT`.

#### `GET /api/v1/users/me/blocked`

- [x] Query params: `@RequestParam(defaultValue = "0") int page`, `@RequestParam(defaultValue = "20") int size`.
- [x] Resolves `currentUserId()`.
- [x] Builds `GetBlockedUsersUseCase.Query` using `currentUserId`, `page`, `size`.
- [x] Calls `getBlockedUsersUseCase.getBlockedUsers(query)`.
- [x] Maps each `UserBlock` to `BlockedUserResponse` — the response must include the blocked user's username and avatar. Since `UserBlock` only carries UUIDs, the controller must batch-fetch the blocked user profiles using an injected `GetUserUseCase` (or `UserRepository` — check how `NotificationController` resolves actor usernames and follow the same batch-fetch pattern).
- [x] Returns `200 OK` with the list.

---

### `AdminController.java`

#### Class-level setup

- [x] Annotate with `@RestController`.
- [x] Annotate with `@RequestMapping("/api/v1/admin")`.
- [x] Annotate with `@Tag(name = "Admin")` for Swagger.
- [x] Inject via constructor:
  - `ReviewReportUseCase`
  - `SuspendUserUseCase`
  - `UnsuspendUserUseCase`
  - `AdminGetReportsUseCase`

#### `GET /api/v1/admin/reports`

- [x] Query params: `@RequestParam(required = false) ReportStatus status`, `@RequestParam(defaultValue = "0") int page`, `@RequestParam(defaultValue = "20") int size`.
- [x] When `status` query param is omitted, it is `null`, and the service returns all reports.
- [x] Builds `AdminGetReportsUseCase.Query` using `status`, `page`, `size`.
- [x] Calls `adminGetReportsUseCase.getReports(query)`.
- [x] Maps each `Report` to `ReportResponse`. The `ReportResponse` must include the reporter's username — batch-fetch the reporter profiles using the same batch-fetch pattern as `NotificationController`.
- [x] Returns `200 OK` with the list wrapped in `ApiResponse`.

#### `PUT /api/v1/admin/reports/{id}`

- [x] Path variable: `@PathVariable UUID id`.
- [x] Request body: `@Valid @RequestBody ReviewReportRequest request`.
- [x] Resolves `currentUserId()` (the admin's UUID).
- [x] Builds `ReviewReportUseCase.Command` using `currentUserId`, `id`, `request.action()`.
- [x] Calls `reviewReportUseCase.reviewReport(command)`.
- [x] Maps the updated `Report` to `ReportResponse`.
- [x] Returns `200 OK`.

#### `PUT /api/v1/admin/users/{id}/suspend`

- [x] Path variable: `@PathVariable UUID id`.
- [x] Request body: `@Valid @RequestBody SuspendUserRequest request`.
- [x] Resolves `currentUserId()` (the admin's UUID).
- [x] Builds `SuspendUserUseCase.Command` using `currentUserId`, `id`, `request.reason()`.
- [x] Calls `suspendUserUseCase.suspendUser(command)`.
- [x] Returns `200 OK` with the updated user summary in `ApiResponse`.

#### `PUT /api/v1/admin/users/{id}/unsuspend`

- [x] Path variable: `@PathVariable UUID id`.
- [x] No request body.
- [x] Resolves `currentUserId()`.
- [x] Builds `UnsuspendUserUseCase.Command` using `currentUserId`, `id`.
- [x] Calls `unsuspendUserUseCase.unsuspendUser(command)`.
- [x] Returns `200 OK`.

#### `GET /api/v1/admin/users`

- [x] Query params: `@RequestParam(required = false) String username` (partial match filter), `@RequestParam(required = false) String status` (account status filter), `@RequestParam(defaultValue = "0") int page`, `@RequestParam(defaultValue = "20") int size`.
- [x] This endpoint requires a new use case — `AdminListUsersUseCase` — or it can delegate to the existing `SearchUsersUseCase` with an admin-specific query. Decide which approach is cleaner. If reusing `SearchUsersUseCase`, note that it does not support filtering by account status. If creating a new use case, add it to TASK-9.4 and implement it in `AdminService`.
- [x] Returns `200 OK` with a paginated list of `AdminUserResponse` records, each containing: `id`, `username`, `email`, `fullName`, `accountStatus`, `isVerified`, `createdAt`.

#### URL Ordering

- [x] Confirm that `GET /api/v1/admin/reports` is declared before `PUT /api/v1/admin/reports/{id}` in the class. Spring MVC matches literal paths before parameterised paths, so this is not strictly required, but it improves readability.

---

## Notes

- The `currentUserId()` helper already exists in other controllers (e.g., `PostController`, `NotificationController`, `SearchController`). Copy the exact same implementation — do not re-implement it.
- For the `GET /api/v1/users/me/blocked` endpoint, the batch-fetch of blocked user profiles follows the same pattern used in `NotificationController.getNotifications()` — build a `Set<UUID>` of user IDs from the result list, then call `getUserUseCase.getUsersByIds(ids)` (or equivalent) to fetch profiles in a single query, then assemble the response.
- The `status` query parameter for `GET /api/v1/admin/reports` must be bound as `ReportStatus` (the domain enum). Spring MVC can automatically convert a string query param to an enum value if the enum is registered as a `@RequestParam` type — this works automatically for standard Spring enums. Verify that `ReportStatus` values are uppercase (`PENDING`, `REVIEWED`, etc.) so they match what the UI sends.

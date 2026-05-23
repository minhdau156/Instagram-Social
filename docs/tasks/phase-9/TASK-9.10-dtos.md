# TASK-9.10 — DTOs: Request & Response

## Overview

Create all request and response DTOs for the moderation and admin features. All DTOs are Java records. Request records carry Bean Validation annotations. Response records have static `from(DomainModel)` factory methods. Following the project convention, all DTOs live in `adapter/in/web/dto/request/` and `adapter/in/web/dto/response/`.

---

## Requirements

- Java records only — no classes with setters.
- Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Size`, `@Pattern`) on all request record fields that have constraints.
- Static `from(...)` factory methods on response records — no logic beyond field mapping.
- No domain model types or JPA entities in response records — all fields are primitive, `String`, or collections of primitives.
- Keep all DTOs in the correct sub-package, not in the controller file.

---

## File Locations

```
backend/src/main/java/com/instagram/adapter/in/web/dto/request/ReportRequest.java
backend/src/main/java/com/instagram/adapter/in/web/dto/request/ReviewReportRequest.java
backend/src/main/java/com/instagram/adapter/in/web/dto/request/SuspendUserRequest.java

backend/src/main/java/com/instagram/adapter/in/web/dto/response/ReportResponse.java
backend/src/main/java/com/instagram/adapter/in/web/dto/response/BlockedUserResponse.java
backend/src/main/java/com/instagram/adapter/in/web/dto/response/AdminUserResponse.java
```

---

## Checklist

### `ReportRequest.java`

- [ ] Record fields:
  - `@NotNull ReportEntityType entityType` — the type of entity being reported. Use the domain `ReportEntityType` enum directly. Spring MVC converts the JSON string to the enum automatically.
  - `@NotNull UUID entityId` — the UUID of the reported content.
  - `@NotBlank @Size(max = 100) String reason` — a short reason code. The allowed values should be documented in the field's `@Schema` annotation for Swagger (e.g., `"SPAM"`, `"HATE_SPEECH"`, `"NUDITY"`, `"VIOLENCE"`, `"HARASSMENT"`, `"FALSE_INFORMATION"`). Consider whether to use a `ReportReason` enum instead of a free-form string — an enum is safer because it prevents invalid reasons from reaching the database. If using an enum, define `ReportReason` in `domain/model/` and use it as the field type here.
  - `@Size(max = 1000) String details` — optional; may be absent or blank.
- [ ] Example valid JSON body for Swagger documentation: `{ "entityType": "POST", "entityId": "uuid-here", "reason": "SPAM", "details": "This post is promoting fake products" }`.

---

### `ReviewReportRequest.java`

- [ ] Record fields:
  - `@NotNull ReviewReportUseCase.Command.ReviewAction action` — the review decision. Uses the inner enum from TASK-9.4. Valid values: `RESOLVE`, `DISMISS`, `MARK_REVIEWED`.
- [ ] This record intentionally has no `resolutionNote` field — the `reports` table in the schema has no such column. If the team decides to add a resolution note in future, a Flyway migration and schema update will be required first.

---

### `SuspendUserRequest.java`

- [ ] Record fields:
  - `@NotBlank @Size(max = 500) String reason` — the human-readable reason for the suspension. This is written to the audit log and may be communicated to the suspended user. Mandatory.

---

### `ReportResponse.java`

- [ ] Record fields:
  - `String id` — report UUID as string.
  - `String reporterId` — the reporter's UUID as string.
  - `String reporterUsername` — the reporter's username. Because `Report` only carries `reporterId` (a UUID), the factory method must accept both a `Report` and a `User` (the resolved reporter) as parameters.
  - `String entityType` — the `ReportEntityType` enum name as a string (e.g., `"POST"`).
  - `String entityId` — the reported entity's UUID as string.
  - `String reason`
  - `String details` — nullable.
  - `String status` — the `ReportStatus` enum name as a string.
  - `String reviewedById` — nullable UUID as string.
  - `String reviewedAt` — nullable ISO 8601 timestamp.
  - `String createdAt` — ISO 8601 timestamp.
- [ ] Factory method signature: `public static ReportResponse from(Report report, User reporter)` — two-argument form because `Report` alone does not carry the reporter's username.
- [ ] Null-safe handling for nullable fields: `reviewedAt` and `reviewedById` may be `null` — the record should accept `null` for those positions; the JSON serialiser will omit or null them.

---

### `BlockedUserResponse.java`

- [ ] Record fields:
  - `String blockedUserId` — the blocked user's UUID as string.
  - `String username` — the blocked user's username.
  - `String fullName` — the blocked user's display name; may be null.
  - `String avatarUrl` — nullable.
  - `String blockedAt` — ISO 8601 timestamp of when the block was created.
- [ ] Factory method signature: `public static BlockedUserResponse from(UserBlock block, User blockedUser)` — requires both the `UserBlock` (for `blockedAt`) and the resolved `User` profile (for username and avatar).

---

### `AdminUserResponse.java`

- [ ] Record fields:
  - `String id` — UUID as string.
  - `String username`
  - `String email`
  - `String fullName` — nullable.
  - `String accountStatus` — the `account_status` enum value as a string (e.g., `"ACTIVE"`, `"SUSPENDED"`).
  - `boolean isVerified`
  - `String createdAt` — ISO 8601.
  - `String lastLoginAt` — ISO 8601; nullable.
- [ ] Factory method: `public static AdminUserResponse from(User user)` — maps all fields directly from the `User` domain model. Note that `User.getStatus()` returns a `UserStatus` enum — call `.name()` on it to convert to a string for the response.
- [ ] Do NOT include sensitive fields such as `passwordHash`, `phoneNumber`, or refresh tokens.

---

## Notes

- All UUID fields in responses are `String`, not `UUID` — Axios deserialises UUIDs as strings in TypeScript.
- The `from(Report, User)` and `from(UserBlock, User)` two-argument factory methods are required because the domain models deliberately do not embed nested profile objects. The controller is responsible for enriching results before mapping — follow the same batch-fetch pattern used in `NotificationController` and `SearchController`.
- `@Size(max = 100)` on `ReportRequest.reason` and `@Size(max = 1000)` on `ReportRequest.details` match the database column constraints (`VARCHAR(255)` for reason and `TEXT` for details). The DTO constraint is tighter than the DB constraint — that is intentional to avoid near-limit edge cases.
- If you decide to use a `ReportReason` enum for the `reason` field, document the full list of valid reasons in the enum definition: `SPAM`, `HATE_SPEECH`, `NUDITY`, `VIOLENCE`, `HARASSMENT`, `FALSE_INFORMATION`, `SELF_HARM`, `OTHER`. Align this list with the frontend `ReportReason` type in TASK-9.12.

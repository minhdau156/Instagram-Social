# TASK-9.3 — Out-Ports: ModerationRepository, AuditLogRepository

## Overview

Define two driven-port interfaces for the moderation domain. `ModerationRepository` handles all read and write operations for `reports` and `user_blocks`. `AuditLogRepository` provides a single write method for appending audit trail entries. Both interfaces are pure Java — they define what the domain needs from the infrastructure, without specifying how it is implemented.

---

## Requirements

- Both interfaces live in `domain/port/out/` — no Spring, JPA, or Lombok imports.
- Method signatures use only domain types: `Report`, `UserBlock`, `ReportStatus`. Never reference JPA entities.
- `Pageable` from `org.springframework.data.domain` is the only permitted Spring import (consistent with `SearchHistoryRepository` and `NotificationRepository` precedents).
- Return `Optional<T>` for single-entity lookups that may produce no result.

---

## File Locations

```
backend/src/main/java/com/instagram/domain/port/out/ModerationRepository.java
backend/src/main/java/com/instagram/domain/port/out/AuditLogRepository.java
```

---

## Checklist

### `ModerationRepository.java`

#### Report Methods

- [x] `Report saveReport(Report report)` — persists a new report (or updates an existing one after a status change). Returns the saved instance with a populated `id` field.
- [x] `Optional<Report> findReportById(UUID reportId)` — looks up a report by its primary key. Returns `Optional.empty()` if not found. The service layer converts an empty result to `ReportNotFoundException`.
- [x] `List<Report> findAllReports(ReportStatus status, Pageable pageable)` — returns all reports filtered by the given status, ordered by `created_at DESC`. When `status` is `null`, return all reports regardless of status (used by the admin dashboard to show everything). The implementation in TASK-9.7 will handle the conditional WHERE clause.
- [x] `List<Report> findPendingReports(Pageable pageable)` — convenience method returning only reports with `status = PENDING`, ordered by `created_at ASC` (oldest first, so admins handle the queue in order). This is a narrower form of `findAllReports(PENDING, pageable)` but exposed as a named method because the admin queue is a primary use case.
- [x] `long countByStatus(ReportStatus status)` — returns the count of reports with the given status. Used by the admin dashboard stats cards.
- [x] `boolean existsByReporterIdAndEntityId(UUID reporterId, UUID entityId)` — checks whether the reporter has already submitted a report for this specific entity. Used in `ModerationService` to prevent duplicate reports from the same user against the same content.

#### Block Methods

- [x] `UserBlock saveBlock(UserBlock block)` — persists a new block. Returns the saved instance.
- [x] `void deleteBlock(UUID blockerId, UUID blockedId)` — removes the block. Does not throw if the relationship does not exist; the service layer performs the existence check before calling this method.
- [x] `boolean isBlocked(UUID blockerId, UUID blockedId)` — returns `true` if a block relationship exists where `blockerId` is the blocker and `blockedId` is the target. This is a directional check — `isBlocked(A, B)` can be true while `isBlocked(B, A)` is false.
- [x] `boolean isEitherBlocked(UUID userA, UUID userB)` — returns `true` if EITHER `userA` has blocked `userB` OR `userB` has blocked `userA`. This bidirectional check is used by the feed and search adapters (TASK-9.8) to exclude content on both sides of a block.
- [x] `List<UserBlock> findBlocksByBlockerId(UUID blockerId, Pageable pageable)` — returns all block records where the given user is the blocker. Ordered by `created_at DESC`. Used to render the "Blocked Accounts" settings page.
- [x] `List<UUID> findBlockedUserIdsByBlockerId(UUID blockerId)` — returns the full list of `blocked_id` values for a given blocker as a plain `List<UUID>`. Intended for the block-filter utility (TASK-9.8) which needs a set of excluded IDs to inject into feed/search queries. Does NOT accept `Pageable` — it fetches all blocked IDs for the current user.
- [x] `List<UUID> findBlockerIdsByBlockedId(UUID blockedId)` — returns all users who have blocked the given user. Also used by the block-filter to ensure neither party can see the other's content.

---

### `AuditLogRepository.java`

- [x] `void log(UUID actorId, String action, String entityType, UUID entityId, String metadata, String ipAddress)` — appends a single audit log entry. All parameters map directly to columns in the `audit_logs` table. Parameters `entityType`, `entityId`, `metadata`, and `ipAddress` are nullable (pass `null` when the action does not involve a specific entity or does not carry metadata). This method is always fire-and-forget — it must not block the main business flow and should never throw a checked exception. If logging fails, it should catch the exception, log it to the application logger (SLF4J), and return silently.
  - `actorId` → `user_id` column
  - `action` → `action VARCHAR(100)` — use descriptive constants such as `"report_submit"`, `"user_block"`, `"user_unblock"`, `"report_resolve"`, `"report_dismiss"`, `"user_suspend"`, `"user_unsuspend"`. Define these as `public static final String` constants in the `AuditLogRepository` interface itself or in a companion `AuditActions` constants class in `domain/model/`.
  - `entityType` → `entity_type VARCHAR(50)`
  - `entityId` → `entity_id UUID`
  - `metadata` → `metadata` JSONB stored as a plain `String` (JSON string passed in by the caller; the persistence adapter stores it without parsing)
  - `ipAddress` → `ip_address INET` stored as a `String` in the domain (the persistence adapter handles conversion to the INET type)

---

## Notes

- `findBlockedUserIdsByBlockerId` and `findBlockerIdsByBlockedId` deliberately return `List<UUID>` rather than `List<UserBlock>`. The block-filter utility only needs IDs, not full domain objects, so returning full domain objects would be wasteful.
- Do NOT add a `findReportsByReporterId(UUID, Pageable)` method at this stage — user-facing report history is out of scope for Phase 9. The admin-facing methods are sufficient.
- The `AuditLogRepository.log()` method intentionally lacks a return type. Audit logs are append-only and callers do not need a reference to the created record.
- `existsByReporterIdAndEntityId` is important for UX: without it, the same user could spam reports against a single post. The service must call this before calling `saveReport`.

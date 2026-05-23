# TASK-9.1 — Domain Models: Report, UserBlock

## Overview

Create two domain models for the content moderation feature. `Report` represents a user-submitted report against a post, comment, user, or message. `UserBlock` represents a unidirectional block relationship between two users. Both models must match the `reports` and `user_blocks` tables in `docs/database/schema.sql` exactly.

Follow the same hand-written Builder pattern used in `Post.java`, `SearchHistory.java`, and `Notification.java`. No Lombok, no Spring, no JPA annotations in this layer.

---

## Requirements

- Both files live in `domain/model/` — zero Spring, JPA, or Lombok imports allowed.
- Use `Post.java` as the canonical reference for the Builder pattern.
- Every field must correspond exactly to a column in `docs/database/schema.sql`. Do not invent fields not backed by the schema.
- Domain models are immutable after construction — expose getters only, no setters.
- Business behaviour belongs inside the entity as methods that return new instances via a copy.

---

## File Locations

```
backend/src/main/java/com/instagram/domain/model/Report.java
backend/src/main/java/com/instagram/domain/model/UserBlock.java
```

---

## Checklist

### Schema Verification

- [ ] Open `docs/database/schema.sql` and locate the `reports` table before writing any code.
  - Exact columns: `id UUID`, `reporter_id UUID NOT NULL FK`, `entity_type report_entity_type NOT NULL`, `entity_id UUID NOT NULL`, `reason VARCHAR(255) NOT NULL`, `details TEXT` (nullable), `status report_status NOT NULL DEFAULT 'PENDING'`, `reviewed_by_id UUID FK` (nullable), `reviewed_at TIMESTAMPTZ` (nullable), `created_at TIMESTAMPTZ NOT NULL`
  - The schema uses a PostgreSQL ENUM `report_entity_type` with values `user`, `post`, `comment`, `message`.
  - The schema uses a PostgreSQL ENUM `report_status` with values `pending`, `reviewed`, `resolved`, `dismissed`.
  - There is **no** `resolution_note` column in the schema — do not add one to the domain model or JPA entity.

- [ ] Open `docs/database/schema.sql` and locate the `user_blocks` table.
  - Exact columns: `blocker_id UUID NOT NULL FK`, `blocked_id UUID NOT NULL FK`, `created_at TIMESTAMPTZ NOT NULL`
  - Composite primary key: `(blocker_id, blocked_id)`.
  - Database-level constraint `chk_no_self_block` enforces `blocker_id <> blocked_id`. The domain model must enforce this rule too before the data ever reaches the database.

---

### `ReportEntityType.java` (inner enum or separate file)

- [ ] Define a Java enum `ReportEntityType` with exactly four values matching the PostgreSQL ENUM: `USER`, `POST`, `COMMENT`, `MESSAGE` (uppercased to follow Java convention; the JPA entity handles lowercase conversion for the database).
- [ ] Decide whether to declare it as a top-level file in `domain/model/` or as a nested type inside `Report`. A top-level file is preferred for clarity, since `SearchJpaAdapter` and future adapters will reference it independently.

### `ReportStatus.java` (inner enum or separate file)

- [ ] Define a Java enum `ReportStatus` with exactly four values: `PENDING`, `REVIEWED`, `RESOLVED`, `DISMISSED`.
- [ ] These values match the `report_status` PostgreSQL ENUM. The JPA entity (TASK-9.6) will handle the lowercase-to-uppercase conversion.

---

### `Report.java`

#### Fields

- [ ] `UUID id` — null before first persistence; assigned by the persistence adapter after the INSERT.
- [ ] `UUID reporterId` — the user who submitted the report; maps to `reporter_id`.
- [ ] `ReportEntityType entityType` — the type of content being reported; maps to `entity_type`.
- [ ] `UUID entityId` — the UUID of the reported entity (post ID, comment ID, user ID, or message ID); maps to `entity_id`.
- [ ] `String reason` — a short mandatory reason label; maps to `reason VARCHAR(255)`.
- [ ] `String details` — optional free-text description from the reporter; maps to `details TEXT`; may be `null`.
- [ ] `ReportStatus status` — lifecycle status, starts as `PENDING`; maps to `status`.
- [ ] `UUID reviewedById` — the admin who reviewed the report; maps to `reviewed_by_id`; `null` until reviewed.
- [ ] `OffsetDateTime reviewedAt` — when the review action was performed; maps to `reviewed_at`; `null` until reviewed.
- [ ] `OffsetDateTime createdAt` — when the report was created; maps to `created_at`.

#### Builder

- [ ] Static inner class `Builder` with a fluent setter method for every field listed above.
- [ ] Static factory method `builder()` that returns a new `Builder` instance.
- [ ] `Builder.build()` creates the `Report` instance and enforces null-checks: `reporterId`, `entityType`, `entityId`, `reason`, and `createdAt` must not be null. `status` must not be null (default it to `ReportStatus.PENDING` in `build()` if the caller did not set it).
- [ ] Private constructor — only the `Builder` can construct a `Report`.

#### Getters

- [ ] Plain getter methods for all ten fields: `getId()`, `getReporterId()`, `getEntityType()`, `getEntityId()`, `getReason()`, `getDetails()`, `getStatus()`, `getReviewedById()`, `getReviewedAt()`, `getCreatedAt()`.

#### Business Methods

- [ ] `withResolved(UUID reviewerId)` — returns a **new** `Report` instance that is a copy of `this` but with `status = RESOLVED`, `reviewedById = reviewerId`, and `reviewedAt = OffsetDateTime.now()`. The original instance is not mutated.
- [ ] `withDismissed(UUID reviewerId)` — same as above but with `status = DISMISSED`.
- [ ] `withReviewed(UUID reviewerId)` — same as above but with `status = REVIEWED`. This intermediate status means the report has been seen but not yet acted upon.
- [ ] All three methods must guard against being called when the report is already in a terminal state (`RESOLVED` or `DISMISSED`). Throw `IllegalStateException` with a descriptive message if the caller attempts to transition from a terminal state.

#### Copy Helper

- [ ] Private `copy()` method that creates a shallow copy of the current instance. This is used internally by the `withXxx()` methods to produce new instances without mutating `this`. Follow the same pattern as `Post.java`.

---

### `UserBlock.java`

#### Fields

- [ ] `UUID blockerId` — the user who initiated the block; maps to `blocker_id`.
- [ ] `UUID blockedId` — the user who is being blocked; maps to `blocked_id`.
- [ ] `OffsetDateTime createdAt` — when the block was created; maps to `created_at`.
- [ ] Note: there is no surrogate `id` column in `user_blocks` — the composite key `(blocker_id, blocked_id)` IS the primary key. Do NOT add an `id` field to this domain model.

#### Builder

- [ ] Static inner class `Builder` with fluent setters for all three fields.
- [ ] Static factory method `builder()`.
- [ ] `Builder.build()` enforces: `blockerId` must not be null, `blockedId` must not be null, `blockerId` must not equal `blockedId` (throw `IllegalArgumentException` with the message "A user cannot block themselves" if they are equal). This mirrors the `chk_no_self_block` database constraint.

#### Getters

- [ ] `getBlockerId()`, `getBlockedId()`, `getCreatedAt()`.

#### No Business Methods Needed

- [ ] `UserBlock` is a write-once relationship record — it is created or deleted, never updated. No `withXxx()` methods are required.

---

## Notes

- The `ReportEntityType` and `ReportStatus` enums must be placed in `domain/model/` alongside the entities, not in the adapter or JPA layer. The JPA adapter in TASK-9.6 will reference these same enums when mapping to/from JPA entities.
- Do NOT import `org.springframework.*`, `jakarta.persistence.*`, or `lombok.*` anywhere in this file. The domain layer is a pure Java library.
- The schema's `report_status` default is `'PENDING'`. Enforce this default in `Report.Builder.build()` (not in the JPA entity) so the business invariant is expressed in the domain, not the database.
- `OffsetDateTime` is the correct type for all timestamp fields, consistent with `Post.java` and `Notification.java`.

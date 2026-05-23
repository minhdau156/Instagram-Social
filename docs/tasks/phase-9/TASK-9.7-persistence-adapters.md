# TASK-9.7 — Persistence Adapters

## Overview

Create two persistence adapters that bridge the domain's out-port interfaces and the Spring Data JPA repositories. `ModerationPersistenceAdapter` implements `ModerationRepository`. `AuditLogPersistenceAdapter` implements `AuditLogRepository`. All JPA-to-domain and domain-to-JPA mapping logic lives in private helper methods inside each adapter — JPA entities must never escape this package.

---

## Requirements

- Annotate each adapter with `@Component`.
- Constructor injection only. All dependencies `final`.
- Each adapter exposes only the methods defined in its corresponding out-port interface — nothing more.
- Private `toEntity(DomainModel)` and `toDomain(JpaEntity)` methods handle all field-by-field mapping.
- JPA entities are never returned from or passed into public methods — only domain model types cross the public boundary.
- Follow the exact same structure as `SearchHistoryPersistenceAdapter.java` and `NotificationPersistenceAdapter.java` as references.

---

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/ModerationPersistenceAdapter.java
backend/src/main/java/com/instagram/adapter/out/persistence/AuditLogPersistenceAdapter.java
```

---

## Checklist

### `ModerationPersistenceAdapter.java`

#### Setup

- [ ] Annotate with `@Component`.
- [ ] Inject `ReportJpaRepository` and `UserBlockJpaRepository` via constructor.

#### Report Methods

- [ ] Implement `saveReport(Report report)`:
  - Call `toReportEntity(report)` to convert the domain model to a JPA entity.
  - Call `reportJpaRepository.save(entity)` and capture the result.
  - Call `toReportDomain(saved)` to convert back to a domain model.
  - Return the domain model.

- [ ] Implement `findReportById(UUID reportId)`:
  - Call `reportJpaRepository.findById(reportId)`.
  - Map the result with `.map(this::toReportDomain)`.
  - Return as `Optional<Report>`.

- [ ] Implement `findAllReports(ReportStatus status, Pageable pageable)`:
  - If `status` is `null`, call `reportJpaRepository.findAllByOrderByCreatedAtDesc(pageable)`.
  - Otherwise, call `reportJpaRepository.findByStatusOrderByCreatedAtDesc(status, pageable)`.
  - Map each entity in the returned `Page` to a domain `Report` using `toReportDomain`.
  - Return as `List<Report>`.

- [ ] Implement `findPendingReports(Pageable pageable)`:
  - Call `reportJpaRepository.findByStatusOrderByCreatedAtAsc(ReportStatus.PENDING, pageable)`.
  - Map and return as `List<Report>`.

- [ ] Implement `countByStatus(ReportStatus status)`:
  - Delegate directly to `reportJpaRepository.countByStatus(status)`.
  - Return the `long` count.

- [ ] Implement `existsByReporterIdAndEntityId(UUID reporterId, UUID entityId)`:
  - Delegate directly to `reportJpaRepository.existsByReporterIdAndEntityId(reporterId, entityId)`.
  - Return the `boolean`.

#### Block Methods

- [ ] Implement `saveBlock(UserBlock block)`:
  - Call `toBlockEntity(block)` to produce the JPA entity with a correctly constructed `UserBlockId`.
  - Call `userBlockJpaRepository.save(entity)`.
  - Return `toBlockDomain(saved)`.

- [ ] Implement `deleteBlock(UUID blockerId, UUID blockedId)`:
  - Call `userBlockJpaRepository.deleteByBlockerIdAndBlockedId(blockerId, blockedId)`.
  - This method is `@Modifying @Transactional` on the repository — ensure the adapter method or its caller is also `@Transactional` so the transaction context is propagated.

- [ ] Implement `isBlocked(UUID blockerId, UUID blockedId)`:
  - Call `userBlockJpaRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)`.
  - Return the `boolean`.

- [ ] Implement `isEitherBlocked(UUID userA, UUID userB)`:
  - Call `isBlocked(userA, userB)` OR `isBlocked(userB, userA)`.
  - Return `true` if either direction is blocked.

- [ ] Implement `findBlocksByBlockerId(UUID blockerId, Pageable pageable)`:
  - Call `userBlockJpaRepository.findByIdBlockerIdOrderByCreatedAtDesc(blockerId, pageable)`.
  - Map each entity to `toBlockDomain`.
  - Return as `List<UserBlock>`.

- [ ] Implement `findBlockedUserIdsByBlockerId(UUID blockerId)`:
  - Call `userBlockJpaRepository.findBlockedIdsByBlockerId(blockerId)`.
  - Return the `List<UUID>` directly — no mapping needed.

- [ ] Implement `findBlockerIdsByBlockedId(UUID blockedId)`:
  - Call `userBlockJpaRepository.findBlockerIdsByBlockedId(blockedId)`.
  - Return the `List<UUID>` directly.

#### Private Mapping Methods

- [ ] `private ReportJpaEntity toReportEntity(Report report)`:
  - Use the Lombok `@Builder` on `ReportJpaEntity` to construct the entity.
  - Map all fields including `id` (may be `null` for new reports — JPA will generate it).
  - Map `entityType` and `status` using the Java enum values. If an `AttributeConverter` is in use for the PostgreSQL ENUMs, ensure the entity fields carry the correct type so the converter is invoked automatically.

- [ ] `private Report toReportDomain(ReportJpaEntity entity)`:
  - Use `Report.builder()` and populate all ten fields from the entity.

- [ ] `private UserBlockJpaEntity toBlockEntity(UserBlock block)`:
  - Construct a `UserBlockId` with `blockerId` and `blockedId`.
  - Build the `UserBlockJpaEntity` using the composite ID and `createdAt`.

- [ ] `private UserBlock toBlockDomain(UserBlockJpaEntity entity)`:
  - Use `UserBlock.builder()` and populate `blockerId` (from `entity.getId().getBlockerId()`), `blockedId` (from `entity.getId().getBlockedId()`), and `createdAt`.

---

### `AuditLogPersistenceAdapter.java`

#### Setup

- [ ] Annotate with `@Component`.
- [ ] Inject `AuditLogJpaRepository` via constructor.
- [ ] Inject `org.slf4j.Logger` (static field, not injected) for error logging.

#### Methods

- [ ] Implement `log(UUID actorId, String action, String entityType, UUID entityId, String metadata, String ipAddress)`:
  - Wrap the entire method body in a `try-catch (Exception e)`.
  - Inside the try block: build an `AuditLogJpaEntity` using the Lombok builder — set `userId = actorId`, `action`, `entityType`, `entityId`, `metadata`, `ipAddress`, and `createdAt = OffsetDateTime.now()`.
  - Call `auditLogJpaRepository.save(entity)`.
  - In the catch block: log the exception using SLF4J at `WARN` level with the message `"Failed to write audit log for action: {}"` and the action string. Do not rethrow — audit log failures must never disrupt the main business flow.

---

## Notes

- `ModerationPersistenceAdapter` is a single adapter handling both report and block persistence. This is acceptable because both domains share a single out-port interface (`ModerationRepository`). If the interface is split in a future refactor, the adapter can be split accordingly.
- The `deleteBlock` method must ensure a `@Transactional` context exists. The safest approach is to annotate the adapter's `deleteBlock` implementation with `@Transactional`. Alternatively, `ModerationService.unblockUser()` is already annotated `@Transactional`, which propagates to the adapter. Document whichever approach you choose.
- `AuditLogPersistenceAdapter.log()` must never throw exceptions. Use the try-catch pattern described above. This is a strict requirement — the audit log is a secondary concern and must not cause a transaction rollback in the primary business service.
- When mapping `ReportStatus` and `ReportEntityType` to their JPA enum values, confirm that the converter (if any) handles both directions correctly: `PENDING` ↔ `pending`, `USER` ↔ `user`, etc. A misconfigured converter will cause `IllegalArgumentException` at runtime.

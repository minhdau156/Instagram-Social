# TASK-9.6 — JPA Entities & Repositories

## Overview

Create all JPA persistence artefacts for the moderation feature. This includes three JPA entity classes and three Spring Data JPA repository interfaces. The `reports` and `user_blocks` tables already exist in the database (defined in the Flyway initial migration). The `audit_logs` table is also pre-existing.

Do not create the actual domain entities — those were created in TASK-9.1. This task is strictly about the persistence layer.

---

## Requirements

- Check `docs/database/schema.sql` for exact column names, types, and constraints before writing any code.
- Lombok is allowed: `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.
- `@Table(name = ...)` must match the schema table name exactly.
- Never expose JPA entities outside the `persistence` package.
- `@Component`, `@Service`, `@Autowired` are NOT used here — repositories are Spring Data interfaces detected by component scanning automatically.

---

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/entity/ReportJpaEntity.java
backend/src/main/java/com/instagram/adapter/out/persistence/repository/ReportJpaRepository.java

backend/src/main/java/com/instagram/adapter/out/persistence/entity/UserBlockJpaEntity.java
backend/src/main/java/com/instagram/adapter/out/persistence/entity/UserBlockId.java
backend/src/main/java/com/instagram/adapter/out/persistence/repository/UserBlockJpaRepository.java

backend/src/main/java/com/instagram/adapter/out/persistence/entity/AuditLogJpaEntity.java
backend/src/main/java/com/instagram/adapter/out/persistence/repository/AuditLogJpaRepository.java
```

---

## Checklist

### `ReportJpaEntity.java`

- [ ] Annotate with `@Entity` and `@Table(name = "reports")`.
- [ ] Lombok: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`.
- [ ] Fields — verify every name against `docs/database/schema.sql`:
  - `@Id @GeneratedValue(strategy = GenerationType.UUID) UUID id`
  - `@Column(name = "reporter_id", nullable = false) UUID reporterId`
  - `@Enumerated(EnumType.STRING) @Column(name = "entity_type", nullable = false) ReportEntityType entityType` — use `EnumType.STRING` so the JPA value matches the PostgreSQL ENUM label. Verify that the Java enum values (`USER`, `POST`, `COMMENT`, `MESSAGE`) map correctly to the DB values (`user`, `post`, `comment`, `message`). If PostgreSQL stores lowercase and Java stores uppercase, you need a custom `AttributeConverter` that lowercases on write and uppercases on read, OR configure Hibernate's `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` to map directly to the PostgreSQL ENUM type. Use whichever approach is already established in the codebase — check how `NotificationJpaEntity` handles `notification_type`.
  - `@Column(name = "entity_id", nullable = false) UUID entityId`
  - `@Column(nullable = false, length = 255) String reason`
  - `@Column(columnDefinition = "TEXT") String details` — nullable.
  - `@Enumerated(EnumType.STRING) @Column(nullable = false) ReportStatus status` — same enum mapping concern as `entityType` above.
  - `@Column(name = "reviewed_by_id") UUID reviewedById` — nullable; stored as raw UUID, no `@ManyToOne` join.
  - `@Column(name = "reviewed_at") OffsetDateTime reviewedAt` — nullable.
  - `@Column(name = "created_at", nullable = false, updatable = false) OffsetDateTime createdAt`
- [ ] `@PrePersist` method: if `createdAt` is null, set it to `OffsetDateTime.now()`.
- [ ] `@PrePersist` method: if `status` is null, set it to `ReportStatus.PENDING`.
- [ ] Do NOT add `@ManyToOne` joins to `UserJpaEntity` for `reporterId` or `reviewedById` — use raw `UUID` fields to avoid fetching user data unintentionally on every report load.

---

### `ReportJpaRepository.java`

- [ ] Extends `JpaRepository<ReportJpaEntity, UUID>`.
- [ ] `Page<ReportJpaEntity> findByStatusOrderByCreatedAtAsc(ReportStatus status, Pageable pageable)` — for the admin queue (oldest pending first).
- [ ] `Page<ReportJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable)` — for the admin dashboard showing all reports.
- [ ] `Page<ReportJpaEntity> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable)` — for filtered views in the admin panel.
- [ ] `long countByStatus(ReportStatus status)` — for the dashboard statistics cards.
- [ ] `boolean existsByReporterIdAndEntityId(UUID reporterId, UUID entityId)` — for duplicate-report prevention in the service layer.

---

### `UserBlockId.java`

- [ ] A separate `@Embeddable` class representing the composite primary key of `user_blocks`.
- [ ] Fields: `UUID blockerId` (maps to `blocker_id`) and `UUID blockedId` (maps to `blocked_id`).
- [ ] Must implement `Serializable` — JPA requires embedded IDs to be serializable.
- [ ] Must override `equals(Object)` and `hashCode()` correctly — both fields must participate in both methods. An incorrect `equals`/`hashCode` will cause silent duplicate-key issues with JPA's first-level cache.
- [ ] Use Lombok `@EqualsAndHashCode` and `@NoArgsConstructor @AllArgsConstructor` — Lombok handles `equals`/`hashCode` correctly for this case.

---

### `UserBlockJpaEntity.java`

- [ ] Annotate with `@Entity` and `@Table(name = "user_blocks")`.
- [ ] Lombok: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`.
- [ ] `@EmbeddedId UserBlockId id` — composite PK using the `UserBlockId` class above.
- [ ] Do NOT repeat `blockerId` and `blockedId` as separate fields — they live inside the embedded ID. Access them as `id.getBlockerId()` and `id.getBlockedId()`.
- [ ] `@Column(name = "created_at", nullable = false, updatable = false) OffsetDateTime createdAt`
- [ ] `@PrePersist` method: if `createdAt` is null, set it to `OffsetDateTime.now()`.

---

### `UserBlockJpaRepository.java`

- [ ] Extends `JpaRepository<UserBlockJpaEntity, UserBlockId>`.
- [ ] `boolean existsById(UserBlockId id)` — Spring Data derives this from the JPA repository automatically; confirm it works with the composite key.
- [ ] `@Query("SELECT CASE WHEN COUNT(b) > 0 THEN TRUE ELSE FALSE END FROM UserBlockJpaEntity b WHERE b.id.blockerId = :blockerId AND b.id.blockedId = :blockedId") boolean existsByBlockerIdAndBlockedId(@Param("blockerId") UUID blockerId, @Param("blockedId") UUID blockedId)` — named for readability; avoids constructing a `UserBlockId` object at the call site.
- [ ] `@Modifying @Transactional @Query("DELETE FROM UserBlockJpaEntity b WHERE b.id.blockerId = :blockerId AND b.id.blockedId = :blockedId") void deleteByBlockerIdAndBlockedId(@Param("blockerId") UUID blockerId, @Param("blockedId") UUID blockedId)` — bulk delete without loading the entity.
- [ ] `Page<UserBlockJpaEntity> findByIdBlockerIdOrderByCreatedAtDesc(UUID blockerId, Pageable pageable)` — finds all blocks for a given blocker.
- [ ] `@Query("SELECT b.id.blockedId FROM UserBlockJpaEntity b WHERE b.id.blockerId = :blockerId") List<UUID> findBlockedIdsByBlockerId(@Param("blockerId") UUID blockerId)` — returns only the IDs for feed/search filtering.
- [ ] `@Query("SELECT b.id.blockerId FROM UserBlockJpaEntity b WHERE b.id.blockedId = :blockedId") List<UUID> findBlockerIdsByBlockedId(@Param("blockedId") UUID blockedId)` — returns users who blocked the given user.

---

### `AuditLogJpaEntity.java`

- [ ] Annotate with `@Entity` and `@Table(name = "audit_logs")`.
- [ ] Lombok: `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`.
- [ ] Fields — match the schema exactly:
  - `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id` — the schema uses `BIGSERIAL`, so use `GenerationType.IDENTITY` (not `UUID`).
  - `@Column(name = "user_id") UUID userId` — nullable; maps to `user_id UUID REFERENCES users(id)`.
  - `@Column(nullable = false, length = 100) String action`
  - `@Column(name = "entity_type", length = 50) String entityType` — nullable.
  - `@Column(name = "entity_id") UUID entityId` — nullable.
  - `@Column(columnDefinition = "JSONB") String metadata` — stored as a plain JSON string; nullable.
  - `@Column(name = "ip_address", length = 45) String ipAddress` — stored as a String; the schema column is `INET` but mapping it via a custom converter or as `String` is simpler. Use `String` and let the JDBC driver handle the INET conversion, or add a Hibernate type annotation if the driver complains.
  - `@Column(name = "created_at", nullable = false, updatable = false) OffsetDateTime createdAt`
- [ ] `@PrePersist`: set `createdAt = OffsetDateTime.now()` if null.
- [ ] No business methods needed. Audit log entities are write-once.

---

### `AuditLogJpaRepository.java`

- [ ] Extends `JpaRepository<AuditLogJpaEntity, Long>`.
- [ ] `Page<AuditLogJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable)` — for viewing a user's audit history.
- [ ] `Page<AuditLogJpaEntity> findByActionOrderByCreatedAtDesc(String action, Pageable pageable)` — for filtering by action type.
- [ ] No other custom query methods are needed for Phase 9.

---

## Notes

- The PostgreSQL ENUM mapping (`report_entity_type`, `report_status`) may require a custom `AttributeConverter` or Hibernate `@JdbcTypeCode` annotation depending on how the existing entities handle similar ENUMs. Open `NotificationJpaEntity.java` and see how `notification_type` is mapped — replicate that exact approach for `report_entity_type` and `report_status` to stay consistent.
- `UserBlockId` must be `Serializable` — JPA requires this for embedded IDs. Forgetting this causes a `HibernateException` at startup.
- For `AuditLogJpaEntity`, the `ip_address INET` column in PostgreSQL is a special type. If using plain JDBC (via Spring Boot's default), storing it as `String` works as long as you pass a valid IPv4 or IPv6 string. The JDBC driver performs implicit casting. If it does not, add a `@Type(type = "com.vladmihalcea.hibernate.type.basic.PostgreSQLInetType")` annotation from the `hibernate-types` library — but first check whether that library is already in `pom.xml`.
- `@Modifying @Transactional` on `deleteByBlockerIdAndBlockedId` must be called within a transaction. Ensure the calling adapter method or service method is annotated `@Transactional`.

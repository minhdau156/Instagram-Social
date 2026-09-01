# Implementation Plan: TASK-10.53 — Password Reset via Email

## Overview

Complete the password-reset flow that Phase 1 (TASK-1.12) deliberately stubbed out. The domain ports, controller, DTOs, exception type, and DB table already exist and are wired end-to-end — but `UserService.requestPasswordReset()` never persists a token (uses `UUID.randomUUID()`, doesn't save it) and `confirmPasswordReset()` unconditionally throws (no persistence lookup at all). `SmtpEmailAdapter` only logs the link, it never sends real mail. This plan closes those three gaps: real token persistence, real token validation, and real SMTP delivery — without touching the parts that are already correct.

**The task doc (`docs/tasks/phase-10/TASK-10.53-password-reset-email.md`) assumes a file layout and a design that don't match this repo.** See Architecture Decisions below for every place this plan deviates from it, and why.

## Current State (verified against the repo, not the task doc)

| Piece | Status | Location |
|---|---|---|
| `RequestPasswordResetUseCase`, `ConfirmPasswordResetUseCase` | ✅ exist | `domain/port/in/` |
| `EmailPort` | ✅ exists | `domain/port/out/EmailPort.java` |
| `SmtpEmailAdapter` | ⚠️ stub — logs only, no `JavaMailSender` | `adapter/out/email/SmtpEmailAdapter.java` |
| `PasswordResetTokenExpiredException` | ✅ exists, mapped to 400 in `GlobalExceptionHandler` | `domain/exception/` |
| `password_reset_tokens` table | ✅ already migrated (V1, not a new migration) | `db/migration/V1__initial_schema.sql:83` |
| `POST /api/v1/auth/password-reset/request` + `/confirm` | ✅ exist, already `permitAll()` via the `/api/v1/auth/**` wildcard | `adapter/in/web/AuthController.java` |
| `PasswordResetRequest`, `PasswordResetConfirmRequest` DTOs | ✅ exist | `adapter/in/web/dto/request/` |
| `UserService.requestPasswordReset()` | ❌ stub — `UUID.randomUUID()` token, never persisted, real email never sent | `application/service/UserService.java:172-188` |
| `UserService.confirmPasswordReset()` | ❌ stub — always throws `PasswordResetTokenExpiredException` | `application/service/UserService.java:193-199` |
| `PasswordResetTokenRepository` out-port + adapter | ❌ does not exist yet | — |
| `spring-boot-starter-mail` / `spring.mail` config | ❌ not present anywhere | `pom.xml`, `application.yml` |
| Tests for either endpoint/service method | ❌ none (mocks declared in `AuthControllerIT` but unused) | `UserServiceTest.java`, `AuthControllerIT.java` |

## Architecture Decisions

- **No new Flyway migration.** The task doc's `V9__password_reset_tokens.sql` is redundant — the table already exists (V1) with columns `id UUID`, `user_id UUID`, `token_hash VARCHAR(255) UNIQUE`, `expires_at TIMESTAMPTZ`, `used_at TIMESTAMPTZ`, `created_at TIMESTAMPTZ`.
- **The schema already commits to hashing the token** (`token_hash`, not `token`). The task doc treats hashing as an optional TODO for "production, not this project level" — but the existing table already made that call, so the service must generate a `SecureRandom` token, send the raw token in the email, and store only its SHA-256 hash. This is stricter than the doc, and it's free (no schema change).
- **`used` is `used_at TIMESTAMPTZ` (nullable), not a boolean.** A token is used when `usedAt != null`. Same semantics as the doc's `used` flag, different column.
- **Single exception type for all invalid-token cases (not found, already used, expired).** The doc wants `InvalidPasswordResetTokenException` + `ExpiredPasswordResetTokenException`. The existing stub already collapses "not found" into `PasswordResetTokenExpiredException`, and `GlobalExceptionHandler` only wires that one. Keeping one generic exception avoids leaking *why* a token failed (not-found vs. used vs. expired is exactly the kind of detail the enumeration-protection principle in this same doc says not to leak) and it means zero new exception classes. Reusing it is the surgical choice.
- **File layout follows this repo's actual hexagonal packages, not the doc's:**
  - Ports: `domain/port/out/PasswordResetTokenRepository.java` (flat, like `UserRepository`, `EmailPort` — not nested under `auth/`)
  - Model: `domain/model/PasswordResetToken.java`
  - Persistence: `adapter/out/persistence/entity/PasswordResetTokenJpaEntity.java`, `adapter/out/persistence/repository/PasswordResetTokenJpaRepository.java`, `adapter/out/persistence/PasswordResetTokenPersistenceAdapter.java` — matching `UserPersistenceAdapter`'s sibling layout exactly (adapter sits directly in `persistence/`, not a nested `adapter/` subfolder)
  - No `web/controller`, `web/dto`, `infrastructure/persistence`, `infrastructure/notification` packages exist in this repo — those are the doc's invented layout, not this codebase's.
- **Endpoint paths stay as they already are** (`/api/v1/auth/password-reset/request`, `/api/v1/auth/password-reset/confirm`), not the doc's `/forgot-password` / `/reset-password`. Changing a shipped path is out of scope and not something this task needs.
- **No `SecurityConfig` change.** `/api/v1/auth/**` already `permitAll()`s both endpoints.
- **Mail config lives directly in `application.yml`** with env-var defaults pointing at `localhost:1025` (Mailpit), matching how `frontend-url` and `minio.*` are already configured in this file — not duplicated into `application-local.yml`, which in this repo only carries genuine per-profile overrides (DB host, CORS origin), never a copy of the parent defaults.
- **`app.frontend-url` is reused as the link base** (already wired into `SmtpEmailAdapter`) instead of introducing the doc's separate `app.base-url`.
- **Test injection for the new `tokenExpiryMinutes` field:** `UserServiceTest` uses `@InjectMocks`, which won't populate a `@Value`-injected primitive. Tests that depend on expiry set it explicitly via `ReflectionTestUtils.setField(userService, "tokenExpiryMinutes", 30)`.

## Task List

### Phase 1: Foundation (independent, parallelizable)
- [x] Task 1: Password-reset-token persistence (domain model + out-port + JPA entity/repo/adapter)
- [x] Task 2: Real SMTP delivery (dependency, config, adapter rewrite)

### Checkpoint: Foundation
- [x] Backend compiles (`mvn compile`)
- [x] No behavior change yet — `requestPasswordReset`/`confirmPasswordReset` still stubbed

### Phase 2: Wire the real flow
- [x] Task 3: Replace stubs in `UserService` with real token generation, persistence, validation, and password update

### Checkpoint: Core flow
- [ ] Manual Mailpit verification (see How to Verify)
- [x] Replay of a used token returns 400 (covered by `confirmPasswordReset_usedToken_throws`)

### Phase 3: Tests
- [x] Task 4: Unit tests (`UserServiceTest`) + controller tests (`AuthControllerIT`)

### Final Checkpoint
- [x] `mvn test` passes (backend)
- [ ] Manual end-to-end verified against Mailpit
- [x] Ready for review / commit

## Task Detail

### Task 1 — Password-reset-token persistence
**Description:** Add the out-port and JPA adapter for `password_reset_tokens`, following the existing `UserRepository`/`UserPersistenceAdapter` pattern exactly (flat out-port, UUID keys, `OffsetDateTime` timestamps to match `User`'s convention).

**`PasswordResetToken` (domain/model, record):**
```java
public record PasswordResetToken(
        UUID id, UUID userId, String tokenHash,
        OffsetDateTime expiresAt, OffsetDateTime usedAt, OffsetDateTime createdAt) {
    public boolean isUsed() { return usedAt != null; }
    public boolean isExpired() { return OffsetDateTime.now().isAfter(expiresAt); }
    public boolean isValid() { return !isUsed() && !isExpired(); }
}
```

**`PasswordResetTokenRepository` (domain/port/out, flat — no `auth` subpackage):**
```java
public interface PasswordResetTokenRepository {
    PasswordResetToken save(PasswordResetToken token);
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    void markUsed(UUID tokenId);
}
```

**JPA entity/repo/adapter** mirror `UserJpaEntity`/`UserJpaRepository`/`UserPersistenceAdapter` (UUID `@Id`, `@GeneratedValue` via DB default `uuid_generate_v4()` — don't set `id` on insert, let Postgres generate it, matching how `users` rows are created elsewhere in this codebase).

**Acceptance criteria:**
- [ ] `PasswordResetToken` domain record with `isUsed()`, `isExpired()`, `isValid()`
- [ ] `PasswordResetTokenRepository` out-port: `save`, `findByTokenHash`, `markUsed`
- [ ] JPA entity maps to existing `password_reset_tokens` columns exactly (no schema change)
- [ ] Adapter compiles and is a `@Component`

**Verification:**
- [ ] `mvn compile` succeeds
- [ ] Manual check: entity field names match `V1__initial_schema.sql:83-90` column-for-column

**Dependencies:** None

**Files:**
- `backend/src/main/java/com/instagram/domain/model/PasswordResetToken.java` (new)
- `backend/src/main/java/com/instagram/domain/port/out/PasswordResetTokenRepository.java` (new)
- `backend/src/main/java/com/instagram/adapter/out/persistence/entity/PasswordResetTokenJpaEntity.java` (new)
- `backend/src/main/java/com/instagram/adapter/out/persistence/repository/PasswordResetTokenJpaRepository.java` (new)
- `backend/src/main/java/com/instagram/adapter/out/persistence/PasswordResetTokenPersistenceAdapter.java` (new)

**Estimated scope:** M (5 files, all new, no existing code touched)

---

### Task 2 — Real SMTP delivery
**Description:** Add `spring-boot-starter-mail`, configure `spring.mail` against Mailpit locally / env vars in prod, and rewrite `SmtpEmailAdapter` to actually send via `JavaMailSender` instead of only logging.

**Acceptance criteria:**
- [ ] `spring-boot-starter-mail` added to `backend/pom.xml` (no version — Spring Boot BOM-managed)
- [ ] `spring.mail.*` added to `application.yml` under the existing `spring:` key, with `SMTP_HOST`/`SMTP_PORT`/`SMTP_USER`/`SMTP_PASS`/`SMTP_AUTH`/`SMTP_STARTTLS` env vars defaulting to Mailpit (`localhost:1025`, auth off)
- [ ] `app.password-reset.token-expiry-minutes: 30` added under the existing `app:` block
- [ ] `.env.example` gains the SMTP vars (matching the defaults already in `application.yml`, so an empty `.env` still works against local Mailpit)
- [ ] `SmtpEmailAdapter` injects `JavaMailSender`, builds the same reset link it builds today, sends a plain-text `SimpleMailMessage`, and keeps a debug log line (not the `[EMAIL STUB]` one — that was Phase 1's marker for "not real yet")

**Verification:**
- [ ] `mvn compile` succeeds
- [ ] Manual check: with Mailpit running on `1025`, a real message appears in its UI (covered by the Task 3 checkpoint, since sending only fires from a real `requestPasswordReset` call)

**Dependencies:** None (independent of Task 1 — can be done in parallel)

**Files:**
- `backend/pom.xml` (modify)
- `backend/src/main/resources/application.yml` (modify)
- `.env.example` (modify)
- `backend/src/main/java/com/instagram/adapter/out/email/SmtpEmailAdapter.java` (modify)

**Estimated scope:** S (4 files, mostly config)

---

### Task 3 — Wire real logic into `UserService`
**Description:** Replace both stub method bodies. Inject `PasswordResetTokenRepository` and the `token-expiry-minutes` property into the constructor.

`requestPasswordReset`:
1. Look up user by email; if absent, return silently (already correct today — keep as-is).
2. Generate a `SecureRandom` token (32 bytes, `Base64.getUrlEncoder().withoutPadding()`), SHA-256-hash it for storage.
3. Persist a new `PasswordResetToken` (`expiresAt = now + tokenExpiryMinutes`).
4. Call `emailPort.sendPasswordResetEmail(email, rawToken)` — same call site as today, just with a real, persisted token.

`confirmPasswordReset`:
1. Hash the presented token, `findByTokenHash`; if absent → throw `PasswordResetTokenExpiredException`.
2. If `!isValid()` (used or expired) → throw `PasswordResetTokenExpiredException`.
3. Load the user by `userId`, hash the new password via `passwordHashPort.hash(...)`, save via `userRepository.save(user.withUpdatedPasswordHash(...))` (existing helper, `User.java:249`).
4. `passwordResetTokenRepository.markUsed(tokenId)`.

Remove the commented-out "replace this block once TASK-1.12 is complete" scaffold — it's now live code, not a TODO.

**Acceptance criteria:**
- [ ] `requestPasswordReset` persists a hashed token and sends a real email for a known address; does nothing for an unknown one
- [ ] `confirmPasswordReset` updates the password hash and marks the token used on a valid token
- [ ] An unknown, already-used, or expired token all throw `PasswordResetTokenExpiredException` (400)
- [ ] A replayed (already-used) token is rejected on the second call

**Verification:**
- [ ] `mvn compile` succeeds
- [ ] Manual: Mailpit steps below (How to Verify)

**Dependencies:** Task 1 (needs the repository), Task 2 (needs real sending to observe the email — but the method itself compiles and is testable without it)

**Files:**
- `backend/src/main/java/com/instagram/application/service/UserService.java` (modify)

**Estimated scope:** S (1 file, two methods + constructor)

---

### Task 4 — Tests
**Description:** Add the missing coverage. `AuthControllerIT` already declares `@MockBean` fields for both use cases — only `@Test` methods are missing.

**`UserServiceTest` additions:**
| Test | Expected |
|---|---|
| `requestPasswordReset_knownEmail_savesTokenAndSendsEmail` | `tokenRepository.save()` and `emailPort.sendPasswordResetEmail()` both called once |
| `requestPasswordReset_unknownEmail_doesNothing` | No calls to `tokenRepository` or `emailPort` |
| `confirmPasswordReset_validToken_updatesPasswordAndMarksUsed` | `userRepository.save()` called with updated hash; `tokenRepository.markUsed()` called |
| `confirmPasswordReset_unknownToken_throws` | `PasswordResetTokenExpiredException` |
| `confirmPasswordReset_usedToken_throws` | `PasswordResetTokenExpiredException` |
| `confirmPasswordReset_expiredToken_throws` | `PasswordResetTokenExpiredException` |

**`AuthControllerIT` additions:**
| Test | Expected |
|---|---|
| `requestPasswordReset_validEmail_returns200` | HTTP 200; use case called once |
| `requestPasswordReset_invalidEmail_returns400` | HTTP 400 (`@Email` validation) |
| `confirmPasswordReset_validRequest_returns200` | HTTP 200; use case called once |
| `confirmPasswordReset_blankToken_returns400` | HTTP 400 |
| `confirmPasswordReset_shortPassword_returns400` | HTTP 400 |
| `confirmPasswordReset_expiredOrInvalidToken_returns400` | service throws `PasswordResetTokenExpiredException` → HTTP 400 |

**Acceptance criteria:**
- [x] All tests above pass
- [x] No existing test regresses (new constructor param doesn't break `@InjectMocks` — Mockito matches by type)

**Verification:**
- [x] `mvn test` (backend, focused: `-Dtest=UserServiceTest,AuthControllerIT`)

**Dependencies:** Task 3

**Files:**
- `backend/src/test/java/com/instagram/application/service/UserServiceTest.java` (modify)
- `backend/src/test/java/com/instagram/adapter/in/web/AuthControllerIT.java` (modify)

**Estimated scope:** S (2 files, additive only)

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| `@InjectMocks` won't populate the new `@Value tokenExpiryMinutes` field | Medium — expiry-dependent tests would see `0` | Set explicitly via `ReflectionTestUtils.setField` in the relevant tests (Task 4) |
| Doc's file paths/exception design get followed by habit instead of the repo's real conventions | High if unchecked | This plan documents every deviation above; Task 1/2/3 file lists are the actual paths to create |
| Mailpit not running locally when manually verifying | Low | Same as the doc's own troubleshooting note — start it first (`docker run -d -p 1025:1025 -p 8025:8025 axllent/mailpit`) |

## Open Questions

None — all ambiguities were resolved by reading the existing code (see Architecture Decisions).

## How to Verify (manual, after Task 3)

1. Start Mailpit: `docker run -d -p 1025:1025 -p 8025:8025 axllent/mailpit` (UI at `http://localhost:8025`)
2. Start backend: `cd backend && mvn spring-boot:run` (local profile)
3. `POST /api/v1/auth/password-reset/request` with a registered email → 200, email appears in Mailpit
4. Same request with an unregistered email → 200, no email sent (no enumeration)
5. `POST /api/v1/auth/password-reset/confirm` with the token from the email + a new password → 200
6. Replay the same token → 400

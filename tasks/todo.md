# TODO: TASK-10.53 — Password Reset via Email

See `tasks/plan.md` for full detail, especially **Architecture Decisions** — the task doc's file paths/exceptions don't match this repo; the paths below are the real ones.

## Phase 1: Foundation (Task 1 and Task 2 are independent — can be done in either order or parallel)

- [ ] Task 1: Password-reset-token persistence
  - Acceptance: `PasswordResetToken` record (`isUsed`/`isExpired`/`isValid`); `PasswordResetTokenRepository` out-port (`save`/`findByTokenHash`/`markUsed`); JPA entity/repo/adapter mapping to the *existing* `password_reset_tokens` table (no new migration)
  - Files: `domain/model/PasswordResetToken.java`, `domain/port/out/PasswordResetTokenRepository.java`, `adapter/out/persistence/entity/PasswordResetTokenJpaEntity.java`, `adapter/out/persistence/repository/PasswordResetTokenJpaRepository.java`, `adapter/out/persistence/PasswordResetTokenPersistenceAdapter.java`

- [ ] Task 2: Real SMTP delivery
  - Acceptance: `spring-boot-starter-mail` added; `spring.mail.*` + `app.password-reset.token-expiry-minutes` in `application.yml` (Mailpit defaults); SMTP vars in `.env.example`; `SmtpEmailAdapter` sends via `JavaMailSender` instead of only logging
  - Files: `backend/pom.xml`, `backend/src/main/resources/application.yml`, `.env.example`, `adapter/out/email/SmtpEmailAdapter.java`

## Checkpoint: Foundation
- [ ] `mvn compile` succeeds
- [ ] No behavior change yet (`UserService` stubs untouched)

## Phase 2: Wire the real flow

- [ ] Task 3: Replace stubs in `UserService`
  - Acceptance: `requestPasswordReset` persists a SHA-256-hashed `SecureRandom` token and sends a real email for known emails, no-ops for unknown ones; `confirmPasswordReset` validates (not-found/used/expired → `PasswordResetTokenExpiredException`), updates the password hash, marks the token used
  - Files: `application/service/UserService.java`
  - Dependencies: Task 1, Task 2

## Checkpoint: Core flow
- [ ] Manual Mailpit verification (steps in `tasks/plan.md` → How to Verify)
- [ ] Replaying a used token returns 400

## Phase 3: Tests

- [ ] Task 4: Unit + controller tests
  - Acceptance: 6 new `UserServiceTest` cases (known/unknown email; valid/unknown/used/expired token) + 6 new `AuthControllerIT` cases (200/400 paths for both endpoints) all pass; no existing test regresses
  - Files: `application/service/UserServiceTest.java`, `adapter/in/web/AuthControllerIT.java`
  - Dependencies: Task 3

## Final Checkpoint
- [ ] `mvn test` passes (backend)
- [ ] Manual end-to-end verified against Mailpit
- [ ] `git status` clean except the files listed above
- [ ] Ready for human review / commit

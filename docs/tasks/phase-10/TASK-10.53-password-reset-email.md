# TASK-10.53 — Password Reset via Email

## Overview

Add a self-service password reset flow to the backend. A user who has forgotten their password submits their email address; the backend generates a secure, time-limited token, persists it, and emails the user a reset link. The user clicks the link (or pastes the token into the frontend form), submits a new password, and the backend validates the token, updates the credential hash, and invalidates the token so it cannot be reused.

The flow has exactly two public HTTP endpoints:

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/auth/forgot-password` | Accepts an email; sends a reset link if the address is registered |
| `POST` | `/api/v1/auth/reset-password` | Accepts a token + new password; resets the credential |

Both endpoints respond with `200 OK` even when the email is not found — this is intentional user-enumeration protection.

---

## Level

Core · Pairs with [TASK-10.15 — Secrets hygiene](TASK-10.15-secrets-hygiene.md) · Relates to [TASK-10.16 — JWT hardening](TASK-10.16-jwt-hardening.md)

---

## Why

Forgotten-password recovery is a baseline trust feature. Without it, users whose credentials are lost or compromised have no self-service path and must contact support. The reset token approach avoids sending passwords in clear text over email: the email carries only a single-use, time-limited opaque token; the actual credential change happens over HTTPS on the backend. Leaking a token (e.g., via email forwarding) is limited in damage because the token expires and is invalidated after first use.

---

## Prerequisites

- The backend compiles and the local profile starts cleanly.
- [TASK-10.15](TASK-10.15-secrets-hygiene.md) is complete — `.env.example` exists and the secrets-loading pattern is established.
- You have access to an SMTP server for local testing. The easiest option is [Mailpit](https://mailpit.axllent.org/) (a local SMTP + web UI that catches outgoing email without delivering it).
- Familiarity with Spring Mail (`JavaMailSender`) and Flyway migrations.

**Concepts to skim:**

- **`SecureRandom` + `Base64`**: the standard Java way to generate a cryptographically secure opaque token. Never use `UUID.randomUUID()` for security tokens — its entropy is lower and the format is predictable.
- **Token expiry**: store a `expiresAt` timestamp in the database. On validation, reject any token where `expiresAt` is before `Instant.now()`.
- **Single-use tokens**: after a successful reset, delete (or mark `used = true`) the token row so it cannot be replayed.
- **User-enumeration protection**: always return `200 OK` from `POST /forgot-password`, even when the email is not registered. Never reveal whether an address exists in the system.
- **Spring Mail `JavaMailSender`**: the Spring abstraction over JavaMail. Configure SMTP host, port, and credentials in `application.yml`; inject `JavaMailSender` into your adapter.

---

## Files to Create / Modify

```
backend/pom.xml                                                                                  (modify — add spring-boot-starter-mail)
backend/src/main/resources/application.yml                                                       (modify — add spring.mail config)
backend/src/main/resources/application-local.yml                                                 (modify — point to Mailpit SMTP)
.env.example                                                                                     (modify — add SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, APP_BASE_URL)

backend/src/main/resources/db/migration/V9__password_reset_tokens.sql                           (new)

backend/src/main/java/com/instagram/domain/model/PasswordResetToken.java                        (new)
backend/src/main/java/com/instagram/domain/exception/InvalidPasswordResetTokenException.java    (new)
backend/src/main/java/com/instagram/domain/exception/ExpiredPasswordResetTokenException.java    (new)

backend/src/main/java/com/instagram/domain/port/in/auth/RequestPasswordResetUseCase.java        (new)
backend/src/main/java/com/instagram/domain/port/in/auth/ResetPasswordUseCase.java               (new)

backend/src/main/java/com/instagram/domain/port/out/auth/PasswordResetTokenRepository.java      (new)
backend/src/main/java/com/instagram/domain/port/out/notification/EmailPort.java                 (new)

backend/src/main/java/com/instagram/application/service/PasswordResetService.java               (new)

backend/src/main/java/com/instagram/infrastructure/persistence/entity/PasswordResetTokenJpaEntity.java   (new)
backend/src/main/java/com/instagram/infrastructure/persistence/repository/PasswordResetTokenJpaRepository.java (new)
backend/src/main/java/com/instagram/infrastructure/persistence/adapter/PasswordResetTokenPersistenceAdapter.java (new)

backend/src/main/java/com/instagram/infrastructure/notification/JavaMailEmailAdapter.java       (new)

backend/src/main/java/com/instagram/web/controller/PasswordResetController.java                 (new)
backend/src/main/java/com/instagram/web/dto/request/ForgotPasswordRequest.java                  (new)
backend/src/main/java/com/instagram/web/dto/request/ResetPasswordRequest.java                   (new)

backend/src/main/java/com/instagram/infrastructure/security/SecurityConfig.java                 (modify — permit the two new endpoints)

backend/src/test/java/com/instagram/application/service/PasswordResetServiceTest.java           (new)
backend/src/test/java/com/instagram/web/controller/PasswordResetControllerTest.java             (new)
```

---

## Step-by-Step

### 1. Add Spring Mail dependency to pom.xml

Open `backend/pom.xml` and add inside `<dependencies>`. No version needed — managed by the Spring Boot BOM:

```xml
<!-- Email sending via JavaMailSender -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

---

### 2. Add Flyway migration V9

Create `backend/src/main/resources/db/migration/V9__password_reset_tokens.sql`:

```sql
CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(128)             NOT NULL UNIQUE,
    user_id     BIGINT                   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    used        BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prt_token   ON password_reset_tokens(token);
CREATE INDEX idx_prt_user_id ON password_reset_tokens(user_id);
```

Token length of 128 characters accommodates a 64-byte `SecureRandom` value encoded as Base64 URL-safe string (86 chars) with room to spare.

---

### 3. Configure Spring Mail in application.yml

Add a `spring.mail` block to `backend/src/main/resources/application.yml`. Place it under the `spring:` key alongside `spring.datasource` and `spring.jpa`:

```yaml
spring:
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:1025}
    username: ${SMTP_USER:}
    password: ${SMTP_PASS:}
    properties:
      mail:
        smtp:
          auth: ${SMTP_AUTH:false}
          starttls:
            enable: ${SMTP_STARTTLS:false}

app:
  base-url: ${APP_BASE_URL:http://localhost:5173}
  password-reset:
    token-expiry-minutes: 30
```

Add to `backend/src/main/resources/application-local.yml` (Mailpit defaults):

```yaml
spring:
  mail:
    host: localhost
    port: 1025
```

Add to `.env.example`:

```dotenv
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USER=no-reply@example.com
SMTP_PASS=changeme
SMTP_AUTH=true
SMTP_STARTTLS=true
APP_BASE_URL=https://yourdomain.com
```

---

### 4. Domain model — PasswordResetToken

Create `backend/src/main/java/com/instagram/domain/model/PasswordResetToken.java`:

```java
package com.instagram.domain.model;

import java.time.Instant;

public record PasswordResetToken(
        Long id,
        String token,
        Long userId,
        Instant expiresAt,
        boolean used,
        Instant createdAt
) {
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !used && !isExpired();
    }
}
```

---

### 5. Domain exceptions

Create `InvalidPasswordResetTokenException.java`:

```java
package com.instagram.domain.exception;

public class InvalidPasswordResetTokenException extends RuntimeException {
    public InvalidPasswordResetTokenException() {
        super("Password reset token is invalid or has already been used");
    }
}
```

Create `ExpiredPasswordResetTokenException.java`:

```java
package com.instagram.domain.exception;

public class ExpiredPasswordResetTokenException extends RuntimeException {
    public ExpiredPasswordResetTokenException() {
        super("Password reset token has expired");
    }
}
```

Register both in `GlobalExceptionHandler` mapping to `400 Bad Request`:

```java
@ExceptionHandler({InvalidPasswordResetTokenException.class, ExpiredPasswordResetTokenException.class})
@ResponseStatus(HttpStatus.BAD_REQUEST)
public ApiResponse<Void> handlePasswordResetTokenException(RuntimeException ex) {
    return ApiResponse.error(ex.getMessage());
}
```

---

### 6. In-ports (use-case interfaces)

Create `RequestPasswordResetUseCase.java`:

```java
package com.instagram.domain.port.in.auth;

public interface RequestPasswordResetUseCase {
    /**
     * Initiates a password reset for the given email.
     * Always completes silently — even if the email is not registered —
     * to prevent user enumeration.
     */
    void requestReset(String email);
}
```

Create `ResetPasswordUseCase.java`:

```java
package com.instagram.domain.port.in.auth;

public interface ResetPasswordUseCase {
    /**
     * Validates the token and replaces the user's password hash.
     * Throws InvalidPasswordResetTokenException or ExpiredPasswordResetTokenException on failure.
     */
    void resetPassword(String token, String newPassword);
}
```

---

### 7. Out-ports

Create `PasswordResetTokenRepository.java`:

```java
package com.instagram.domain.port.out.auth;

import com.instagram.domain.model.PasswordResetToken;
import java.util.Optional;

public interface PasswordResetTokenRepository {
    PasswordResetToken save(PasswordResetToken token);
    Optional<PasswordResetToken> findByToken(String token);
    void markUsed(Long tokenId);
    void deleteExpiredTokensForUser(Long userId);
}
```

Create `EmailPort.java`:

```java
package com.instagram.domain.port.out.notification;

public interface EmailPort {
    void sendPasswordResetEmail(String toEmail, String resetLink);
}
```

---

### 8. Application service — PasswordResetService

Create `backend/src/main/java/com/instagram/application/service/PasswordResetService.java`:

```java
package com.instagram.application.service;

import com.instagram.domain.exception.ExpiredPasswordResetTokenException;
import com.instagram.domain.exception.InvalidPasswordResetTokenException;
import com.instagram.domain.model.PasswordResetToken;
import com.instagram.domain.port.in.auth.RequestPasswordResetUseCase;
import com.instagram.domain.port.in.auth.ResetPasswordUseCase;
import com.instagram.domain.port.out.auth.PasswordResetTokenRepository;
import com.instagram.domain.port.out.notification.EmailPort;
import com.instagram.domain.port.out.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService implements RequestPasswordResetUseCase, ResetPasswordUseCase {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 64;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailPort emailPort;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.password-reset.token-expiry-minutes:30}")
    private int tokenExpiryMinutes;

    @Override
    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            tokenRepository.deleteExpiredTokensForUser(user.getId());

            byte[] bytes = new byte[TOKEN_BYTES];
            SECURE_RANDOM.nextBytes(bytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            PasswordResetToken token = new PasswordResetToken(
                    null,
                    rawToken,
                    user.getId(),
                    Instant.now().plus(tokenExpiryMinutes, ChronoUnit.MINUTES),
                    false,
                    Instant.now()
            );
            tokenRepository.save(token);

            String resetLink = baseUrl + "/reset-password?token=" + rawToken;
            emailPort.sendPasswordResetEmail(user.getEmail(), resetLink);
            log.info("Password reset email sent userId={}", user.getId());
        });
        // No-op when email not found — intentional user-enumeration protection
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(InvalidPasswordResetTokenException::new);

        if (resetToken.used()) {
            throw new InvalidPasswordResetTokenException();
        }
        if (resetToken.isExpired()) {
            throw new ExpiredPasswordResetTokenException();
        }

        String newHash = passwordEncoder.encode(newPassword);
        userRepository.updatePasswordHash(resetToken.userId(), newHash);
        tokenRepository.markUsed(resetToken.id());
        log.info("Password reset completed userId={}", resetToken.userId());
    }
}
```

> **Note:** `UserRepository` must expose `findByEmail(String email)` and `updatePasswordHash(Long userId, String newHash)`. Add these methods if they are missing.

---

### 9. JPA entity and repository

Create `PasswordResetTokenJpaEntity.java`:

```java
package com.instagram.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PasswordResetTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String token;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

Create `PasswordResetTokenJpaRepository.java`:

```java
package com.instagram.infrastructure.persistence.repository;

import com.instagram.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenJpaRepository
        extends JpaRepository<PasswordResetTokenJpaEntity, Long> {

    Optional<PasswordResetTokenJpaEntity> findByToken(String token);

    @Modifying
    @Query("DELETE FROM PasswordResetTokenJpaEntity t WHERE t.userId = :userId AND t.expiresAt < :now")
    void deleteExpiredByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
```

Create `PasswordResetTokenPersistenceAdapter.java`:

```java
package com.instagram.infrastructure.persistence.adapter;

import com.instagram.domain.model.PasswordResetToken;
import com.instagram.domain.port.out.auth.PasswordResetTokenRepository;
import com.instagram.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import com.instagram.infrastructure.persistence.repository.PasswordResetTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PasswordResetTokenPersistenceAdapter implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository jpaRepository;

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        PasswordResetTokenJpaEntity entity = PasswordResetTokenJpaEntity.builder()
                .token(token.token())
                .userId(token.userId())
                .expiresAt(token.expiresAt())
                .used(token.used())
                .createdAt(token.createdAt() != null ? token.createdAt() : Instant.now())
                .build();
        PasswordResetTokenJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<PasswordResetToken> findByToken(String token) {
        return jpaRepository.findByToken(token).map(this::toDomain);
    }

    @Override
    public void markUsed(Long tokenId) {
        jpaRepository.findById(tokenId).ifPresent(entity -> {
            entity.setUsed(true);
            jpaRepository.save(entity);
        });
    }

    @Override
    public void deleteExpiredTokensForUser(Long userId) {
        jpaRepository.deleteExpiredByUserId(userId, Instant.now());
    }

    private PasswordResetToken toDomain(PasswordResetTokenJpaEntity e) {
        return new PasswordResetToken(e.getId(), e.getToken(), e.getUserId(),
                e.getExpiresAt(), e.isUsed(), e.getCreatedAt());
    }
}
```

---

### 10. Email adapter — JavaMailEmailAdapter

Create `backend/src/main/java/com/instagram/infrastructure/notification/JavaMailEmailAdapter.java`:

```java
package com.instagram.infrastructure.notification;

import com.instagram.domain.port.out.notification.EmailPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JavaMailEmailAdapter implements EmailPort {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:no-reply@localhost}")
    private String fromAddress;

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Reset your password");
        message.setText(
                "You requested a password reset.\n\n" +
                "Click the link below to set a new password (valid for 30 minutes):\n\n" +
                resetLink + "\n\n" +
                "If you did not request this, you can safely ignore this email."
        );
        mailSender.send(message);
        log.debug("Password reset email dispatched to={}", toEmail);
    }
}
```

---

### 11. REST controller

Create `backend/src/main/java/com/instagram/web/controller/PasswordResetController.java`:

```java
package com.instagram.web.controller;

import com.instagram.domain.port.in.auth.RequestPasswordResetUseCase;
import com.instagram.domain.port.in.auth.ResetPasswordUseCase;
import com.instagram.web.dto.request.ForgotPasswordRequest;
import com.instagram.web.dto.request.ResetPasswordRequest;
import com.instagram.web.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        requestPasswordResetUseCase.requestReset(request.email());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        resetPasswordUseCase.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

Create `ForgotPasswordRequest.java`:

```java
package com.instagram.web.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank @Email String email
) {}
```

Create `ResetPasswordRequest.java`:

```java
package com.instagram.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 128) String newPassword
) {}
```

---

### 12. Permit the new endpoints in SecurityConfig

Open `SecurityConfig.java` and add two matchers inside the `authorizeHttpRequests` chain, alongside the existing `/api/v1/auth/**` permit:

```java
.requestMatchers("/api/v1/auth/forgot-password").permitAll()
.requestMatchers("/api/v1/auth/reset-password").permitAll()
```

These are already covered by the existing `.requestMatchers("/api/v1/auth/**").permitAll()` rule if that wildcard is present. Verify and skip adding duplicates if so.

---

### 13. Tests

#### PasswordResetServiceTest (unit)

Create `backend/src/test/java/com/instagram/application/service/PasswordResetServiceTest.java`:

Cover the following cases with Mockito mocks for `UserRepository`, `PasswordResetTokenRepository`, `EmailPort`, and `PasswordEncoder`:

| Test | Expected |
|------|----------|
| `requestReset_knownEmail_savesTokenAndSendsEmail` | `tokenRepository.save()` and `emailPort.sendPasswordResetEmail()` both called once |
| `requestReset_unknownEmail_doesNothing` | No calls to `tokenRepository` or `emailPort` |
| `resetPassword_validToken_updatesPasswordAndMarksUsed` | `userRepository.updatePasswordHash()` called; `tokenRepository.markUsed()` called |
| `resetPassword_unknownToken_throwsInvalidException` | `InvalidPasswordResetTokenException` thrown |
| `resetPassword_alreadyUsedToken_throwsInvalidException` | `InvalidPasswordResetTokenException` thrown |
| `resetPassword_expiredToken_throwsExpiredException` | `ExpiredPasswordResetTokenException` thrown |

#### PasswordResetControllerTest (WebMvcTest)

Create `backend/src/test/java/com/instagram/web/controller/PasswordResetControllerTest.java`:

| Test | Expected |
|------|----------|
| `forgotPassword_validEmail_returns200` | HTTP 200; use case called once |
| `forgotPassword_invalidEmail_returns400` | HTTP 400 (validation) |
| `resetPassword_validRequest_returns200` | HTTP 200; use case called once |
| `resetPassword_blankToken_returns400` | HTTP 400 |
| `resetPassword_shortPassword_returns400` | HTTP 400 |
| `resetPassword_invalidToken_returns400` | Service throws `InvalidPasswordResetTokenException`; HTTP 400 |
| `resetPassword_expiredToken_returns400` | Service throws `ExpiredPasswordResetTokenException`; HTTP 400 |

---

## Checklist

- [ ] `spring-boot-starter-mail` added to `pom.xml`
- [ ] `V9__password_reset_tokens.sql` migration created with `token`, `user_id`, `expires_at`, `used`, `created_at` columns and two indexes
- [ ] `PasswordResetToken` domain record with `isExpired()` and `isValid()` helpers
- [ ] `InvalidPasswordResetTokenException` + `ExpiredPasswordResetTokenException` registered in `GlobalExceptionHandler` → 400
- [ ] `RequestPasswordResetUseCase` and `ResetPasswordUseCase` in-port interfaces
- [ ] `PasswordResetTokenRepository` and `EmailPort` out-port interfaces
- [ ] `PasswordResetService` uses `SecureRandom` (not `UUID`), deletes expired tokens before creating new ones, sends email, and is silent for unknown emails
- [ ] `PasswordResetTokenJpaEntity` + `PasswordResetTokenJpaRepository` + `PasswordResetTokenPersistenceAdapter`
- [ ] `JavaMailEmailAdapter` sends a plain-text email with the reset link
- [ ] `PasswordResetController` with two endpoints; DTOs have `@NotBlank`, `@Email`, `@Size` constraints
- [ ] Both endpoints permitted in `SecurityConfig`
- [ ] `app.base-url` and `app.password-reset.token-expiry-minutes` properties present in `application.yml`
- [ ] Local SMTP config points to Mailpit in `application-local.yml`
- [ ] SMTP env vars added to `.env.example`
- [ ] Unit tests and controller tests pass

---

## How to Verify

**1. Start Mailpit (local SMTP catcher):**

```powershell
# With Docker
docker run -d -p 1025:1025 -p 8025:8025 axllent/mailpit
# Web UI at http://localhost:8025
```

**2. Start the backend with the local profile:**

```powershell
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**3. Request a password reset:**

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/forgot-password" `
  -ContentType "application/json" `
  -Body '{"email":"testuser@example.com"}'
```

Expected: HTTP 200. Check Mailpit at `http://localhost:8025` — the reset email should appear with the reset link.

**4. Unknown email returns 200 (no enumeration):**

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/forgot-password" `
  -ContentType "application/json" `
  -Body '{"email":"nobody@doesnotexist.com"}'
```

Expected: HTTP 200 (no email sent, no error revealed).

**5. Reset the password with the token from the email:**

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/reset-password" `
  -ContentType "application/json" `
  -Body '{"token":"<paste-token-from-email>","newPassword":"NewSecurePass1!"}'
```

Expected: HTTP 200. Verify you can log in with the new password.

**6. Replaying the same token returns 400:**

```powershell
try {
    Invoke-RestMethod -Method Post `
      -Uri "http://localhost:8080/api/v1/auth/reset-password" `
      -ContentType "application/json" `
      -Body '{"token":"<same-token>","newPassword":"AnotherPass1!"}'
} catch {
    $_.Exception.Response.StatusCode
}
```

Expected: `400` (token already used).

---

## Notes / Gotchas

**"Email is not delivered locally."**
Make sure Mailpit is running before starting the backend. The local profile points to `localhost:1025`. If you see a `ConnectException`, Mailpit is not running.

**"Token contains `+` or `/` and gets corrupted in a URL."**
Use `Base64.getUrlEncoder().withoutPadding()` — this replaces `+` with `-` and `/` with `_`, producing a URL-safe string that survives query-parameter encoding without `encodeURIComponent`.

**"Should I hash the token before storing it?"**
For a production system, yes — store `SHA-256(token)` in the database so a DB breach cannot reveal live tokens. For this project level, storing the raw token is acceptable and keeps the adapter simple. Add a `// TODO: hash before persistence` comment if you want to flag it.

**"What if the user requests reset multiple times?"**
`deleteExpiredTokensForUser` cleans up old expired rows before creating a new one. However, a user could accumulate multiple *valid* tokens if they request reset several times in quick succession. All valid tokens work — the last-issued one is what the email contains. This is acceptable behaviour for this project.

**"The frontend needs a `/reset-password` page."**
The backend delivers the link as `{app.base-url}/reset-password?token=...`. The frontend must add a route at `/reset-password` that reads the `token` query parameter, shows a "new password" form, and calls `POST /api/v1/auth/reset-password`. This frontend page is out of scope for this task.

---

## Learning Resources

### Concepts to learn
- **Spring Mail** — sending emails from Spring Boot — https://spring.io/guides/gs/sending-email/
- **SecureRandom** — cryptographically strong random token generation — https://docs.oracle.com/en/java/docs/api/java.base/java/security/SecureRandom.html
- **Mailpit** — local SMTP catcher with web UI — https://mailpit.axllent.org/

### Official docs (code reference)
- **Spring Boot Mail auto-configuration** — https://docs.spring.io/spring-boot/docs/current/reference/html/io.html#io.email
- **Micrometer (for future metric on resets)** — https://docs.micrometer.io/micrometer/reference/

# TASK-10.52 — Logging best practices audit

## Overview

Audit the backend codebase for the four most harmful logging anti-patterns — string concatenation, swallowed stack traces, logged secrets, and log-and-rethrow duplication — then fix every instance found. Write a short logging-conventions guide (either in `CONTRIBUTING.md` or `docs/`) so the team knows what to do next time. The fix is verifiable: after the audit, grepping for the anti-patterns should return zero matches.

---

## Level

Warm-up · Pairs with [TASK-10.25 — Trace one request end-to-end](TASK-10.25-trace-request-end-to-end.md), [TASK-10.26 — Structured logging & MDC](TASK-10.26-structured-logging-mdc.md), and [TASK-10.49 — Troubleshooting runbook](TASK-10.49-troubleshooting-runbook.md)

---

## Why

In production you cannot attach a debugger. Logs are the only window into what went wrong. When a `catch` block calls `log.warn(ex.getMessage())` instead of `log.warn("operation failed", ex)`, the stack trace is permanently lost — you can see what failed but never where or why. When developers use string concatenation (`"user " + userId`) instead of parameterized placeholders, the string is built even when the log level is disabled, which wastes CPU in hot paths. Logging JWT tokens, refresh tokens, or user passwords turns a routine log scan into a credential exposure incident. These are not cosmetic issues — they are the difference between a twenty-minute incident diagnosis and a two-day investigation.

---

## Prerequisites

- The backend compiles and the test suite passes: `cd backend && mvn test`.
- [TASK-10.25](TASK-10.25-trace-request-end-to-end.md) — you have manually traced a request through the logs and felt why good log output matters.
- [TASK-10.26](TASK-10.26-structured-logging-mdc.md) — ideally started or completed. TASK-10.26 adds `requestId` and `userId` MDC fields; the parameterized logging conventions in this task ensure those MDC fields appear on every line correctly.
- **Concepts to skim:**
  - **SLF4J parameterized logging** — `log.info("post {} by user {}", postId, userId)` uses `{}` placeholders. The string is only built if the `INFO` level is actually enabled. Official docs: [slf4j.org](https://www.slf4j.org/faq.html#logging_performance).
  - **Log levels (SLF4J/Logback order):** `TRACE < DEBUG < INFO < WARN < ERROR`. Use the right level for the right situation — see the conventions table below.
  - **MDC (Mapped Diagnostic Context)** — a per-thread key/value store that Logback appends to every log line automatically. Fields set by TASK-10.26 (`requestId`, `userId`) appear in structured logs without any extra code, but only if every log statement uses parameterized format (never raw concatenation).
  - **Stack trace in `log.error()`** — SLF4J adds the full stack trace only if the `Throwable` is passed as the **last argument**: `log.error("message", ex)`. `log.error(ex.getMessage())` logs only the message string — the stack trace is discarded.

---

## Files to Create / Modify

```
# Convention guide (create one of these — pick the one that fits your team):
CONTRIBUTING.md                                                         (new)
  -- OR --
docs/logging-conventions.md                                             (new)

# Source fixes (modify — these already exist):
backend/src/main/java/com/instagram/adapter/in/web/GlobalExceptionHandler.java   (modify)
backend/src/main/java/com/instagram/application/service/UserService.java          (modify)
```

---

## Step-by-Step

### 1. Run the anti-pattern audit grep commands

Before changing any code, run each grep to get a full inventory of the problems. Fix nothing yet — just collect the list.

**Anti-pattern 1: string concatenation in log calls**

```powershell
# Find log calls that use + to build the message string
grep -rn 'log\.\(warn\|info\|error\|debug\).*".*+' `
    backend/src/main/java/com/instagram/
```

Git Bash / PowerShell with ripgrep:
```powershell
# PowerShell
Select-String -Path "backend/src/main/java/com/instagram/**/*.java" `
    -Pattern 'log\.(warn|info|error|debug)\([^)]*\+' -Recurse
```

Known instance to find:
```
GlobalExceptionHandler.java:75: log.warn(fieldName + " " + combinedString);
UserService.java:179:            log.warn("confirmPasswordReset() - ... not yet implemented; " +
                                        "throwing PasswordResetTokenExpiredException ...");
```

**Anti-pattern 2: exception logged without stack trace (`log.*(ex.getMessage())`)**

```powershell
Select-String -Path "backend/src/main/java/com/instagram/**/*.java" `
    -Pattern 'log\.(warn|info|error|debug)\(ex\.getMessage\(\)\)' -Recurse
```

Known instances to find — every handler in `GlobalExceptionHandler.java` uses this pattern:
```
log.warn(ex.getMessage());   ← appears ~25 times, one per exception type
```

**Anti-pattern 3: secrets or PII in log calls**

```powershell
Select-String -Path "backend/src/main/java/com/instagram/**/*.java" `
    -Pattern 'log\.(info|debug|warn|error).*(?i)(password|token|secret|email|phone)' -Recurse
```

Review each match manually. A log line referencing a `userId` variable is acceptable; one referencing a `password` or `accessToken` variable is not.

**Anti-pattern 4: log-and-rethrow (the same exception logged more than once)**

```powershell
# Find catch blocks that both log and rethrow — look for 'log.' followed by 'throw' in the same catch
Select-String -Path "backend/src/main/java/com/instagram/**/*.java" `
    -Pattern 'catch.*\{' -Recurse -Context 0,4 | Select-String "log\."
```

Review each match. If the catch block logs the exception AND re-throws it (or wraps and throws), the same error will appear twice in the log as it bubbles up the call stack. Pick one layer to log — typically the outermost handler (`GlobalExceptionHandler`) — and remove the log from inner layers.

---

### 2. Write the logging conventions guide

Create `CONTRIBUTING.md` at the project root (or `docs/logging-conventions.md` if a `CONTRIBUTING.md` already exists). Paste the content below:

```markdown
# Logging Conventions

All backend logging uses **SLF4J with Logback**. The logger is declared at the top of each class:

```java
private static final Logger log = LoggerFactory.getLogger(MyClass.class);
```

---

## When to use each level

| Level   | Use for                                                                         | Example |
|---------|---------------------------------------------------------------------------------|---------|
| `ERROR` | Unexpected failures that require immediate attention — the operation cannot proceed. Always include the full exception. | `log.error("Failed to save post id={}", postId, ex)` |
| `WARN`  | Recoverable problems or expected business-rule violations (not-found, conflict). The stack trace is usually not needed because the cause is a named domain exception. | `log.warn("Post not found: id={}", postId)` |
| `INFO`  | Key lifecycle events (service started, user registered, job completed). One line per significant action — not one line per loop iteration. | `log.info("User registered: id={}", userId)` |
| `DEBUG` | Diagnostic detail useful during development. Disabled in production. Do not put PII here. | `log.debug("Cache miss for key={}", cacheKey)` |
| `TRACE` | Very fine-grained — SQL parameter binding, serialization steps. Disabled unless actively debugging. | `log.trace("Binding param idx={} value={}", i, val)` |

---

## Parameterized placeholders — always, without exception

**Correct:**
```java
log.info("Created post id={} for user id={}", post.getId(), userId);
```

**Wrong — string concatenation:**
```java
log.info("Created post id=" + post.getId() + " for user id=" + userId); // BAD
```

The `{}` placeholder syntax delays string construction until the level check passes. With concatenation the string is always built, even when `INFO` logging is disabled — wasting CPU in hot paths. This is especially important inside loops.

---

## Always pass the exception as the last argument

**Correct (stack trace preserved):**
```java
log.error("Failed to create post id={}", postId, ex);
log.warn("Unexpected state in payment id={}", paymentId, ex);
```

**Wrong (stack trace lost forever):**
```java
log.error(ex.getMessage());                    // BAD — no context, no stack trace
log.error("error: " + ex.getMessage());        // BAD — concatenation + no stack trace
log.warn("Payment failed: {}", ex.getMessage()); // BAD — message only, trace discarded
```

The rule: if you have an exception variable in scope, it must be the **last argument** to the log call.

---

## Never log secrets or PII

The following must never appear in any log call, regardless of level:

- Passwords or password hashes
- JWT access tokens or refresh tokens
- OAuth2 access / refresh tokens (`access_token`, `refresh_token`)
- Email addresses (use `userId` or a redacted form `u***@example.com` if essential)
- Phone numbers
- Message body content

If a log entry might contain sensitive data, redact it:
```java
// Acceptable — logs the fact, not the value
log.info("Password reset requested for userId={}", userId);

// Not acceptable
log.info("Resetting password for email={}", user.getEmail()); // BAD
```

---

## No log-and-rethrow duplication

If a catch block logs the exception and then re-throws (or wraps and throws), the same error will appear twice in the log. Log at the outermost boundary — the `GlobalExceptionHandler` — and let exceptions bubble up silently through inner layers.

**Wrong:**
```java
// Inner service — logs it
catch (Exception ex) {
    log.error("Failed in PostService", ex);  // first log
    throw new RuntimeException("wrapped", ex);
}

// GlobalExceptionHandler — logs it again
@ExceptionHandler(Exception.class)
public ResponseEntity<?> handleAll(Exception ex) {
    log.error("Unhandled exception", ex);    // duplicate log
    ...
}
```

**Correct:**
```java
// Inner service — just rethrow, no log
catch (Exception ex) {
    throw new PostCreationException("Could not save post", ex);
}

// GlobalExceptionHandler — log once at the boundary
@ExceptionHandler(PostCreationException.class)
public ResponseEntity<?> handlePostCreation(PostCreationException ex) {
    log.error("Post creation failed", ex);
    ...
}
```

---

## Correlation context (MDC — after TASK-10.26)

After TASK-10.26 is complete, `requestId` and `userId` are set in MDC for every request. These fields appear automatically in every log line — you do not need to include them in message text:

```java
// After TASK-10.26 — MDC provides requestId and userId automatically
log.info("Post created id={}", post.getId());
// Log output: 2026-05-23 14:22:01 INFO  [requestId=abc123, userId=uuid] PostService — Post created id=xyz789

// Before TASK-10.26 — manually include context
log.info("Post created id={} for userId={}", post.getId(), userId);
```

Until TASK-10.26 is done, include the most useful identifiers (postId, userId) in the message itself.
```

---

### 3. Fix the string concatenation instances

**Fix 1: `GlobalExceptionHandler.java` line 75**

The current code:
```java
log.warn(fieldName + " " + combinedString);
```

Replace with:
```java
log.warn("Validation failed: field={} errors={}", fieldName, combinedString);
```

**Fix 2: `UserService.java` lines 179–180**

The current code:
```java
log.warn("confirmPasswordReset() - PasswordResetTokenRepository not yet implemented; " +
        "throwing PasswordResetTokenExpiredException as stub behaviour");
```

This is a multi-line string literal, not a runtime concatenation, so the performance concern does not apply. However, it is still cleaner as a single string. Replace with:
```java
log.warn("confirmPasswordReset(): PasswordResetTokenRepository not yet implemented - throwing PasswordResetTokenExpiredException as stub behaviour");
```

---

### 4. Fix the missing-stack-trace instances in `GlobalExceptionHandler.java`

Every `@ExceptionHandler` method currently uses `log.warn(ex.getMessage())`. This discards the exception's stack trace. For named domain exceptions (like `PostNotFoundException`) the stack trace is not always interesting — the message is the whole story. But the pattern `log.warn(ex.getMessage())` is still wrong because:

1. It discards the stack trace even when you want it (e.g. `MediaUploadException`).
2. It is inconsistent — some handlers should log at `ERROR`, not `WARN`.

Apply the following fixes:

**4a. For well-understood business-rule exceptions (4xx) — log at WARN with the message as a parameter:**

These are expected failures. The message is informative, the stack trace is noise. Change all of these from `log.warn(ex.getMessage())` to `log.warn("{}", ex.getMessage())` — this preserves the parameterized-placeholder style even for a single value.

Affected handlers (change all `log.warn(ex.getMessage())` to `log.warn("{}", ex.getMessage())`):
- `handlePostNotFound`, `handleUserNotFound`, `handleUserAlreadyExists`
- `handleInvalidCredentials`, `handlePasswordResetTokenExpired`
- `handleAlreadyFollowing`, `handleFollowRequestNotFound`, `handleCannotFollowYourself`
- `handleAlreadyLiked`, `handleNotLiked`
- `handleCommentNotFound`, `handleUnauthorizedCommentAccess`
- `handleAlreadySaved`, `handleNotSaved`
- `handleConversationNotFound`, `handleNotConversationMember`, `handleMessageNotFound`
- `handleNotificationNotFound`, `handleUnauthorizedNotificationAccess`
- `handleAlreadyBlocked`, `handleNotBlocked`, `handleSelfBlock`
- `handleUnauthorizedPostAccess`, `handleUnauthorizedModerationAccess`, `handleReportNotFound`

**4b. For unexpected 5xx errors — log at ERROR with the exception object:**

`handleMediaUpload` maps a `MediaUploadException` to HTTP 500. A storage failure is worth a stack trace:
```java
// Before:
log.warn(ex.getMessage());

// After:
log.error("Media upload failed: {}", ex.getMessage(), ex);
```

**4c. For `handleHttpMessageNotReadable`:**
```java
// Before:
log.warn(ex.getMessage());

// After:
log.warn("Malformed request body: {}", ex.getMessage());
```

**4d. For `handleIllegalArgument`:**
```java
// Before:
log.warn(ex.getMessage());

// After:
log.warn("Illegal argument: {}", ex.getMessage());
```

---

### 5. Verify there are no logged secrets or PII

Run the secret-audit grep from Step 1 and review each match manually:

```powershell
Select-String -Path "backend/src/main/java/com/instagram/**/*.java" `
    -Pattern 'log\.(info|debug|warn|error).*(?i)(password|token|secret|email|phone)' -Recurse
```

For each match, check whether the variable being logged is:
- A domain-safe identifier (e.g. `userId`, `postId`) — acceptable.
- A credential or PII value (e.g. `user.getEmail()`, `request.getPassword()`, `accessToken`) — redact or remove.

The `UserService` stub log at line 166–167 references "reset token generated for userId" — that is acceptable because it logs the `userId`, not the token value itself. No change needed there.

---

### 6. Verify no log-and-rethrow duplication

Run the audit from Step 1 for log-and-rethrow. In the current codebase, the inner services (`PostService`, `CommentService`, etc.) do not have try/catch blocks that log before re-throwing — exceptions bubble up to `GlobalExceptionHandler` cleanly. Confirm this is still the case after your changes:

```powershell
# Find catch blocks that contain both a log call and a throw — possible duplication
Select-String -Path "backend/src/main/java/com/instagram/**/*.java" `
    -Pattern 'catch' -Recurse -Context 0,6 | Select-String "log\."
```

Review each result. If a catch block logs the exception and then throws a new or wrapped exception, you have duplication. Keep the log only in `GlobalExceptionHandler`.

---

### 7. Confirm zero anti-patterns remain (the audit grep must return nothing)

After all fixes, re-run the two critical grep commands and confirm they return no output:

```powershell
# Anti-pattern 1: string concatenation (should return nothing)
Select-String -Path "backend/src/main/java/com/instagram/**/*.java" `
    -Pattern 'log\.(warn|info|error|debug)\([^)]*\+' -Recurse

# Anti-pattern 2: exception message without exception object (should return nothing)
Select-String -Path "backend/src/main/java/com/instagram/**/*.java" `
    -Pattern 'log\.(warn|info|error|debug)\(ex\.getMessage\(\)\)' -Recurse
```

Both must return no output.

---

### 8. Run the test suite to confirm nothing is broken

```powershell
cd backend
mvn test
```

Expected: `BUILD SUCCESS`. The logging changes are cosmetic — they do not change control flow — but running the suite confirms no accidental edits slipped through.

---

## Checklist

- [ ] Follow the `logging-patterns` skill and write a short "logging conventions" note (in `CONTRIBUTING.md` or `docs/`): when to use `ERROR` / `WARN` / `INFO` / `DEBUG` / `TRACE`
- [ ] Replace string concatenation with parameterized logging — `log.info("post {} by user {}", postId, userId)`, never `log.info("post " + postId)`
- [ ] Ensure every `catch` that logs passes the exception as the **last argument** (`log.error("create post failed", ex)`) so the stack trace is preserved — never `log.error(ex.getMessage())`
- [ ] Confirm no secret or PII is ever logged (passwords, JWT/refresh tokens, emails, message bodies); redact where found
- [ ] Attach correlation context to boundary logs (the `requestId` / `userId` from MDC in TASK-10.26) and remove any logging inside hot loops
- [ ] Verify nothing logs-and-rethrows (the same error logged twice as it bubbles up the stack)

---

## How to Verify

**1. Zero string-concatenation instances in log calls**

```powershell
$matches = Select-String -Path "backend/src/main/java/com/instagram/**/*.java" `
    -Pattern 'log\.(warn|info|error|debug)\([^)]*\+' -Recurse
if ($matches) { Write-Host "FAIL: $($matches.Count) concatenation(s) found"; $matches } `
else { Write-Host "PASS: No string concatenation in log calls." }
```

Expected: `PASS: No string concatenation in log calls.`

**2. Zero `log.*(ex.getMessage())` calls**

```powershell
$matches = Select-String -Path "backend/src/main/java/com/instagram/**/*.java" `
    -Pattern 'log\.(warn|info|error|debug)\(ex\.getMessage\(\)\)' -Recurse
if ($matches) { Write-Host "FAIL: $($matches.Count) bare getMessage() call(s)"; $matches } `
else { Write-Host "PASS: No bare ex.getMessage() in log calls." }
```

Expected: `PASS: No bare ex.getMessage() in log calls.`

**3. Conventions guide exists**

```powershell
(Test-Path CONTRIBUTING.md) -or (Test-Path docs/logging-conventions.md)
```

Expected: `True`

**4. Test suite still passes**

```powershell
cd backend; mvn test -q
```

Expected: `BUILD SUCCESS`

**5. (Manual check) Run the app and send a bad request — confirm the log looks right**

Start the backend and send a request with a missing required field:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/register `
    -ContentType application/json `
    -Body '{"username":"x"}'
```

Look in the backend log for a `WARN` line from `GlobalExceptionHandler`. It must:
- Use parameterized placeholders (`field=... errors=...`), not string concatenation.
- Not contain a stack trace (400-level errors are expected failures — no stack trace needed).
- Not contain any password or token value from the request body.

**Passing result:** All five checks pass. The diff for the commit contains only parameterization and level fixes in `GlobalExceptionHandler.java` and `UserService.java`, plus a new `CONTRIBUTING.md` or `docs/logging-conventions.md`. No logic changes. No test failures.

---

## Notes / Gotchas

**"After the fix, some 4xx log lines no longer have a stack trace — is that a regression?"**
No. A `PostNotFoundException` is a normal, expected outcome (user asked for a post that does not exist). Stack traces on expected 4xx failures add noise and can obscure the real `ERROR` lines you care about. For `MediaUploadException` (500) the fix in Step 4b explicitly adds the exception object so the stack trace is preserved.

**"The fix `log.warn("{}", ex.getMessage())` looks odd — why not just use `log.warn(ex.getMessage())`?"**
`log.warn(String message)` is a valid SLF4J overload and the string is already built (it is a method return value, not a literal plus concatenation), so there is no performance difference. However, `log.warn("{}", ex.getMessage())` makes the parameterized pattern consistent and visible at a glance, and it avoids accidentally triggering the `log.warn(String, Object...)` overload with unexpected behaviour if the message string happens to contain `{}`.

**"I see `log.info()` calls inside a loop in the notification event handler — should I remove them?"**
Check whether the loop is on the hot path (called on every HTTP request) or on a background event (called occasionally). For background event handlers, one `log.info()` per event is fine. For loops that run inside a request-handling thread (e.g. iterating over post hashtags), prefer a single summary log after the loop: `log.debug("Processed {} hashtags for postId={}", count, postId)`.

**"`GlobalExceptionHandler` already has `@RestControllerAdvice` — why doesn't it log at `ERROR` for all exceptions?"**
Most domain exceptions map to 4xx HTTP status codes and represent expected business failures (user not found, duplicate username). These are not application errors — they are correct behaviour. Logging them at `ERROR` would drown out the real errors. The convention is: `ERROR` for 5xx (unexpected), `WARN` for 4xx (expected business rules).

**Reference docs:**
- [SLF4J — FAQ: what is the fastest way of (not) logging?](https://www.slf4j.org/faq.html#logging_performance) — explains why parameterized placeholders matter
- [Logback — Configuration](https://logback.qos.ch/manual/configuration.html) — log level and appender configuration
- [SLF4J — Parameterized logging](https://www.slf4j.org/api/org/slf4j/Logger.html) — API reference showing the `(String format, Object... args)` overloads

**Related tasks:**
- [TASK-10.25](TASK-10.25-trace-request-end-to-end.md) — trace a request manually; motivates why good log format matters
- [TASK-10.26](TASK-10.26-structured-logging-mdc.md) — adds `requestId`/`userId` MDC fields; works best when all log calls use parameterized format (this task)
- [TASK-10.49](TASK-10.49-troubleshooting-runbook.md) — the troubleshooting runbook that log quality directly enables

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Log levels & when to use them** — ERROR/WARN/INFO/DEBUG/TRACE — https://www.slf4j.org/manual.html
- **Parameterized logging** — `log.info("user {}", id)` avoids string concat cost — https://www.slf4j.org/faq.html#logging_performance
- **Log to stdout (12-Factor)** — let the platform handle log routing — https://12factor.net/logs
- **Never log secrets/PII** — what to redact and why — https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html

### Official docs (code reference)
- **SLF4J manual** — https://www.slf4j.org/manual.html
- **OWASP Logging Cheat Sheet** — https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html

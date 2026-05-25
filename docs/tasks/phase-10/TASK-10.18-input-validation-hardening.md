# TASK-10.18 — Input validation hardening

## Overview

Audit every request DTO in the project for missing Bean Validation annotations, confirm that malformed JSON already returns `400` (it does — `GlobalExceptionHandler` handles `HttpMessageNotReadableException`), and add HTML stripping to all user-generated text fields to prevent stored cross-site scripting (XSS). This task touches the adapter boundary and leaves the domain layer unchanged.

---

## Level

**Core** — Pairs with [TASK-10.21 (Object-level authorization & IDOR audit)](TASK-10.21-object-level-authorization-idor.md), which audits the authorization side of the adapter boundary.

---

## Why

The domain layer trusts its inputs. If a controller passes an unchecked string from the HTTP body straight into a use-case command, the only thing preventing malicious input is the client's own good behaviour — which cannot be assumed. Three concrete problems: (1) an over-length caption can exceed database column limits and cause a 500 error, which leaks stack information; (2) malformed JSON that slips past the deserializer can cause `NullPointerException` in the domain; (3) a caption containing `<script>alert(1)</script>` stored in the database will execute in every browser that renders the post. Validating at the adapter boundary costs a few annotations; fixing a stored-XSS incident costs enormously more.

---

## Prerequisites

- `spring-boot-starter-validation` is already in `pom.xml` — Bean Validation (`@NotBlank`, `@Size`, `@Pattern`) is available without adding a dependency.
- `GlobalExceptionHandler` in `adapter/in/web/` already handles `HttpMessageNotReadableException` → `400` and `MethodArgumentNotValidException` → `400`. Confirm before writing new handlers.
- The DB schema at `docs/database/schema.sql` defines the maximum lengths for each user-editable column — use those as the `@Size` upper bounds.
- **Concept gloss:**
  - **Bean Validation** — Jakarta EE specification for declarative field constraints via annotations. Spring's `@Valid` on a controller parameter triggers validation before the method body runs.
  - **`MethodArgumentNotValidException`** — thrown when `@Valid` finds a violation; `GlobalExceptionHandler` maps it to `400`.
  - **Stored XSS** — an attacker stores malicious HTML/script in a database via an API; every user who reads that data has the script executed in their browser.
  - **OWASP AntiSamy** — a library that sanitizes HTML by stripping disallowed tags/attributes according to a configurable policy.

---

## Files to Create / Modify

```
backend/src/main/java/com/instagram/adapter/in/web/dto/request/                       (modify — audit all files)
backend/pom.xml                                                                         (modify — add AntiSamy)
backend/src/main/java/com/instagram/infrastructure/security/HtmlSanitizer.java         (new)
backend/src/main/java/com/instagram/adapter/in/web/GlobalExceptionHandler.java         (verify — no change needed if handler exists)
```

Key request DTOs to audit (located in `adapter/in/web/dto/request/`):

```
CreatePostRequest.java
UpdatePostRequest.java
RegisterRequest.java
UpdateProfileRequest.java
CreateConversationRequest.java
SendMessageRequest.java
LoginRequest.java
```

---

## Step-by-Step

### 1. Open the database schema to know the column constraints

Open `docs/database/schema.sql` and note the `VARCHAR` lengths for user-editable columns. The values you need are:

| Column | Table | Max length |
|--------|-------|-----------|
| `caption` | `posts` | 2200 |
| `location` | `posts` | 255 |
| `bio` | `users` | 500 |
| `full_name` | `users` | 100 |
| `username` | `users` | 30 |
| `email` | `users` | 255 |
| `reason` | `reports` | 255 |
| `content` | `messages` | 2000 |
| `content` | `comments` | 2200 |

Use these as the `max` value in `@Size` annotations.

### 2. Audit every request DTO and add missing annotations

For each DTO file listed above, add the appropriate annotation to every field. The rules are:

- A field that must be non-empty: `@NotBlank` (also implies non-null for `String`).
- A field that has a maximum length from the schema: `@Size(max = N)`.
- A field that is optional but bounded: `@Size(max = N)` without `@NotBlank`.
- A UUID path variable: no annotation needed — Spring converts it and throws `400` if malformed.
- A password: `@Size(min = 8, max = 100)`.
- An email: `@Email`.
- A username: `@Pattern(regexp = "^[a-zA-Z0-9_.]{3,30}$", message = "Username must be 3–30 characters and contain only letters, digits, underscores, and periods")`.

Example — `CreatePostRequest.java`:

```java
// adapter/in/web/dto/request/CreatePostRequest.java
package com.instagram.adapter.in.web.dto.request;

import com.instagram.domain.port.in.CreatePostUseCase;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreatePostRequest(
        @Size(max = 2200, message = "Caption must not exceed 2200 characters")
        String caption,

        @Size(max = 255, message = "Location must not exceed 255 characters")
        String location,

        List<MediaItemRequest> mediaItems
) {
    public CreatePostUseCase.Command toCommand(UUID userId) {
        return new CreatePostUseCase.Command(userId, caption, location, mediaItems);
    }

    public record MediaItemRequest(
            @jakarta.validation.constraints.NotBlank String mediaUrl,
            @jakarta.validation.constraints.NotBlank String mediaType,
            Integer displayOrder
    ) {}
}
```

Example — `RegisterRequest.java`:

```java
// adapter/in/web/dto/request/RegisterRequest.java
package com.instagram.adapter.in.web.dto.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9_.]{3,30}$",
                 message = "Username must be 3–30 characters: letters, digits, underscore, period only")
        String username,

        @NotBlank
        @Email(message = "Must be a valid email address")
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 100, message = "Password must be 8–100 characters")
        String password,

        @NotBlank
        @Size(max = 100, message = "Full name must not exceed 100 characters")
        String fullName
) {}
```

Apply the same pattern to every other DTO. Each annotation should carry a human-readable `message` — this message appears in the `400` body via `GlobalExceptionHandler`.

### 3. Confirm `GlobalExceptionHandler` handles malformed JSON

Open `GlobalExceptionHandler.java`. The handler for `HttpMessageNotReadableException` already exists:

```java
@ExceptionHandler(HttpMessageNotReadableException.class)
public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
    log.warn(ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Malformed request body"));
}
```

No change is needed here. Confirm it is present; if it is missing, add it following the pattern of other handlers in that file.

### 4. Add the OWASP AntiSamy dependency

Open `backend/pom.xml`. Add AntiSamy inside `<dependencies>`:

```xml
<!-- HTML sanitisation — strip disallowed tags from user-generated text -->
<dependency>
    <groupId>org.owasp.antisamy</groupId>
    <artifactId>antisamy</artifactId>
    <version>1.7.6</version>
</dependency>
```

Run `mvn dependency:resolve -q` to confirm.

### 5. Create `HtmlSanitizer.java`

Create a small utility class that wraps the AntiSamy API. Place it in `infrastructure/security/` (not in the domain — it depends on AntiSamy, an infrastructure concern):

```java
package com.instagram.infrastructure.security;

import org.owasp.validator.html.AntiSamy;
import org.owasp.validator.html.CleanResults;
import org.owasp.validator.html.Policy;
import org.owasp.validator.html.PolicyException;
import org.owasp.validator.html.ScanException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Strips disallowed HTML tags and attributes from user-generated text
 * using the OWASP AntiSamy library and the bundled "text-only" policy.
 *
 * Usage: inject this bean into any adapter class that accepts user text,
 * and call sanitize() before passing the value to a use-case command.
 */
@Component
public class HtmlSanitizer {

    private static final Logger log = LoggerFactory.getLogger(HtmlSanitizer.class);
    private final AntiSamy antiSamy;
    private final Policy policy;

    public HtmlSanitizer() {
        try {
            // "antisamy-text-only.xml" — rejects all HTML tags; plain text only.
            // AntiSamy ships several bundled policies; text-only is the most restrictive.
            InputStream policyStream = getClass()
                    .getResourceAsStream("/antisamy-text-only.xml");
            if (policyStream == null) {
                throw new IllegalStateException(
                        "AntiSamy policy file 'antisamy-text-only.xml' not found on classpath");
            }
            this.policy = Policy.getInstance(policyStream);
            this.antiSamy = new AntiSamy();
        } catch (PolicyException e) {
            throw new IllegalStateException("Failed to load AntiSamy policy", e);
        }
    }

    /**
     * Returns the input with all HTML stripped.
     * Returns null if the input is null (preserves optional fields).
     */
    public String sanitize(String input) {
        if (input == null) {
            return null;
        }
        try {
            CleanResults results = antiSamy.scan(input, policy);
            if (!results.getErrorMessages().isEmpty()) {
                log.debug("AntiSamy stripped content: {}", results.getErrorMessages());
            }
            return results.getCleanHTML();
        } catch (ScanException | PolicyException e) {
            log.warn("AntiSamy scan failed, returning empty string: {}", e.getMessage());
            return "";
        }
    }
}
```

AntiSamy ships policy files on the classpath. The `antisamy-text-only.xml` policy strips all HTML tags and leaves only plain text — appropriate for captions, bios, and messages. If you want to allow basic formatting (bold, italic, links) in the future, switch to `antisamy-slashdot.xml`.

> **Note:** AntiSamy's policy XML file must be on the classpath. When running `mvn package`, the file in `src/main/resources/` will be included automatically. If you get a `NullPointerException` at the `getResourceAsStream` line, copy the bundled policy file from the AntiSamy JAR into `backend/src/main/resources/`.

### 6. Apply `HtmlSanitizer` in controllers that accept free-text

Inject `HtmlSanitizer` into `PostController` and call it before constructing the use-case command. The domain layer never sees raw HTML.

```java
// In PostController — constructor now includes HtmlSanitizer
private final CreatePostUseCase createPostUseCase;
// ... other use cases ...
private final HtmlSanitizer htmlSanitizer;

public PostController(CreatePostUseCase createPostUseCase,
                      /* ... */
                      HtmlSanitizer htmlSanitizer) {
    this.createPostUseCase = createPostUseCase;
    // ...
    this.htmlSanitizer = htmlSanitizer;
}

@PostMapping
public ResponseEntity<ApiResponse<PostResponse>> createPost(
        @Valid @RequestBody CreatePostRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {

    UUID userId = UUID.fromString(userDetails.getUsername());

    Post post = createPostUseCase.createPost(new CreatePostUseCase.Command(
            userId,
            htmlSanitizer.sanitize(request.caption()),
            htmlSanitizer.sanitize(request.location()),
            request.mediaItems()
    ));

    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(PostResponse.from(post, null)));
}
```

Apply the same pattern in:
- `CommentController` — sanitize `content`.
- `UserController` (update profile) — sanitize `bio`, `fullName`.
- `MessageController` — sanitize message `content`.

---

## Checklist

- [ ] Audit all request DTOs — ensure every field has `@NotNull`/`@NotBlank`/`@Size`/`@Pattern` where appropriate
  - [ ] `CreatePostRequest` — `@Size(max=2200)` on `caption`, `@Size(max=255)` on `location`
  - [ ] `RegisterRequest` — `@NotBlank @Email @Size(max=255)` on `email`; `@NotBlank @Pattern(...)` on `username`; `@Size(min=8,max=100)` on `password`; `@NotBlank @Size(max=100)` on `fullName`
  - [ ] `UpdateProfileRequest` — `@Size(max=500)` on `bio`; `@Size(max=100)` on `fullName`
  - [ ] `SendMessageRequest` — `@NotBlank @Size(max=2000)` on `content`
  - [ ] Other DTOs covered (inspect each file in `adapter/in/web/dto/request/`)
- [ ] Add `@ControllerAdvice` handler for `HttpMessageNotReadableException` (malformed JSON) → `400`
  - [ ] Confirmed present in `GlobalExceptionHandler` (no new code needed)
- [ ] Strip HTML from all user-generated text fields using OWASP AntiSamy
  - [ ] `antisamy` dependency added to `pom.xml`
  - [ ] `HtmlSanitizer.java` created in `infrastructure/security/`
  - [ ] `HtmlSanitizer.sanitize()` called in `PostController`, `CommentController`, `UserController`, `MessageController` before use-case delegation

---

## How to Verify

**Over-length caption returns 400:**

```powershell
$longCaption = "A" * 2201   # 2201 chars — one over the limit
$body = "{`"caption`":`"$longCaption`",`"mediaItems`":[]}"
$r = Invoke-WebRequest "http://localhost:8080/api/v1/posts" `
    -Method POST `
    -ContentType "application/json" `
    -Headers @{ Authorization = "Bearer $token" } `
    -Body $body `
    -SkipHttpErrorCheck
Write-Host $r.StatusCode   # Expected: 400
$r.Content                 # Expected: {"data":null,"error":"Caption must not exceed 2200 characters",...}
```

**Malformed JSON returns 400:**

```powershell
$r = Invoke-WebRequest "http://localhost:8080/api/v1/auth/login" `
    -Method POST -ContentType "application/json" `
    -Body 'this is not json' -SkipHttpErrorCheck
Write-Host $r.StatusCode   # Expected: 400
```

**HTML in caption is stripped:**

```powershell
$body = '{"caption":"<script>alert(1)<\/script>Hello","location":null,"mediaItems":[]}'
$r = Invoke-RestMethod "http://localhost:8080/api/v1/posts" `
    -Method POST -ContentType "application/json" `
    -Headers @{ Authorization = "Bearer $token" } `
    -Body $body
$r.data.caption   # Expected: "Hello" (script tag stripped)
```

---

## Notes / Gotchas

**`@NotBlank` vs `@NotNull`:**
`@NotBlank` implies non-null AND non-empty AND not just whitespace. For `String` fields that are required, `@NotBlank` is almost always what you want. Use `@NotNull` only for non-String types (numbers, enums, nested records).

**`@Valid` on the controller parameter is mandatory.**
Annotations on the DTO fields do nothing unless the controller method parameter has `@Valid`. Every controller that accepts a `@RequestBody` must have `@Valid @RequestBody`.

**AntiSamy vs plain regex for HTML stripping:**
A regex like `input.replaceAll("<[^>]+>", "")` seems simpler but is notoriously incomplete — attackers have found ways around nearly every regex pattern. AntiSamy uses a proper HTML parser, so it handles malformed tags, Unicode escapes, and entity encoding that plain regex misses.

**AntiSamy's `antisamy-text-only.xml` policy:**
This policy rejects all HTML tags. The cleaned output is the innermost text content. For example, `<b>Hello</b>` → `Hello`. If you ever need to allow basic formatting (e.g. in a rich bio field), use a more permissive policy and test it carefully.

**Do not call `sanitize()` inside the domain layer.**
The domain layer must remain free of infrastructure dependencies. HTML sanitization is an adapter concern — the controller sanitizes the raw string before handing it to the use-case command. The domain entity stores clean text and never calls AntiSamy.

**Reference docs:**
- [OWASP AntiSamy](https://owasp.org/www-project-antisamy/)
- [OWASP XSS Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
- [Spring Validation — Bean Validation](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#validation-beanvalidation)

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Input validation principles** — validate at the boundary, allow-list not deny-list — https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html
- **Jakarta Bean Validation** — `@NotNull`, `@Size`, `@Pattern` and friends — https://www.baeldung.com/javax-validation
- **Validating request bodies in Spring** — `@Valid` + the official walkthrough — https://spring.io/guides/gs/validating-form-input/
- **Mass assignment / over-posting** — never bind requests straight to entities — https://cheatsheetseries.owasp.org/cheatsheets/Mass_Assignment_Cheat_Sheet.html

### Official docs (code reference)
- **Hibernate Validator** — https://hibernate.org/validator/documentation/
- **Spring validation reference** — https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html

# TASK-10.26 — Structured logging & MDC

## Overview

This task adds a `requestId` to every log line produced by a single HTTP request, and switches the log format to JSON in non-local profiles. A `MdcLoggingFilter` runs before the security filter and stamps four fields — `requestId`, `userId`, `method`, and `path` — into SLF4J's Mapped Diagnostic Context (MDC) so that every subsequent log call made on that thread automatically carries them. After this task, you can search your log aggregator (Loki, in TASK-10.30) for a single `requestId` value and get the complete ordered lifecycle of that request across controller, service, and persistence adapter.

---

## Level

Core · Pairs with [TASK-10.25 — Trace one request end-to-end](TASK-10.25-trace-request-end-to-end.md) · Builds toward [TASK-10.30 — Loki log aggregation](TASK-10.30-loki-log-aggregation.md)

---

## Why

Free-text logs are hard to search and correlate. When a 500 error happens at 2 am, a log line that says `Error saving post` is almost useless — you cannot tell which user triggered it, which of the fifty concurrent requests it belonged to, or what the path was. JSON logs with a per-request `requestId` let you follow a single user's request across many log lines and machines: you paste the `requestId` into your log aggregator and instantly see the controller entry, the service call, the Hibernate SQL, and the exception — in order, with timing. The `userId` field lets you filter to everything one specific user experienced in a session.

---

## Prerequisites

- [TASK-10.25](TASK-10.25-trace-request-end-to-end.md) complete — you have traced a request manually and understand the controller → service → adapter order.
- The backend compiles and the local profile runs (`mvn spring-boot:run`).
- Familiarity with the `logging-patterns` skill (SLF4J, MDC, JSON logging). Skim the skill description before starting.
- You have confirmed that `JwtAuthenticationFilter` sets the `SecurityContext` before the filter chain continues — `MdcLoggingFilter` reads the user ID from the `SecurityContext`, so it must run after `JwtAuthenticationFilter`.

**Concepts to skim:**

- **MDC (Mapped Diagnostic Context)**: a per-thread key/value map that SLF4J injects into every log statement automatically. Set a key with `MDC.put("requestId", value)` and every subsequent `log.info()` on that thread includes it.
- **`OncePerRequestFilter`**: a Spring base class that guarantees the filter body runs exactly once per HTTP request, even when the request passes through a forward or include.
- **Logback `<springProfile>`**: a Logback XML element that activates a configuration block only when a specific Spring profile is active. Used here to keep human-readable logs in the `local` profile and emit JSON in `prod`.
- **`logstash-logback-encoder`**: the standard library for JSON-formatted Logback output. It reads MDC keys automatically and includes them in every JSON log object.

---

## Files to Create / Modify

```
backend/src/main/java/com/instagram/infrastructure/config/MdcLoggingFilter.java    (new)
backend/src/main/resources/logback-spring.xml                                        (new)
backend/src/main/resources/application-local.yml                                     (modify — no change needed if logging.level already correct)
backend/pom.xml                                                                       (modify — add logstash-logback-encoder)
```

---

## Step-by-Step

### 1. Add the logstash-logback-encoder dependency to pom.xml

Open `backend/pom.xml` and add the following inside `<dependencies>`. This library provides the JSON appender that Logback uses in non-local profiles:

```xml
<!-- Structured (JSON) logging for non-local profiles -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

> Spring Boot 3.3.x does not manage `logstash-logback-encoder` in its BOM, so the version must be specified explicitly. `7.4` is the latest stable release compatible with Logback 1.4.x (bundled in Spring Boot 3.3).

---

### 2. Create MdcLoggingFilter.java

Create the file at `backend/src/main/java/com/instagram/infrastructure/config/MdcLoggingFilter.java`.

This filter runs on every request. It generates a `requestId`, reads the authenticated user's ID from the `SecurityContext` (populated earlier by `JwtAuthenticationFilter`), and stores both — plus the HTTP method and path — in the MDC. The `try/finally` block in `doFilterInternal` guarantees that MDC keys are cleared after the response is sent, which prevents them from leaking into the next request handled by this thread.

```java
package com.instagram.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates the SLF4J MDC for every HTTP request so that all log lines
 * produced during that request automatically carry:
 * <ul>
 *   <li>{@code requestId} — a random UUID unique to this request</li>
 *   <li>{@code userId}    — the authenticated user's UUID, or "anonymous"</li>
 *   <li>{@code method}    — the HTTP method (GET, POST, …)</li>
 *   <li>{@code path}      — the request URI</li>
 * </ul>
 * MDC keys are cleared in a {@code finally} block to prevent thread-pool leakage.
 */
@Component
@Order(1)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_KEY = "requestId";
    private static final String USER_ID_KEY    = "userId";
    private static final String METHOD_KEY     = "method";
    private static final String PATH_KEY       = "path";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString();

        MDC.put(REQUEST_ID_KEY, requestId);
        MDC.put(METHOD_KEY, request.getMethod());
        MDC.put(PATH_KEY, request.getRequestURI());
        MDC.put(USER_ID_KEY, resolveUserId());

        // Echo the requestId back to the caller so they can correlate
        response.setHeader("X-Request-Id", requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
            MDC.remove(USER_ID_KEY);
            MDC.remove(METHOD_KEY);
            MDC.remove(PATH_KEY);
        }
    }

    private String resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails ud) {
            return ud.getUsername(); // stores the UUID string per JwtAuthenticationFilter
        }
        return "anonymous";
    }
}
```

**Why `@Order(1)`?**
The `JwtAuthenticationFilter` is registered as a `UsernamePasswordAuthenticationFilter` predecessor — it runs before the MDC filter in the raw Servlet filter chain order. However, Spring Security's `JwtAuthenticationFilter` is a `OncePerRequestFilter` added to the security filter chain via `addFilterBefore`, not via the Servlet filter registration. The `@Order(1)` here ensures `MdcLoggingFilter` is among the first Servlet-level filters and that by the time the filter chain reaches `JwtAuthenticationFilter`, the MDC `requestId` is already set. The `userId` will be "anonymous" at the start and that is acceptable — the more important field is `requestId`, which never changes for the lifetime of the request.

> If you need `userId` to be accurate from the very first log line, move the MDC `userId` update to a point after `JwtAuthenticationFilter` has run — for example, inside a custom `AuthenticationSuccessHandler` or by using Spring's `HandlerInterceptor`. For most debugging purposes, "anonymous" vs the real UUID is not important — the `requestId` correlation is what matters.

---

### 3. Create logback-spring.xml

Create the file at `backend/src/main/resources/logback-spring.xml`. Logback automatically detects this filename and processes the `<springProfile>` elements, which switch the output format based on the active Spring profile.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

  <!-- ============================================================
       LOCAL profile — human-readable coloured console output.
       MDC fields are appended at the end of each line.
       ============================================================ -->
  <springProfile name="local">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>
          %d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [rid=%X{requestId} uid=%X{userId}] - %msg%n
        </pattern>
      </encoder>
    </appender>

    <root level="INFO">
      <appender-ref ref="CONSOLE"/>
    </root>

    <!-- Fine-grained levels for local development -->
    <logger name="com.instagram" level="DEBUG"/>
    <logger name="org.hibernate.SQL" level="DEBUG"/>
    <logger name="org.springframework.web" level="INFO"/>
    <logger name="org.springframework.security" level="INFO"/>
  </springProfile>

  <!-- ============================================================
       All non-local profiles (prod, staging, test) — JSON output.
       logstash-logback-encoder serialises every MDC field as a
       top-level JSON key automatically.
       ============================================================ -->
  <springProfile name="!local">
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <!-- Include the Spring application name as a static field -->
        <customFields>{"app":"instagram"}</customFields>
        <!-- MDC fields (requestId, userId, method, path) are included automatically -->
      </encoder>
    </appender>

    <root level="INFO">
      <appender-ref ref="JSON_CONSOLE"/>
    </root>

    <logger name="com.instagram" level="INFO"/>
    <logger name="org.hibernate.SQL" level="WARN"/>
  </springProfile>

</configuration>
```

**What this produces in the `local` profile:**

```
14:32:01.123 [http-nio-8080-exec-3] INFO  c.i.adapter.in.web.PostController [rid=f3a2-... uid=550e-...] - Creating post for user 550e-...
```

**What this produces in `prod` / `staging` (JSON):**

```json
{"@timestamp":"2026-05-23T14:32:01.123Z","level":"INFO","logger_name":"c.i.adapter.in.web.PostController","message":"Creating post for user 550e-...","app":"instagram","requestId":"f3a2-...","userId":"550e-...","method":"POST","path":"/api/v1/posts"}
```

---

### 4. Replace any remaining System.out.println calls with SLF4J

Search the backend source tree for `System.out.println` and replace each one with the appropriate SLF4J call:

```powershell
# Find all occurrences in backend Java source
Get-ChildItem -Path backend/src/main/java -Recurse -Filter "*.java" |
  Select-String "System\.out\.println"
```

For each hit, open the file, add `private static final Logger log = LoggerFactory.getLogger(ClassName.class);` at the top of the class (if not already present), and replace:

```java
// Before
System.out.println("Something happened: " + value);

// After
log.info("Something happened: value={}", value);
```

> The project already imports `lombok` in the adapter layer. You may use `@Slf4j` from Lombok (which generates the `log` field automatically) in any class under `adapter/` or `infrastructure/`. Do NOT add Lombok annotations in the `domain/` layer.

---

### 5. Add a log statement to PostController and PostService

Add one `log.info()` call to each class so that the layered flow is visible in the logs immediately after this task:

**PostController.java** — inside `createPost()`, after the use-case call succeeds:

```java
// At class level (Lombok @Slf4j is already on the class via @RequiredArgsConstructor — add @Slf4j if missing)
// Inside createPost():
log.info("Post created id={} userId={}", createdPost.getId(), effectiveUserId);
```

**PostService.java** — inside `createPost()`, at the start of the method:

```java
// Add the logger field at the top of the class:
private static final Logger log = LoggerFactory.getLogger(PostService.class);

// Inside createPost():
log.debug("createPost userId={} caption_length={}", command.userId(),
    command.caption() == null ? 0 : command.caption().length());
```

These two lines, combined with the Hibernate SQL output, give you the full controller → service → persistence chain that TASK-10.25 asked you to identify manually.

---

### 6. Restart and verify the local log format

```powershell
cd backend
mvn spring-boot:run
```

Send the same `POST /api/v1/posts` request from TASK-10.25. In the console you should now see lines like:

```
14:32:01.099 [http-nio-8080-exec-3] DEBUG c.i.application.service.PostService [rid=f3a2c1b0-... uid=550e8400-...] - createPost userId=550e8400-... caption_length=18
14:32:01.101 [http-nio-8080-exec-3] DEBUG org.hibernate.SQL [rid=f3a2c1b0-... uid=550e8400-...] - insert into posts ...
14:32:01.103 [http-nio-8080-exec-3] INFO  c.i.adapter.in.web.PostController [rid=f3a2c1b0-... uid=550e8400-...] - Post created id=... userId=...
```

All three lines share the same `rid` value. That is the proof that MDC is working.

---

## Checklist

- [ ] Follow the `logging-patterns` skill instructions
- [ ] Create `MdcLoggingFilter.java` — sets `requestId`, `userId`, `method`, `path` in MDC for every request
- [ ] Update `logback-spring.xml` to output JSON format in non-local profiles
- [ ] Replace any remaining `System.out.println` with SLF4J calls

---

## How to Verify

**1. Check the MDC fields appear in every log line (local profile):**

Start the backend and send a `POST /api/v1/posts` request. In the console, confirm that the `rid=` and `uid=` fields appear in the log lines and that the same `rid` value is shared across the controller, service, and SQL lines for that one request.

**2. Check the X-Request-Id response header:**

```powershell
Invoke-WebRequest -Method Post `
  -Uri "http://localhost:8080/api/v1/posts" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body '{"caption":"test","mediaItems":[{"objectKey":"k","mediaType":"IMAGE","displayOrder":0}]}' |
  Select-Object -ExpandProperty Headers | Format-List
```

The response headers should include `X-Request-Id: <uuid>`.

**3. Check the JSON format is emitted under a non-local profile:**

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=prod"
```

Send one request. The console output should be one JSON object per line with no colour codes. Check that `requestId`, `userId`, `method`, and `path` are top-level fields:

```json
{"@timestamp":"...","level":"INFO","message":"Post created id=...","app":"instagram","requestId":"f3a2-...","userId":"550e-...","method":"POST","path":"/api/v1/posts"}
```

**4. Confirm no System.out.println remains:**

```powershell
Get-ChildItem -Path backend/src/main/java -Recurse -Filter "*.java" |
  Select-String "System\.out\.println" |
  Measure-Object -Line
```

Expected output: `Lines : 0`

---

## Notes / Gotchas

**"MDC is empty — `rid=` shows as blank."**
Make sure `MdcLoggingFilter` is actually being invoked. If you accidentally placed it in a package that Spring's component scan does not cover, it will not be registered as a bean. Verify by adding a temporary `log.info("MDC filter running")` at the top of `doFilterInternal` and checking for it in the console.

**"userId shows 'anonymous' even for authenticated requests."**
`MdcLoggingFilter` runs early in the Servlet filter chain, potentially before `JwtAuthenticationFilter` populates the `SecurityContext`. The `requestId` is always correct; the `userId` being "anonymous" on the first few lines is acceptable. If you need it, you can update `MDC.put(USER_ID_KEY, ...)` again inside the controller after authentication is confirmed — or accept that `anonymous` on pre-auth lines is fine.

**"Logback XML is not being picked up."**
Logback loads `logback-spring.xml` from the classpath root automatically when the Spring Boot parent POM is used. If you named the file `logback.xml` (without `-spring`), the `<springProfile>` elements are silently ignored because those are processed by Spring Boot's Logback integration, not by Logback itself. Rename to `logback-spring.xml`.

**"The JSON output has duplicate fields."**
`LogstashEncoder` merges MDC keys into the JSON root by default. If you see both `mdc.requestId` and `requestId`, you have a custom field configuration conflict — remove the custom `<mdcFields>` block and let the encoder include them automatically.

**"I get a `ClassNotFoundException: net.logstash.logback.encoder.LogstashEncoder`."**
You likely forgot to add the `logstash-logback-encoder` dependency to `pom.xml` in step 1.

**References:**
- [SLF4J MDC docs](https://www.slf4j.org/manual.html#mdc)
- [Logback `<springProfile>` docs](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.logging.logback-extensions.profile-specific)
- [logstash-logback-encoder GitHub](https://github.com/logfellow/logstash-logback-encoder)

**Cross-task references:**
- [TASK-10.25](TASK-10.25-trace-request-end-to-end.md) — manual request tracing that motivates this task
- [TASK-10.30](TASK-10.30-loki-log-aggregation.md) — ships these JSON logs to Loki and lets you query by `requestId`

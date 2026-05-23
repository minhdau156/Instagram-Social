# TASK-10.14 — Add baseline security headers

## Overview

Add three standard HTTP security headers to every response the backend sends: `X-Content-Type-Options`, `X-Frame-Options`, and `Content-Security-Policy`. These headers are set once in `SecurityConfig` and cost almost nothing to implement, but each one closes off a different class of browser-based attack. This is the right task to do first in Phase 10 Security because it gives you an immediate win and builds intuition for the bigger hardening work that follows.

---

## Level

**Warm-up** — Pairs with [TASK-10.20 (HTTPS / TLS)](TASK-10.20-https-tls.md), which adds the `Strict-Transport-Security` header alongside these three.

---

## Why

Browsers trust the server's instructions about how to handle its own content. Without explicit headers, a browser will:

- Guess at a file's type even if the server labelled it wrong (`Content-Type` sniffing), which lets an attacker trick the browser into executing a script disguised as an image.
- Embed your app inside an `<iframe>` on another domain and overlay invisible buttons to trick users into clicking things they never meant to (clickjacking).
- Load external scripts, images, and frames from any origin, which is the entry point for many cross-site scripting (XSS) attacks.

Each of the three headers directly blocks one of those scenarios, with a single line of Spring Security configuration.

---

## Prerequisites

- [TASK-0.1](../../phase-0/TASK-0.1-project-setup.md) — project structure and Spring Boot running locally.
- Familiarity with Spring Security's `HttpSecurity` DSL — the `SecurityConfig` class at `backend/src/main/java/com/instagram/infrastructure/security/SecurityConfig.java` already uses it.
- **Concept gloss:**
  - **`X-Content-Type-Options: nosniff`** — instructs the browser never to guess the MIME type; use only what the server declared.
  - **`X-Frame-Options: DENY`** — prevents any page from embedding your app in an `<iframe>`, blocking clickjacking.
  - **`Content-Security-Policy`** — a whitelist of approved sources for scripts, images, fonts, etc. A strict policy prevents injected scripts from loading.
  - **Spring Security headers DSL** — `http.headers(h -> h.frameOptions(...).contentTypeOptions(...).contentSecurityPolicy(...))`.

---

## Files to Create / Modify

```
backend/src/main/java/com/instagram/infrastructure/security/SecurityConfig.java   (modify)
```

---

## Step-by-Step

### 1. Open `SecurityConfig.java`

The file is at:

```
backend/src/main/java/com/instagram/infrastructure/security/SecurityConfig.java
```

The existing `filterChain` method configures CORS, CSRF, session management, and authorization rules. You will extend it with a `.headers(...)` block.

### 2. Add the headers block inside `filterChain`

Locate the `http` chain inside `filterChain`. Add a `.headers(...)` customizer **before** the `.cors(...)` call (or anywhere in the chain — order does not affect the output headers, only readability):

```java
http
    .headers(headers -> headers
        // Prevent MIME-type sniffing: browser must use the declared Content-Type.
        .contentTypeOptions(Customizer.withDefaults())
        // Prevent the page from being embedded in an iframe on any other domain.
        .frameOptions(frame -> frame.deny())
        // Baseline CSP: same-origin only for scripts and frames;
        // expand when TASK-10.20 confirms TLS is in place.
        .contentSecurityPolicy(csp -> csp
            .policyDirectives(
                "default-src 'self'; " +
                "script-src 'self'; " +
                "frame-ancestors 'none'; " +
                "object-src 'none'"
            )
        )
    )
    .cors(cors -> cors.configurationSource(corsConfigurationSource))
    // ... rest of the existing chain unchanged
```

Add the import at the top of the file:

```java
import org.springframework.security.config.Customizer;
```

### 3. Check the full `filterChain` signature compiles

The `SecurityConfig` already injects `CorsConfigurationSource`, `JwtAuthenticationFilter`, and `OAuth2SuccessHandler` via constructor injection. No new beans are required for the headers block.

Run the backend to confirm it starts without errors:

```powershell
# from the repo root
cd backend; mvn spring-boot:run
```

Expected last line in the console:

```
Started SocialMediaApplication in X.XXX seconds
```

### 4. Verify the headers in the browser DevTools

With the app running, open `http://localhost:8080/swagger-ui.html` in a browser.

- Open **DevTools** (F12) → **Network** tab.
- Click any request in the list (e.g. the top-level document or a `GET /v3/api-docs` request).
- Click **Headers** → scroll to the **Response Headers** section.

You should see:

```
content-security-policy: default-src 'self'; script-src 'self'; frame-ancestors 'none'; object-src 'none'
x-content-type-options: nosniff
x-frame-options: DENY
```

### 5. Verify with `curl` from the terminal

```powershell
curl -i http://localhost:8080/api/v1/auth/login `
     -X POST `
     -H "Content-Type: application/json" `
     -d '{"identifier":"test","password":"test"}'
```

The response headers (even on a `401`) must include all three:

```
HTTP/1.1 401
x-content-type-options: nosniff
x-frame-options: DENY
content-security-policy: default-src 'self'; ...
```

### 6. (When TASK-10.20 is complete) add HSTS

After TLS termination is configured (TASK-10.20), add the `Strict-Transport-Security` header to the same `.headers(...)` block:

```java
.httpStrictTransportSecurity(hsts -> hsts
    .includeSubDomains(true)
    .maxAgeInSeconds(31536000)  // 1 year
    .preload(false)             // only enable preload after careful review
)
```

Do not enable HSTS before TLS is in place — it will lock the browser out of the HTTP-only local dev server.

---

## Checklist

- [ ] Add the three headers in `SecurityConfig` (alongside the HSTS header from TASK-10.20)
  - [ ] `contentTypeOptions(Customizer.withDefaults())` — produces `X-Content-Type-Options: nosniff`
  - [ ] `frameOptions(frame -> frame.deny())` — produces `X-Frame-Options: DENY`
  - [ ] `contentSecurityPolicy(csp -> csp.policyDirectives("..."))` — produces `Content-Security-Policy`
- [ ] Confirm each header is present on a page response in DevTools → Network → Headers

---

## How to Verify

**Browser DevTools (canonical check):**

1. Boot the backend: `cd backend; mvn spring-boot:run`
2. Open `http://localhost:8080/swagger-ui.html` in Chrome or Firefox.
3. DevTools → Network → click the first request → Response Headers.
4. Passing result — all three headers present with exactly these values:

```
x-content-type-options: nosniff
x-frame-options: DENY
content-security-policy: default-src 'self'; script-src 'self'; frame-ancestors 'none'; object-src 'none'
```

**`curl` check (copy-pasteable):**

```powershell
curl -si http://localhost:8080/actuator/health | Select-String "x-content-type|x-frame|content-security"
```

Expected output (three matching lines):

```
x-content-type-options: nosniff
x-frame-options: DENY
content-security-policy: default-src 'self'; script-src 'self'; frame-ancestors 'none'; object-src 'none'
```

If any of the three lines is missing, the `.headers(...)` block was not wired in correctly — re-read Step 2 and ensure you are editing the `filterChain` method, not the class body.

---

## Notes / Gotchas

**Spring Security already sets `X-Frame-Options` and `X-Content-Type-Options` by default — why re-declare them?**
Spring Boot 3.x enables them by default only when you use `http.headers(Customizer.withDefaults())`, which adds every default header. Because this project's `SecurityConfig` uses a custom chain and does not call `withDefaults()`, the defaults are NOT applied. Declaring them explicitly is the safe, intentional approach.

**`Content-Security-Policy` will break Swagger UI in development.**
The default Swagger UI loads inline scripts. If the browser console shows "Refused to execute inline script", relax the CSP for the Swagger path only:

```java
.contentSecurityPolicy(csp -> csp.policyDirectives(
    "default-src 'self'; " +
    "script-src 'self' 'unsafe-inline'; " +   // <-- add 'unsafe-inline' to unblock Swagger
    "frame-ancestors 'none'; " +
    "object-src 'none'"
))
```

Apply the permissive `unsafe-inline` only in development. Remove it (or tighten to a nonce) in production after TASK-10.20 confirms TLS is running.

**`frameOptions.deny()` vs `frameOptions.sameOrigin()`**
Use `DENY` unless your app intentionally embeds itself in an iframe (e.g. a widget). `SAME_ORIGIN` still allows embedding from your own domain, which is a weaker protection.

**Do not add HSTS now.**
The `Strict-Transport-Security` header must only be sent over HTTPS. Sending it over HTTP causes the browser to remember "always use HTTPS" for up to a year, which will break local HTTP development. Add HSTS only after completing [TASK-10.20](TASK-10.20-https-tls.md).

**Reference docs:**
- [OWASP Secure Headers Project](https://owasp.org/www-project-secure-headers/)
- [Spring Security — HTTP Security Headers](https://docs.spring.io/spring-security/reference/features/exploits/headers.html)
- [MDN — Content-Security-Policy](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Security-Policy)

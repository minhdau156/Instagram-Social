# TASK-10.17 — Rate limiting

## Overview

Add per-IP rate limiting to the authentication endpoints and a blanket limit to all other API endpoints. The library `bucket4j-spring-boot-starter` provides a Spring MVC filter that implements the token-bucket algorithm — the same algorithm used by GitHub, Stripe, and most production APIs. When a client exceeds its limit, the API returns `429 Too Many Requests` with a `Retry-After` header that tells the client when to try again.

---

## Level

**Core** — No direct pair in the security track, but complements [TASK-10.18 (Input validation hardening)](TASK-10.18-input-validation-hardening.md) and [TASK-10.16 (JWT hardening)](TASK-10.16-jwt-hardening.md).

---

## Why

Without rate limiting, an attacker can attempt every entry in a password dictionary against the `/login` endpoint at network speed — thousands of attempts per minute, invisible to the user whose account is being targeted. The same endpoint can be weaponized for a denial-of-service attack: flood it with requests until the server can no longer respond to legitimate users. Rate limiting caps how many requests one IP address can make in a given window. The 11th login attempt in one minute hits a wall, and the wall stays up for the rest of the window. This does not stop a distributed attack (many different IPs), but it stops the most common brute-force scripts that come from a single machine.

---

## Prerequisites

- [TASK-0.1](../../phase-0/TASK-0.1-project-setup.md) — project structure and `pom.xml` accessible.
- Understanding of Spring MVC's `HandlerInterceptor` or `OncePerRequestFilter` — Bucket4j-Spring integrates as a filter configured in `application.yml`.
- **Concept gloss:**
  - **Token bucket algorithm** — each IP starts with a bucket containing N tokens. Each request consumes one token. The bucket refills at a fixed rate. When the bucket is empty, the request is rejected.
  - **`429 Too Many Requests`** — the standard HTTP status code for rate limit exceeded (RFC 6585).
  - **`Retry-After`** — a response header containing the number of seconds the client should wait before retrying.
  - **`bucket4j-spring-boot-starter`** — an auto-configuration library that reads rate-limit rules from `application.yml` and applies them as a `javax.servlet.Filter`.

---

## Files to Create / Modify

```
backend/pom.xml                                                                        (modify — add dependency)
backend/src/main/resources/application.yml                                             (modify — add bucket4j config)
backend/src/main/java/com/instagram/infrastructure/config/RateLimitConfig.java         (new — custom 429 error body)
```

---

## Step-by-Step

### 1. Add the `bucket4j-spring-boot-starter` dependency to `pom.xml`

Open `backend/pom.xml`. Inside the `<dependencies>` block, add:

```xml
<!-- Rate limiting — token-bucket algorithm per IP -->
<dependency>
    <groupId>com.giffing.bucket4j.spring.boot.starter</groupId>
    <artifactId>bucket4j-spring-boot-starter</artifactId>
    <version>0.12.7</version>
</dependency>
```

Bucket4j-Spring requires a cache provider (Caffeine for an in-memory, single-node setup). Add Caffeine if it is not already present:

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

After editing, run:

```powershell
cd backend; mvn dependency:resolve -q
```

No build errors means the dependency resolved correctly.

### 2. Enable Spring caching (required by Bucket4j)

Add `@EnableCaching` to any existing `@Configuration` class, or create a small config class. The safest place is the new `RateLimitConfig.java`:

```java
package com.instagram.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class RateLimitConfig {

    /**
     * Caffeine cache used by Bucket4j to store per-IP token buckets.
     * Entries expire after 2 minutes of inactivity (covers a 1-minute window plus margin).
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("rate-limit-buckets");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterAccess(2, TimeUnit.MINUTES)
                .maximumSize(100_000));
        return manager;
    }
}
```

### 3. Configure rate limits in `application.yml`

Add the following block to `backend/src/main/resources/application.yml`. Read the inline comments before each value so you understand the tradeoff being made:

```yaml
bucket4j:
  enabled: true
  filters:
    # ── 1. Registration: 5 requests per IP per 10 minutes ──────────────────────
    - cache-name: rate-limit-buckets
      url: /api/v1/auth/register
      rate-limits:
        - bandwidths:
            - capacity: 5
              time: 10
              unit: minutes
              refill-speed: intervally   # refill all tokens at once after the window

    # ── 2. Login: 10 requests per IP per 1 minute ──────────────────────────────
    - cache-name: rate-limit-buckets
      url: /api/v1/auth/login
      rate-limits:
        - bandwidths:
            - capacity: 10
              time: 1
              unit: minutes
              refill-speed: intervally

    # ── 3. All other endpoints: 200 requests per IP per 1 minute ───────────────
    - cache-name: rate-limit-buckets
      url: /api/v1/.*           # regex — matches everything under /api/v1/
      rate-limits:
        - bandwidths:
            - capacity: 200
              time: 1
              unit: minutes
              refill-speed: greedy      # tokens refill continuously (smoother)

  # The HTTP response sent when the bucket is empty
  http-response-body: >
    {"data":null,"error":"Too many requests — please retry after the indicated delay","timestamp":""}
  http-status-code: 429
```

> **Important:** the three filters are evaluated in order, most specific first. Bucket4j-Spring matches filters by URL pattern and applies the first one that matches. Put `/api/v1/auth/register` and `/api/v1/auth/login` before the catch-all `/api/v1/.*`.

### 4. Add a `Retry-After` header to the 429 response

Bucket4j-Spring does not add `Retry-After` by default. Add it via a custom filter that wraps the Bucket4j response. The simplest approach is to extend `OncePerRequestFilter` and check for a `429` status in the response:

```java
// Add this bean inside RateLimitConfig.java (same class, additional bean)

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

// ... inside RateLimitConfig class:

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> retryAfterFilter() {
        FilterRegistrationBean<OncePerRequestFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                chain.doFilter(request, response);
                if (response.getStatus() == 429) {
                    // Inform the client to retry after 60 seconds (conservative default)
                    response.setHeader("Retry-After", "60");
                }
            }
        });
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Integer.MIN_VALUE + 1); // run after Bucket4j filter
        return bean;
    }
```

### 5. Test the rate limit manually

Start the backend:

```powershell
cd backend; mvn spring-boot:run
```

Send 11 consecutive login requests from the same IP. The 11th should return `429`:

```powershell
# Send 11 requests in a tight loop
for ($i = 1; $i -le 11; $i++) {
    $resp = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body '{"identifier":"nobody","password":"wrong"}' `
        -SkipHttpErrorCheck
    Write-Host "Request $i : HTTP $($resp.StatusCode)"
}
```

Expected output:

```
Request 1  : HTTP 401
Request 2  : HTTP 401
...
Request 10 : HTTP 401
Request 11 : HTTP 429
```

Also confirm the `Retry-After` header is present on request 11:

```powershell
$resp.Headers["Retry-After"]
# Expected: 60
```

---

## Checklist

- [ ] Add `bucket4j-spring-boot-starter` dependency to `pom.xml`
  - [ ] Also add `caffeine` and `spring-boot-starter-cache` if not already present
  - [ ] `mvn dependency:resolve` completes without error
- [ ] Configure rate limits per IP:
  - [ ] `/api/v1/auth/register` → 5 req / 10 min
  - [ ] `/api/v1/auth/login` → 10 req / 1 min
  - [ ] All other endpoints → 200 req / 1 min
  - [ ] Filters ordered most-specific first in `application.yml`
- [ ] Return `429 Too Many Requests` with `Retry-After` header on limit exceeded
  - [ ] `http-status-code: 429` in Bucket4j config
  - [ ] `Retry-After` header added by `retryAfterFilter` bean
  - [ ] Response body follows the project's `ApiResponse` error format

---

## How to Verify

**11th login attempt returns 429:**

```powershell
for ($i = 1; $i -le 11; $i++) {
    $r = Invoke-WebRequest "http://localhost:8080/api/v1/auth/login" `
        -Method POST -ContentType "application/json" `
        -Body '{"identifier":"x","password":"y"}' -SkipHttpErrorCheck
    if ($i -eq 11) {
        if ($r.StatusCode -eq 429) {
            Write-Host "PASS: 11th request returned 429"
        } else {
            Write-Host "FAIL: expected 429, got $($r.StatusCode)"
        }
        Write-Host "Retry-After: $($r.Headers['Retry-After'])"
    }
}
```

**Passing result:**

```
PASS: 11th request returned 429
Retry-After: 60
```

**Registration limit (6th request returns 429):**

```powershell
for ($i = 1; $i -le 6; $i++) {
    $r = Invoke-WebRequest "http://localhost:8080/api/v1/auth/register" `
        -Method POST -ContentType "application/json" `
        -Body '{"username":"u","email":"u@test.com","password":"pass","fullName":"U"}' `
        -SkipHttpErrorCheck
    Write-Host "Request $i : $($r.StatusCode)"
}
# Expected: 1-5 → 409 or 400 (business errors); 6 → 429
```

---

## Notes / Gotchas

**The rate limit resets after the window, not on success.**
If you send 10 failed login attempts and then wait 60 seconds, the bucket refills and you can try again. This is by design — the goal is to slow down, not permanently block, an IP.

**Localhost loopback may appear as a different IP in tests.**
When running locally, all requests come from `127.0.0.1`. This means your 11-request test burns the real bucket for that IP. Wait 60 seconds between test runs, or change the limit temporarily to a higher value during development.

**`refill-speed: intervally` vs `greedy`:**
- `intervally` — tokens refill all at once at the end of the window (e.g., 10 tokens added at the 60-second mark). This creates a "hard window" where bursting is possible right after the reset.
- `greedy` — tokens refill continuously at a constant rate (e.g., 1 token every 6 seconds for a 10/min limit). Smoother and harder to game with burst timing.

Use `intervally` for auth endpoints (simpler reasoning) and `greedy` for the general API (fairness).

**Rate limiting behind a reverse proxy:**
If the app is behind nginx or AWS ALB in production, the `X-Forwarded-For` header carries the real client IP. Bucket4j-Spring reads `request.getRemoteAddr()` by default, which will be the proxy's IP — causing all traffic to share one bucket. Enable `server.forward-headers-strategy=FRAMEWORK` in `application-prod.yml` (see [TASK-10.20](TASK-10.20-https-tls.md)) so Spring unwraps `X-Forwarded-For` before the rate-limit filter sees the request.

**In-memory buckets are not shared across instances.**
Caffeine is in-process. If you run two backend instances, each has its own buckets and the effective limit doubles. For production with multiple instances, replace Caffeine with a Redis-backed Bucket4j store (`bucket4j-redis`). That migration is out of scope here but is the natural follow-up once [TASK-10.3 (Redis caching)](TASK-10.3-redis-caching.md) is done.

**Reference docs:**
- [Bucket4j Spring Boot Starter — GitHub](https://github.com/MarcGiffing/bucket4j-spring-boot-starter)
- [OWASP — Blocking Brute Force Attacks](https://owasp.org/www-community/controls/Blocking_Brute_Force_Attacks)
- [RFC 6585 — 429 Too Many Requests](https://www.rfc-editor.org/rfc/rfc6585)

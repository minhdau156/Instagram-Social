# TASK-10.16 — JWT hardening

## Overview

Strengthen the JWT implementation in two ways. First, confirm (and enforce) that access tokens expire in 15 minutes and refresh tokens in 7 days. Second, upgrade from the current HMAC-SHA256 (HS256) symmetric algorithm to RSA-SHA256 (RS256) asymmetric signing — this lets you verify tokens without ever exposing the private key. Third, implement refresh token rotation so that every call to `/api/v1/auth/refresh` invalidates the old refresh token and issues a new one, shrinking the window of a stolen token to a single use.

---

## Level

**Core** — Pairs with [TASK-10.15 (Secrets hygiene)](TASK-10.15-secrets-hygiene.md), which ensures the private key is stored in an environment variable and not committed to the repository.

---

## Why

The current `JwtTokenProvider` signs tokens with HMAC-SHA256 using a single shared secret. If that secret leaks — or if the algorithm has a weakness — every token ever issued is compromised, and there is no way to verify a token without possessing the full secret. Asymmetric signing (RS256) separates the signing key (private, server-only) from the verification key (public, shareable), so a verification endpoint or a third-party service can validate tokens without touching the private key. Combined with short access token lifetimes (15 minutes) and single-use refresh tokens, a captured token has a strictly bounded impact: at most 15 minutes of access, and the first refresh invalidates it permanently.

---

## Prerequisites

- [TASK-10.15 (Secrets hygiene)](TASK-10.15-secrets-hygiene.md) — the private key must live in an environment variable.
- The `jjwt-api`, `jjwt-impl`, and `jjwt-jackson` libraries (version 0.12.6) are already in `pom.xml` — they support RS256.
- The current token infrastructure: `JwtTokenProvider` at `infrastructure/security/`, `TokenPort` in `domain/port/out/`, `RefreshTokenUseCase` in `domain/port/in/`.
- **Concept gloss:**
  - **HS256 (HMAC-SHA256)** — symmetric: the same secret is used to both sign and verify. Anyone who can verify can also forge.
  - **RS256 (RSA-SHA256)** — asymmetric: a private key signs; the corresponding public key verifies. Only the holder of the private key can sign.
  - **Refresh token rotation** — after every successful refresh, the used refresh token is invalidated and a new one is issued. A stolen token can only be used once before it becomes invalid.
  - **`java.security.KeyFactory`** — the standard Java API for constructing RSA key objects from raw bytes.

---

## Files to Create / Modify

```
backend/src/main/java/com/instagram/infrastructure/security/JwtTokenProvider.java   (modify)
backend/src/main/resources/application.yml                                            (modify)
backend/src/main/resources/application-local.yml                                      (modify)
.env.example                                                                           (modify — add RSA key vars)
```

If refresh token rotation is not already implemented (see Step 4):

```
backend/src/main/java/com/instagram/domain/service/UserService.java                   (modify — verify rotation logic)
```

---

## Step-by-Step

### 1. Verify current expiry values

Open `backend/src/main/resources/application.yml`. The current configuration is:

```yaml
app:
  jwt:
    secret: ${JWT_SECRET}
    access-token-expiry-ms: 900000     # 15 minutes — correct
    refresh-token-expiry-ms: 604800000 # 7 days — correct
```

The expiry values are already correct. Confirm that `JwtTokenProvider` reads them via `@Value` and uses them in `generateAccessToken` and `generateRefreshToken`. No change needed here.

### 2. Generate an RSA-2048 key pair

Generate a key pair locally. You need both the private key (for signing) and the public key (for verification). Use `openssl` if available, or the Java `keytool`:

**Option A — `openssl` (preferred, available in Git Bash or WSL):**

```bash
# Generate private key (PEM, PKCS#8 format)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private_key.pem

# Extract the public key
openssl rsa -pubout -in private_key.pem -out public_key.pem

# Convert both to single-line Base64 for use as env vars
# Private key (strip PEM headers and newlines):
cat private_key.pem | grep -v "\-\-\-" | tr -d '\n'

# Public key:
cat public_key.pem | grep -v "\-\-\-" | tr -d '\n'
```

**Option B — PowerShell (no openssl):**

```powershell
Add-Type -AssemblyName System.Security
$rsa = [System.Security.Cryptography.RSA]::Create(2048)

# Export private key as PKCS#8 base64
$privateKeyBytes = $rsa.ExportPkcs8PrivateKey()
$privateKeyB64   = [Convert]::ToBase64String($privateKeyBytes)

# Export public key as SPKI base64
$publicKeyBytes  = $rsa.ExportSubjectPublicKeyInfo()
$publicKeyB64    = [Convert]::ToBase64String($publicKeyBytes)

Write-Host "JWT_RSA_PRIVATE_KEY=$privateKeyB64"
Write-Host "JWT_RSA_PUBLIC_KEY=$publicKeyB64"
```

Copy the two Base64 strings into `backend/.env`:

```dotenv
JWT_RSA_PRIVATE_KEY=<base64-encoded-pkcs8-private-key>
JWT_RSA_PUBLIC_KEY=<base64-encoded-spki-public-key>
```

Do NOT add them to `application.yml` or any committed file. Add placeholder lines to `.env.example`:

```dotenv
# RSA key pair for JWT RS256 signing
# Generate with: openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | base64 -w0
JWT_RSA_PRIVATE_KEY=<base64-pkcs8-private-key>
JWT_RSA_PUBLIC_KEY=<base64-spki-public-key>
```

### 3. Update `application.yml` to reference the new key variables

Remove the `app.jwt.secret` line (no longer needed for RS256) and reference the two new variables:

```yaml
app:
  jwt:
    rsa-private-key: ${JWT_RSA_PRIVATE_KEY}
    rsa-public-key:  ${JWT_RSA_PUBLIC_KEY}
    access-token-expiry-ms:  900000     # 15 minutes
    refresh-token-expiry-ms: 604800000  # 7 days
```

### 4. Rewrite `JwtTokenProvider` to use RS256

Replace the HMAC key initialization with RSA key loading. The `jjwt` library supports RS256 natively:

```java
package com.instagram.infrastructure.security;

import com.instagram.domain.port.out.TokenPort;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtTokenProvider implements TokenPort {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${app.jwt.rsa-private-key}")
    private String rsaPrivateKeyBase64;

    @Value("${app.jwt.rsa-public-key}")
    private String rsaPublicKeyBase64;

    @Value("${app.jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    private PrivateKey privateKey;
    private PublicKey  publicKey;

    @PostConstruct
    public void init() {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");

            byte[] privateBytes = Decoders.BASE64.decode(rsaPrivateKeyBase64);
            privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));

            byte[] publicBytes = Decoders.BASE64.decode(rsaPublicKeyBase64);
            publicKey = kf.generatePublic(new X509EncodedKeySpec(publicBytes));

        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA key pair for JWT signing", e);
        }
    }

    @Override
    public String generateAccessToken(UUID userId, String role) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiryMs))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    @Override
    public String generateRefreshToken(UUID userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiryMs))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    @Override
    public Optional<UUID> validateAccessToken(String token) {
        return parseSubject(token);
    }

    @Override
    public Optional<UUID> validateRefreshToken(String token) {
        return parseSubject(token);
    }

    private Optional<UUID> parseSubject(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(UUID.fromString(subject));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
```

### 5. Verify refresh token rotation is in place

Refresh token rotation means: when the client calls `POST /api/v1/auth/refresh`, the old refresh token stored in the database is invalidated and a new one is issued in the same response.

Check the `RefreshTokenUseCase` implementation (likely in a service class). The correct pattern is:

```java
// Pseudocode — adapt to the actual service class name
public AuthResult refreshToken(RefreshTokenUseCase.Command command) {
    // 1. Validate the incoming refresh token cryptographically
    UUID userId = tokenPort.validateRefreshToken(command.refreshToken())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

    // 2. Look up the stored token and confirm it has not already been used/revoked
    RefreshToken stored = refreshTokenRepository.findByToken(command.refreshToken())
            .orElseThrow(() -> new InvalidCredentialsException("Refresh token not found or revoked"));

    // 3. Invalidate the old token (delete or mark revoked)
    refreshTokenRepository.delete(stored);

    // 4. Issue a new access token AND a new refresh token
    String newAccessToken  = tokenPort.generateAccessToken(userId, stored.role());
    String newRefreshToken = tokenPort.generateRefreshToken(userId);
    refreshTokenRepository.save(new RefreshToken(userId, newRefreshToken, ...));

    return new AuthResult(newAccessToken, newRefreshToken);
}
```

If the current implementation reuses the same refresh token across multiple refresh calls (i.e. it does not delete the old one), add the delete step. The `RefreshToken` persistence model already exists from TASK-1.x — search for it with:

```powershell
Get-ChildItem -Recurse -Filter "*.java" "backend\src" | `
    Select-String -Pattern "RefreshToken" | `
    Select-Object Path, LineNumber, Line | Format-Table -AutoSize
```

### 6. Start the backend and confirm RS256 tokens are issued

```powershell
cd backend; mvn spring-boot:run
```

Login via the API:

```powershell
$response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" `
    -Method POST `
    -ContentType "application/json" `
    -Body '{"identifier":"testuser","password":"password"}'

$token = $response.data.accessToken
Write-Host $token
```

Paste the token into [jwt.io](https://jwt.io) and confirm:

- **Header** shows `"alg": "RS256"` (not `"HS256"`).
- **Payload** shows `"exp"` roughly 15 minutes from now (check with `[DateTimeOffset]::FromUnixTimeSeconds($exp)`).

---

## Checklist

- [ ] Verify access token expiry = 15 min, refresh token expiry = 7 days in `JwtTokenProvider`
  - [ ] `access-token-expiry-ms: 900000` in `application.yml`
  - [ ] `refresh-token-expiry-ms: 604800000` in `application.yml`
- [ ] Use RS256 (asymmetric) key pair instead of HS256 if not already done; store private key in env var
  - [ ] RSA-2048 key pair generated
  - [ ] `JWT_RSA_PRIVATE_KEY` and `JWT_RSA_PUBLIC_KEY` in `.env` (not committed)
  - [ ] `.env.example` updated with placeholder lines for both keys
  - [ ] `JwtTokenProvider` reads `rsa-private-key` / `rsa-public-key` from `application.yml`
  - [ ] `@PostConstruct` loads `PrivateKey` and `PublicKey` via `KeyFactory`
  - [ ] `generateAccessToken` and `generateRefreshToken` use `Jwts.SIG.RS256`
  - [ ] `validateAccessToken` and `validateRefreshToken` use `publicKey` for verification
  - [ ] Token decoded at jwt.io shows `"alg": "RS256"`
- [ ] Implement refresh token rotation — invalidate old token on each `/auth/refresh` call
  - [ ] `RefreshTokenUseCase` implementation deletes the old token before issuing a new one
  - [ ] A second call with the same refresh token returns `401` (token already consumed)

---

## How to Verify

**RS256 algorithm in token header:**

```powershell
$body = '{"identifier":"testuser","password":"testpassword"}'
$resp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" `
    -Method POST -ContentType "application/json" -Body $body
$tokenParts = $resp.data.accessToken -split "\."
$header = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(
    ($tokenParts[0].PadRight(($tokenParts[0].Length + 3) -band -bnot 3, '='))))
Write-Host $header
# Expected: {"alg":"RS256","typ":"JWT"}
```

**15-minute expiry:**

```powershell
$payload = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(
    ($tokenParts[1].PadRight(($tokenParts[1].Length + 3) -band -bnot 3, '='))))
$exp = ($payload | ConvertFrom-Json).exp
$expiresAt = [DateTimeOffset]::FromUnixTimeSeconds($exp).ToLocalTime()
$minutesFromNow = ($expiresAt - [DateTimeOffset]::Now).TotalMinutes
Write-Host "Token expires at $expiresAt ($minutesFromNow minutes from now)"
# Expected: approx 15 minutes
```

**Refresh token rotation (single-use):**

```powershell
# Call refresh once — should succeed
$r1 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/refresh" `
    -Method POST -ContentType "application/json" `
    -Body "{`"refreshToken`": `"$($resp.data.refreshToken)`"}"

# Call refresh again with the SAME original token — must fail with 401
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/refresh" `
        -Method POST -ContentType "application/json" `
        -Body "{`"refreshToken`": `"$($resp.data.refreshToken)`"}"
} catch {
    Write-Host "Second refresh rejected: $($_.Exception.Response.StatusCode)"
    # Expected: 401
}
```

---

## Notes / Gotchas

**"My app won't start after removing `app.jwt.secret`."**
Spring throws `IllegalArgumentException: Could not resolve placeholder '${JWT_RSA_PRIVATE_KEY}'` if the env var is not set. Ensure your `backend/.env` has the two new keys and that the `spring-dotenv` library is loading it (it loads from the working directory where you run `mvn`).

**Base64 encoding gotcha: standard vs. URL-safe.**
`jjwt` uses `io.jsonwebtoken.io.Decoders.BASE64` which expects standard Base64 (with `+` and `/`, NOT the URL-safe `-` and `_` variant). Ensure the output of `openssl` / PowerShell `Convert.ToBase64String` uses standard Base64. If you accidentally use the URL-safe variant you will get a `DecodingException` at startup.

**Do not use PEM file paths in `application.yml`.**
Referencing a file path like `classpath:private_key.pem` would require committing the key to the `resources` folder. Always pass the key as a Base64 environment variable.

**HS256 → RS256 is a breaking change for existing tokens.**
Tokens signed with the old HS256 key will be rejected by the RS256 verifier. This means all users are effectively logged out on the first deployment after this change. Consider scheduling a maintenance window or issuing a one-time "force re-login" notification.

**Key rotation (future):**
The public key is now the verification authority. If you ever rotate the key pair, old tokens become unverifiable. A multi-key verifier (try the new key first, then fall back to the old one during a grace period) can smooth rotation. That is out of scope here but worth knowing.

**Reference docs:**
- [OWASP JWT Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
- [jjwt library documentation](https://github.com/jwtk/jjwt)
- [Spring Security: JSON Web Tokens](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **What a JWT is** — header, claims, signature — https://jwt.io/introduction
- **JWT security best practices** — algorithm pinning, expiry, validation pitfalls — https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html
- **Access vs refresh tokens & rotation** — short-lived access, rotating refresh — https://auth0.com/docs/secure/tokens/refresh-tokens
- **RFC 7519** — the JWT specification itself — https://datatracker.ietf.org/doc/html/rfc7519

### Official docs (code reference)
- **Spring Security OAuth2 Resource Server (JWT)** — https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- **jjwt library** — https://github.com/jwtk/jjwt

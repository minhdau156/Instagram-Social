# TASK-10.20 — HTTPS / TLS

## Overview

Configure the backend to handle production traffic correctly when it runs behind a TLS-terminating reverse proxy (nginx or AWS ALB). The proxy handles the certificate; the backend just needs to trust the `X-Forwarded-Proto` header the proxy injects, and respond with a `Strict-Transport-Security` header that tells browsers to use HTTPS exclusively. You will also create a concise infrastructure document explaining the nginx or ALB TLS configuration for future operators.

---

## Level

**Core** — Pairs with [TASK-10.14 (Security headers)](TASK-10.14-security-headers.md), which adds the other baseline response headers alongside HSTS.

---

## Why

Plain HTTP sends every byte of every request — including the JWT access token and the password on login — in clear text to anyone on the same network segment. On a shared Wi-Fi network (coffee shop, airport, hotel) or any network where an attacker can read traffic, this is a trivial eavesdrop. TLS encrypts the traffic so only the two endpoints can read it. The `Strict-Transport-Security` (HSTS) header goes one step further: it instructs the browser to refuse HTTP connections to this domain for up to a year, even if the user types `http://` manually. This prevents SSL-strip attacks, where an attacker downgrades an HTTPS connection to HTTP before the browser's TLS handshake completes.

---

## Prerequisites

- [TASK-10.14 (Security headers)](TASK-10.14-security-headers.md) — the `.headers(...)` block in `SecurityConfig` is already open; you will add HSTS there.
- Familiarity with `application-prod.yml` at `backend/src/main/resources/application-prod.yml`.
- **Concept gloss:**
  - **TLS termination** — the reverse proxy (nginx or ALB) decrypts the HTTPS traffic and forwards plain HTTP to the backend. The backend never handles TLS certificates directly.
  - **`X-Forwarded-Proto`** — a header the proxy adds to indicate whether the original request was `http` or `https`. Without it the backend always sees `http` and may generate incorrect redirect URLs.
  - **`server.forward-headers-strategy=FRAMEWORK`** — tells Spring Boot to process `X-Forwarded-*` headers and use them for scheme, host, and port. Required for correct behaviour behind a proxy.
  - **HSTS (`Strict-Transport-Security`)** — the browser stores this header and refuses to make HTTP connections to the domain for the specified `max-age`, even if the user types `http://`.

---

## Files to Create / Modify

```
backend/src/main/resources/application-prod.yml                                  (modify)
backend/src/main/java/com/instagram/infrastructure/security/SecurityConfig.java  (modify — add HSTS)
docs/infra/tls-setup.md                                                           (new)
```

---

## Step-by-Step

### 1. Add `forward-headers-strategy` to `application-prod.yml`

Open `backend/src/main/resources/application-prod.yml`. Add the following under `server:` (create the block if it doesn't exist):

```yaml
server:
  # Tell Spring Boot to trust X-Forwarded-Proto / X-Forwarded-For
  # injected by nginx or AWS ALB.
  # Required when TLS is terminated at the proxy, not the app server.
  forward-headers-strategy: FRAMEWORK
```

The complete `application-prod.yml` after this change:

```yaml
server:
  forward-headers-strategy: FRAMEWORK

spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
  data:
    redis:
      url: ${REDIS_URL}
  jpa:
    show-sql: false

app:
  storage:
    minio:
      url: ${MINIO_URL}
      access-key: ${MINIO_ACCESS_KEY}
      secret-key: ${MINIO_SECRET_KEY}
      bucket: ${MINIO_BUCKET}

  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS}

logging:
  level:
    root: WARN
```

### 2. Add the HSTS header in `SecurityConfig`

Open `SecurityConfig.java`. Inside the `.headers(...)` block you created in [TASK-10.14](TASK-10.14-security-headers.md), add the HSTS customizer:

```java
http
    .headers(headers -> headers
        .contentTypeOptions(Customizer.withDefaults())
        .frameOptions(frame -> frame.deny())
        .contentSecurityPolicy(csp -> csp
            .policyDirectives(
                "default-src 'self'; script-src 'self'; " +
                "frame-ancestors 'none'; object-src 'none'"
            )
        )
        // HSTS — only add after TLS is confirmed in production.
        // Set includeSubDomains only if ALL subdomains also serve HTTPS.
        .httpStrictTransportSecurity(hsts -> hsts
            .maxAgeInSeconds(31536000)   // 1 year — standard recommendation
            .includeSubDomains(true)
            .preload(false)              // submit to the browser preload list only after stable
        )
    )
    // ... rest of the chain unchanged
```

> **Warning:** HSTS sent over plain HTTP will break local development. Spring Security will only emit the `Strict-Transport-Security` header when the request is over HTTPS — which means behind the proxy in production, or when the app is directly configured with a TLS keystore. In the local dev environment (HTTP on `localhost:8080`), Spring Security suppresses the header automatically, so this change is safe to deploy.

### 3. Create `docs/infra/tls-setup.md`

Create the directory if it doesn't exist:

```powershell
New-Item -ItemType Directory -Force -Path "docs\infra"
```

Create `docs/infra/tls-setup.md`:

```markdown
# TLS Setup

## Overview

TLS is terminated at the reverse proxy layer. The backend (`localhost:8080` inside the container network) receives plain HTTP. The proxy injects `X-Forwarded-Proto: https` so the backend can build correct redirect URLs. The backend responds with `Strict-Transport-Security` to force browsers into HTTPS for future requests.

---

## Option A — nginx (self-managed VM / Docker)

### Prerequisites

- A domain name with a DNS A record pointing to the server.
- `certbot` installed (Let's Encrypt client).

### Steps

1. Obtain a certificate:

   ```bash
   certbot certonly --standalone -d api.yourdomain.com
   # Certificates land in /etc/letsencrypt/live/api.yourdomain.com/
   ```

2. Create `/etc/nginx/sites-available/api`:

   ```nginx
   server {
       listen 80;
       server_name api.yourdomain.com;
       # Redirect all HTTP traffic to HTTPS
       return 301 https://$host$request_uri;
   }

   server {
       listen 443 ssl http2;
       server_name api.yourdomain.com;

       ssl_certificate     /etc/letsencrypt/live/api.yourdomain.com/fullchain.pem;
       ssl_certificate_key /etc/letsencrypt/live/api.yourdomain.com/privkey.pem;

       # Modern TLS settings (generated by ssl-config.mozilla.org)
       ssl_protocols       TLSv1.2 TLSv1.3;
       ssl_ciphers         ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:...;
       ssl_prefer_server_ciphers off;

       location / {
           proxy_pass         http://backend:8080;
           proxy_set_header   Host              $host;
           proxy_set_header   X-Real-IP         $remote_addr;
           proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
           proxy_set_header   X-Forwarded-Proto $scheme;
       }
   }
   ```

3. Enable and reload:

   ```bash
   ln -s /etc/nginx/sites-available/api /etc/nginx/sites-enabled/
   nginx -t && systemctl reload nginx
   ```

4. Set up auto-renewal:

   ```bash
   certbot renew --dry-run   # verify renewal works
   # certbot installs a systemd timer / cron job automatically
   ```

---

## Option B — AWS Application Load Balancer (ALB)

1. **Provision a certificate** in AWS Certificate Manager (ACM) for your domain. ACM handles auto-renewal for free.

2. **Create an HTTPS listener** on the ALB:
   - Protocol: HTTPS, Port: 443.
   - Default SSL certificate: the ACM certificate.
   - Default action: forward to the backend target group (port 8080).

3. **Add an HTTP → HTTPS redirect listener** (Port 80):
   - Protocol: HTTP, Port: 80.
   - Default action: Redirect to HTTPS (301).

4. **ALB injects `X-Forwarded-Proto: https`** automatically — no extra configuration needed.

5. **Security group**: allow port 443 (and 80 for redirect) from `0.0.0.0/0` on the ALB. Backend instances only need port 8080 open to the ALB's security group.

---

## Verification

After deployment, check the HSTS header:

```bash
curl -si https://api.yourdomain.com/actuator/health | grep -i strict
# Expected: strict-transport-security: max-age=31536000; includeSubDomains
```

Check that `X-Forwarded-Proto` is being read correctly (no incorrect HTTP → HTTPS redirect loop):

```bash
curl -si https://api.yourdomain.com/api/v1/auth/login \
     -X POST -H "Content-Type: application/json" \
     -d '{"identifier":"test","password":"test"}'
# Expected: 401 (not 301 or 500)
```
```

### 4. Verify locally (simulated proxy header)

You can test the `X-Forwarded-Proto` handling locally without a real TLS setup by sending the header manually:

```powershell
curl -si http://localhost:8080/actuator/health `
     -H "X-Forwarded-Proto: https" `
     | Select-String "strict-transport-security"
```

With `forward-headers-strategy: FRAMEWORK` active in the local profile (it is currently only in `application-prod.yml`, so this test requires the `prod` profile), the response will include the HSTS header. In local development with the `local` profile, the header will be absent — which is correct.

---

## Checklist

- [ ] Document TLS termination in `docs/infra/tls-setup.md` (nginx / AWS ALB config)
  - [ ] Both nginx and ALB options documented with copy-pasteable config
  - [ ] Auto-renewal and certificate management covered
- [ ] Add `server.forward-headers-strategy=FRAMEWORK` in `application-prod.yml` for `X-Forwarded-Proto` handling
- [ ] Set `Strict-Transport-Security` header in `SecurityConfig`
  - [ ] `maxAgeInSeconds(31536000)` — 1 year
  - [ ] `includeSubDomains(true)`
  - [ ] `preload(false)` — defer preload list submission

---

## How to Verify

**HSTS header present in a production-like environment:**

```powershell
# Simulate a proxied HTTPS request by activating the prod profile and passing the header
$env:SPRING_PROFILES_ACTIVE = "prod"
# (set required prod env vars in your shell first)
cd backend; mvn spring-boot:run &

curl -si "http://localhost:8080/actuator/health" `
     -H "X-Forwarded-Proto: https" `
     | Select-String "strict-transport-security"
# Expected: strict-transport-security: max-age=31536000; includeSubDomains
```

**`X-Forwarded-Proto` is consumed (not forwarded to the response):**

The header must affect Spring's scheme detection (`request.isSecure()`) but must NOT appear in the response headers. Verify:

```powershell
curl -si "http://localhost:8080/api/v1/auth/login" `
     -X POST -H "Content-Type: application/json" `
     -H "X-Forwarded-Proto: https" `
     -d '{"identifier":"x","password":"y"}' `
     | Select-String "x-forwarded"
# Expected: no output (the header is consumed, not reflected)
```

---

## Notes / Gotchas

**Never enable HSTS on HTTP.**
The `Strict-Transport-Security` header is only meaningful over HTTPS. Sending it over HTTP means the browser will try to upgrade to HTTPS for the next year — and if HTTPS is not working, users are locked out. Spring Security suppresses HSTS automatically when the request scheme is HTTP, so this is safe as long as you only deploy with TLS termination in production.

**HSTS `preload` is irreversible for at least one year.**
If you add `preload=true` and submit your domain to the browser preload list, browsers will refuse HTTP connections to your domain even before they have seen your HSTS header — forever, until your domain is removed from the list (which can take 6–12 months). Do not enable preload until you are certain all subdomains serve HTTPS and you will never need HTTP again.

**`forward-headers-strategy: FRAMEWORK` vs `NATIVE`:**
`FRAMEWORK` — Spring processes the headers itself. Safe for most deployments.
`NATIVE` — the embedded Tomcat processes them. Required if you are using Tomcat's native connector. Use `FRAMEWORK` unless you have a specific reason to use `NATIVE`.

**Only activate this in the prod profile.**
`forward-headers-strategy: FRAMEWORK` in `application.yml` (the base config) would cause the local dev server to trust any client-sent `X-Forwarded-Proto` header, which is a security risk. Keep it in `application-prod.yml` only, where the proxy is the only source of the header.

**Related tasks:**
- [TASK-10.14 (Security headers)](TASK-10.14-security-headers.md) — adds `X-Content-Type-Options`, `X-Frame-Options`, and `Content-Security-Policy` to the same `.headers()` block.
- [TASK-10.46 (Full docker-compose.yml)](TASK-10.46-docker-compose.md) — the nginx service in Docker Compose is the local equivalent of the production proxy.

**Reference docs:**
- [OWASP Transport Layer Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html)
- [Spring Boot — Handling Behind a Proxy](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.webserver.use-behind-a-proxy-server)
- [Mozilla SSL Configuration Generator](https://ssl-config.mozilla.org/)
- [HSTS Preload List](https://hstspreload.org/)

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **How TLS works** — handshake, certificates, and what HTTPS actually protects — https://developer.mozilla.org/en-US/docs/Web/Security/Transport_Layer_Security
- **HSTS** — forcing HTTPS after the first visit — https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Strict-Transport-Security
- **Free certificates with Let's Encrypt** — automated cert issuance & renewal — https://letsencrypt.org/docs/

### Official docs (code reference)
- **Mozilla SSL Config Generator** — secure cipher/protocol configs — https://ssl-config.mozilla.org/
- **OWASP Transport Layer Security Cheat Sheet** — https://cheatsheetseries.owasp.org/cheatsheets/Transport_Layer_Security_Cheat_Sheet.html

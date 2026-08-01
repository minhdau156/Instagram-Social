# OAuth2

## 1. What is it?

**OAuth2** (Open Authorization 2.0) is an **authorization framework** — not an authentication protocol — that lets a third-party application access a user's resources on another service **without ever seeing the user's password**.

Instead of handing out credentials, the resource owner (user) grants a **client application** limited, scoped, revocable access via **tokens** issued by an **authorization server**.

Key actors (roles):
- **Resource Owner** — the user who owns the data (e.g., you, logging into Instagram-Social-).
- **Client** — the application requesting access (e.g., a frontend SPA, a mobile app, or a backend service).
- **Authorization Server** — issues access tokens after authenticating the resource owner and obtaining consent (e.g., Keycloak, Auth0, Google, your own auth service).
- **Resource Server** — hosts the protected resources/API and validates the access token on each request (e.g., your Spring Boot backend).

Key artifacts:
- **Access Token** — short-lived credential (often a JWT) sent with each API request to prove authorization.
- **Refresh Token** — long-lived credential used to obtain a new access token without re-authenticating the user.
- **Scopes** — permissions the client is requesting (e.g., `read:profile`, `write:posts`).
- **Grant Type / Flow** — the sequence of steps used to obtain a token (Authorization Code, Client Credentials, etc.).

## 2. Why use it?

- **Never share passwords** — the client app never touches the user's actual credentials; only the authorization server does.
- **Scoped, limited access** — a client only gets the specific permissions (scopes) it needs, not full account access.
- **Revocable** — access can be revoked (token expiry, refresh token invalidation) without changing the user's password.
- **Delegated access** — lets users grant third parties (or your own microservices) access to their data on their behalf.
- **Standardized & interoperable** — every major identity provider (Google, GitHub, Facebook, Keycloak) speaks OAuth2, so you don't reinvent auth.
- **Separation of concerns** — authentication/authorization logic is centralized in the authorization server, decoupled from each resource server's business logic (fits microservices architecture well).

## 3. How can you use it? — The Workflow

The most common and most secure flow is the **Authorization Code Flow** (ideally with **PKCE** for public clients like SPAs/mobile apps).

**Step-by-step workflow:**

```
User (Resource Owner)      Client App             Authorization Server        Resource Server
        |                       |                          |                        |
        | 1. Clicks "Login"     |                          |                        |
        |---------------------->|                          |                        |
        |                       | 2. Redirect to /authorize|                        |
        |                       |   (client_id, redirect_uri,                       |
        |                       |    scope, state, code_challenge)                  |
        |<--------------------------------------------------|                        |
        | 3. Logs in + consents to authorization server     |                        |
        |---------------------------------------------------->|                      |
        |                       |  4. Redirect back with `code`                     |
        |<----------------------------------------------------|                      |
        | 5. Browser forwards `code` to client's redirect_uri|                        |
        |---------------------->|                          |                        |
        |                       | 6. Exchange `code` for tokens (POST /token,       |
        |                       |    client_secret or code_verifier for PKCE)       |
        |                       |------------------------->|                        |
        |                       | 7. Returns access_token + refresh_token (+id_token)|
        |                       |<-------------------------|                        |
        |                       | 8. Calls API with `Authorization: Bearer <token>` |
        |                       |------------------------------------------------->  |
        |                       |                          | 9. Validates token     |
        |                       |                          |    (signature, exp,    |
        |                       |                          |     issuer, scope)     |
        |                       | 10. Returns protected resource                    |
        |                       |<-------------------------------------------------  |
```

**In plain steps:**
1. User clicks "Login" in the client app.
2. Client redirects the user's browser to the Authorization Server's `/authorize` endpoint with `client_id`, `redirect_uri`, `scope`, `state` (CSRF protection), and `code_challenge` (PKCE).
3. User authenticates (username/password, SSO, MFA) and consents to the requested scopes.
4. Authorization Server redirects back to the client's `redirect_uri` with a short-lived, one-time **authorization code**.
5. Client's backend exchanges that code at the `/token` endpoint (with its `client_secret` or PKCE `code_verifier`) for an **access token** (and usually a **refresh token**).
6. Client includes the access token as `Authorization: Bearer <token>` on every request to the Resource Server (your API).
7. Resource Server validates the token — signature, expiry, issuer, audience, scopes — either locally (JWT) or via introspection call to the Authorization Server.
8. When the access token expires, the client silently uses the **refresh token** to get a new access token, without bothering the user to log in again.

**Other grant types worth knowing:**
- **Client Credentials** — machine-to-machine (no user involved), e.g., one backend service calling another.
- **Resource Owner Password Credentials** — legacy, discouraged; client collects username/password directly.
- **Implicit** — deprecated; tokens returned directly in the redirect URL fragment (insecure, replaced by Auth Code + PKCE).

**Typical Spring Boot integration:**
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth-server.example.com/realms/instagram-social
```
Spring Security auto-validates incoming JWT bearer tokens against the issuer's public keys (JWKS endpoint) — you just annotate endpoints with `@PreAuthorize("hasAuthority('SCOPE_read:posts')")`.

## 4. When to use it in real life

- **"Login with Google/GitHub/Facebook"** buttons — classic third-party delegated login.
- **Microservices architecture** — one central Authorization Server (e.g., Keycloak) issues tokens; every microservice acts as a Resource Server validating the same JWT, avoiding duplicated auth logic.
- **Mobile/SPA apps** calling your backend API — Authorization Code + PKCE flow keeps tokens out of the browser's URL and avoids embedding client secrets in public clients.
- **Service-to-service (M2M) calls** — Client Credentials flow, e.g., a background job or another internal service calling your API with no human user involved.
- **API access delegation** — e.g., letting a third-party analytics tool read a user's post statistics without giving it the user's password.
- **Single Sign-On (SSO)** across multiple internal apps sharing one identity provider.
- **Fine-grained, revocable permissions** — e.g., granting a partner app `read-only` access to a user's profile, revocable at any time without a password reset.

---

## Summary

OAuth2 is an authorization framework that lets clients access protected resources on behalf of a user via short-lived, scoped tokens issued by an Authorization Server — without the client ever seeing the user's password.

- **What**: A token-based delegation framework with four roles (Resource Owner, Client, Authorization Server, Resource Server) and artifacts (access token, refresh token, scopes).
- **Why**: Avoids password sharing, enables scoped/revocable access, centralizes auth logic, and is the industry-standard way to secure APIs and enable third-party/delegated access.
- **How**: The Authorization Code flow — client redirects user to the Authorization Server → user authenticates/consents → server redirects back with a code → client exchanges the code for an access + refresh token → client calls the API with `Authorization: Bearer <token>` → Resource Server validates the token → refresh token renews access silently when it expires.
- **When**: Social login ("Login with Google"), microservices with a shared identity provider (e.g., Keycloak), mobile/SPA-to-backend API access (Auth Code + PKCE), service-to-service calls (Client Credentials), SSO across apps, and fine-grained/revocable third-party API access.

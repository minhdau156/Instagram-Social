# TASK-10.15 — Secrets hygiene check

## Overview

Audit the codebase for hard-coded secrets (database passwords, JWT keys, OAuth client secrets, MinIO credentials) and move any that are found into environment variables. Then create a `.env.example` file that documents the variables the app needs — with placeholder values only — so any new developer knows exactly what to set without ever seeing a real secret. This is a two-hour warm-up task with permanent security impact.

---

## Level

**Warm-up** — Pairs with [TASK-10.16 (JWT hardening)](TASK-10.16-jwt-hardening.md) and [TASK-10.46 (Full docker-compose.yml)](TASK-10.46-docker-compose.md).

---

## Why

A secret committed to a Git repository is leaked permanently. Even if you delete the line in a later commit, anyone with access to the history can run `git log -p` and read the original value. Public repositories are scraped by automated bots within minutes of a push; private repositories are only one compromised developer account away from the same outcome. Moving secrets to environment variables costs almost nothing and is the foundational habit every other security practice builds on.

---

## Prerequisites

- Basic familiarity with the repo layout and the `application.yml` / `application-local.yml` / `application-prod.yml` files under `backend/src/main/resources/`.
- The `spring-dotenv` library (`me.paulschwarz:spring-dotenv:4.0.0`) is already in `pom.xml` — it lets Spring Boot read a `.env` file in the working directory during local development.
- **Concept gloss:**
  - **Hard-coded secret** — a real credential value that appears literally in source code or config files tracked by Git.
  - **Environment variable** — a key/value pair set in the shell or a Docker env file at runtime; never committed.
  - **`.env` file** — a plain-text file of `KEY=value` pairs loaded at startup; **must be in `.gitignore`** and never committed.
  - **`.env.example`** — the safe, committed twin: same keys, but with placeholder values like `changeme` or `<your-jwt-secret>`.

---

## Files to Create / Modify

```
backend/src/main/resources/application.yml        (modify — replace literal defaults with env-var refs)
backend/src/main/resources/application-local.yml  (modify — confirm no real secrets)
.env.example                                       (new — at repo root, placeholder values only)
.gitignore                                         (modify — ensure .env is listed)
README.md                                          (modify — document .env.example setup)
```

---

## Step-by-Step

### 1. Search the repo for hard-coded secrets

Run a targeted search from the repo root. The goal is to find any literal credential values, not just `password` as a field name:

```powershell
# Search for common secret patterns — adjust the path to your local workspace
Select-String -Path "backend\src\main\resources\*.yml" `
    -Pattern "password\s*:\s*\S+|secret\s*:\s*\S+|key\s*:\s*\S+" `
    | Format-List Path, LineNumber, Line
```

Also search for any `.env` file that might already be tracked by Git:

```powershell
git ls-files | Select-String "\.env$"
```

If any `.env` file appears in that output, it has been committed and you must remove it from history (see the Gotchas section below).

### 2. Audit `application.yml` for literal defaults

Open `backend/src/main/resources/application.yml`. The relevant section currently looks like:

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:mwPSXOqeKWG5HhqMKV6DHbzFDWkVKOjvw5rBiT4T8+Q}   # ← real secret as fallback
    access-token-expiry-ms: 900000
    refresh-token-expiry-ms: 604800000

  minio:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}   # ← "minioadmin" is a well-known default
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
```

The syntax `${ENV_VAR:default}` means "use the env var if set, otherwise fall back to the literal". For secrets the fallback must never be a real value. Replace the JWT secret fallback with a clearly invalid placeholder that will cause a startup failure if the variable is not set — this forces the developer to set it explicitly rather than silently using an insecure key:

```yaml
app:
  jwt:
    secret: ${JWT_SECRET}          # no default — startup fails fast if missing
    access-token-expiry-ms: 900000
    refresh-token-expiry-ms: 604800000

  minio:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}   # minioadmin is acceptable for local MinIO only
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
```

For local development the app reads from a `.env` file via `spring-dotenv`, so removing the fallback will not break `mvn spring-boot:run` as long as a `.env` file is present.

### 3. Check `application-local.yml` for secrets

Open `backend/src/main/resources/application-local.yml`. Confirm it uses env-var references for any credentials (e.g. `spring.datasource.password: ${DB_PASSWORD:changeme}`). The value `changeme` is acceptable as a local-only default for the database password because it only works on a local Docker Postgres container, but make a note of it in the `.env.example`.

### 4. Create a `.env` file for local development (not committed)

Create `backend/.env` (or a `.env` at the repo root, depending on where you run `mvn`). This file is local-only and must never be committed:

```dotenv
# backend/.env  — local development only; NEVER commit this file

JWT_SECRET=local-dev-secret-replace-before-production-at-least-32-chars
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret

# MinIO (local Docker defaults are fine for dev)
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=instagram-media

# Database (local Docker defaults)
DB_URL=jdbc:postgresql://localhost:5432/instagram
DB_USERNAME=instagram
DB_PASSWORD=changeme
```

### 5. Create `.env.example` at the repo root

This file is committed. It shows the full list of required variables with clearly fake placeholder values. A new developer copies it to `.env` and fills in real values:

```dotenv
# .env.example — copy to .env and fill in real values before running locally.
# Never commit the .env file.

# ---- JWT ----
# Generate a strong secret (e.g. openssl rand -base64 32)
JWT_SECRET=<generate-a-strong-random-secret>

# ---- OAuth2 ----
GOOGLE_CLIENT_ID=<your-google-oauth2-client-id>
GOOGLE_CLIENT_SECRET=<your-google-oauth2-client-secret>

# ---- MinIO (object storage) ----
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=<minio-admin-username>
MINIO_SECRET_KEY=<minio-admin-password>
MINIO_BUCKET=instagram-media

# ---- Database ----
DATABASE_URL=jdbc:postgresql://localhost:5432/instagram
DATABASE_USERNAME=instagram
DATABASE_PASSWORD=<your-local-postgres-password>

# ---- Frontend URL (CORS allowed origin) ----
FRONTEND_URL=http://localhost:5173
```

### 6. Confirm `.env` is in `.gitignore`

Open `.gitignore` at the repo root. Check for a `.env` entry:

```powershell
Select-String -Path ".gitignore" -Pattern "^\.env"
```

If there is no match, add it:

```
# Secrets — never commit
.env
backend/.env
```

Verify:

```powershell
git check-ignore -v backend/.env
```

Expected output: `backend/.gitignore:N:.env  backend/.env` (or similar showing the file is ignored). If you see no output, the file is **not** ignored and you must add it before proceeding.

### 7. Document the setup in `README.md`

Find the local development section of `README.md` (or add one). Add a "First-time setup" step:

```markdown
### First-time local setup

1. Copy the example environment file and fill in your values:
   ```
   cp .env.example backend/.env
   ```
2. Edit `backend/.env` — at minimum set `JWT_SECRET` to a random string of at least 32 characters.
3. Start the local Docker stack (PostgreSQL + MinIO):
   ```
   docker compose up -d postgres minio
   ```
4. Run the backend:
   ```
   cd backend && mvn spring-boot:run
   ```
```

### 8. Confirm the app boots from environment variables

Start the backend with the `.env` file present and confirm it reads the `JWT_SECRET` variable correctly:

```powershell
cd backend
mvn spring-boot:run
```

Expected: no startup error about missing `JWT_SECRET`. Then rename your `.env` temporarily and confirm the failure message appears:

```powershell
Rename-Item backend/.env backend/.env.bak
cd backend; mvn spring-boot:run
# Expected: IllegalArgumentException or similar about unresolved placeholder ${JWT_SECRET}
Rename-Item backend/.env.bak backend/.env
```

---

## Checklist

- [ ] Search the repo for hard-coded secrets (DB password, JWT key)
  - [ ] Grep `application.yml`, `application-local.yml`, `application-prod.yml` for literal credential values
  - [ ] Run `git ls-files | Select-String "\.env"` to ensure no `.env` file is tracked
- [ ] Move any found secrets to environment variables
  - [ ] Replace `${JWT_SECRET:mwPSXOqeKWG5HhqMKV6DHbzFDWkVKOjvw5rBiT4T8+Q}` with `${JWT_SECRET}` in `application.yml`
  - [ ] Create `backend/.env` with real local values (not committed)
  - [ ] Confirm `.env` is listed in `.gitignore`
- [ ] Add a `.env.example` with placeholder values and document it in `README.md`
  - [ ] `.env.example` committed at repo root with dummy values for every required variable
  - [ ] `README.md` has a "First-time local setup" step that references `.env.example`

---

## How to Verify

**No secrets in tracked files:**

```powershell
# Should return zero results — no real secret values in YAML configs
git grep -n "mwPSXOqeKWG5" --  "*.yml"
git grep -n "minioadmin"   --  "*.yml"
```

Both commands must print nothing. If they print a line, the secret is still hard-coded.

**`.env` is properly ignored:**

```powershell
git check-ignore -v backend/.env
```

Must print a line containing the path. No output means it is **not** ignored.

**App boots from env vars:**

```powershell
cd backend; mvn spring-boot:run
# Check the startup log line:
# Started SocialMediaApplication in X.XXX seconds
```

**App fails without the env var (fast-fail check):**

```powershell
$env:JWT_SECRET = ""        # clear the variable
cd backend; mvn spring-boot:run 2>&1 | Select-String "JWT_SECRET|placeholder|unresolvable"
$env:JWT_SECRET = $null     # clean up
```

Expected: a Spring startup error mentioning `${JWT_SECRET}` could not be resolved.

---

## Notes / Gotchas

**"I already committed a `.env` file with real secrets."**
Deleting the file in a new commit does NOT remove it from history. You must:
1. Revoke and rotate the exposed credential immediately (generate a new JWT secret, change the DB password).
2. Use `git filter-repo` (preferred) or `git filter-branch` to rewrite history and remove the file.
3. Force-push the cleaned history.
4. Notify all collaborators to re-clone.

This is painful, which is exactly why the `.gitignore` step must be done before any `.env` file is created.

**`spring-dotenv` only loads the `.env` file during `spring-boot:run`.**
When running tests (`mvn test`), environment variables must be set in the shell or in `application-test.yml`. The test profile should not depend on a `.env` file.

**The `${VAR:default}` pattern with a non-secret default is fine for non-credentials.**
For example, `${MINIO_ENDPOINT:http://localhost:9000}` is safe because the endpoint URL is not a secret. Only actual credential values (passwords, keys, tokens) must have the default removed.

**Do not store real secrets in `application-prod.yml`.**
`application-prod.yml` is committed. It should contain only structural configuration (logging level, datasource URL format) with all credential values referencing environment variables — exactly as it does today.

**Related tasks:**
- [TASK-10.16 (JWT hardening)](TASK-10.16-jwt-hardening.md) — upgrades to RS256 and refresh token rotation; the private key also needs to live in an environment variable.
- [TASK-10.46 (Full docker-compose.yml)](TASK-10.46-docker-compose.md) — the compose file will inject environment variables from the `.env` file via `env_file:` or `environment:` — never from hard-coded values.

**Reference docs:**
- [OWASP Cheat Sheet: Secrets Management](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [GitHub: Removing sensitive data from a repository](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Secrets management** — why credentials must never live in source — https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html
- **12-Factor config** — keep secrets in the environment, not the repo — https://12factor.net/config
- **Scanning git history for leaks** — secrets stay in history even after deletion — https://github.com/gitleaks/gitleaks

### Official docs (code reference)
- **gitleaks (secret scanner)** — https://github.com/gitleaks/gitleaks
- **GitHub secret scanning** — https://docs.github.com/en/code-security/secret-scanning/about-secret-scanning

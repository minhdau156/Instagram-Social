# Implementation Plan: TASK-10.41 — `.dockerignore` files

## Overview

Add `backend/.dockerignore` and `frontend/.dockerignore` so the Docker build context stays lean once TASK-10.44/10.45 add the real Dockerfiles. Two independent, leaf-level file creations — no dependency graph, no vertical slicing needed (this is infra tooling, not a user-facing feature).

## Architecture Decisions

- **Backend `.dockerignore` extends the task doc's sample with `.env`/`.env.*` and `*.log`** — the doc's own backend sample omits `.env` (only its frontend sample has it), but `backend/.env` exists on disk in this repo with real local secrets (DB creds, JWT secret). Excluding it from the Docker build context is non-negotiable. Stray local log files (`flyway-error.log`, `flyway-clean.log`, `Ccrash-logsapp-output.log`) sitting next to `pom.xml` are excluded via `*.log` — no runtime value.
- **Frontend `.dockerignore` matches the task doc's sample as-is** — `frontend/.env.local` and `frontend/.env.example` are both already covered by the doc's `.env.*` glob.
- **Verification uses a throwaway Dockerfile, not the real ones** — TASK-10.44/10.45 haven't landed yet, so there's no real `Dockerfile` to `docker build` against. Docker tars the full build context before it even needs the Dockerfile's instructions, so a temporary one-line Dockerfile (`FROM alpine:3` + `COPY . /ctx` + `RUN ls -la /ctx`) is enough to measure the before/after context size and confirm excluded paths don't appear — then it's deleted, since it isn't part of this task's deliverables.

## Task List

### Phase 1: Both `.dockerignore` files (no dependency between them)
- [ ] Task 1: `backend/.dockerignore`
- [ ] Task 2: `frontend/.dockerignore`

### Checkpoint: Verification
- [ ] Context-size + excluded-path verification for both (via throwaway Dockerfile)
- [ ] Temporary Dockerfiles deleted, `git status` clean except the two new `.dockerignore` files
- [ ] Ready for human review / commit

## Task Detail

### Task 1 — `backend/.dockerignore`
**Description:** Create `backend/.dockerignore` next to `pom.xml`, excluding Maven build output, VCS/IDE/OS noise, test sources, and — beyond the task doc's own sample — local secrets and log files actually present in this repo.
**Content:**
```
# Maven build output — the Dockerfile re-runs mvn package inside the image
target/

# Git history — not needed in the image and may contain old secrets
.git/
.gitignore

# Documentation — no runtime value
*.md
docs/

# IDE metadata
.idea/
*.iml
.vscode/

# OS noise
.DS_Store
Thumbs.db

# Test-only config that should never leak into a prod image
src/test/

# Environment files — secrets must come from Docker secrets or env vars at runtime
.env
.env.*

# Local log files — noise, no runtime value
*.log
```
**Acceptance criteria:**
- [ ] File exists at `backend/.dockerignore`
- [ ] Excludes `target/`, `.git/`, `src/test/`, `.env`/`.env.*`, `*.log`
**Verification:** shared verification below (Task 3).
**Dependencies:** None
**Files:** `backend/.dockerignore`
**Estimated scope:** XS (1 file)

### Task 2 — `frontend/.dockerignore`
**Description:** Create `frontend/.dockerignore` next to `package.json`, matching the task doc's sample exactly.
**Content:**
```
# Dependencies — npm ci inside the image installs a clean copy
node_modules/

# Previous build output — the Dockerfile re-runs npm run build
dist/

# Git history
.git/
.gitignore

# Documentation
*.md

# IDE metadata
.idea/
.vscode/

# OS noise
.DS_Store
Thumbs.db

# Environment files — secrets must come from Docker secrets or env vars at runtime
.env
.env.*
```
**Acceptance criteria:**
- [ ] File exists at `frontend/.dockerignore`
- [ ] Excludes `node_modules/`, `dist/`, `.env`/`.env.*`
**Verification:** shared verification below (Task 3).
**Dependencies:** None
**Files:** `frontend/.dockerignore`
**Estimated scope:** XS (1 file)

### Task 3 — Verification (throwaway Dockerfile technique)
**Description:** Since no real Dockerfile exists yet (TASK-10.44/10.45 not done), verify both `.dockerignore` files using a temporary one-line Dockerfile per side, then delete it.
**Steps:**
1. Create temporary `backend/Dockerfile`: `FROM alpine:3` / `COPY . /ctx` / `RUN ls -la /ctx`.
2. `git stash` the new `backend/.dockerignore`, `docker build -t ctx-check ./backend`, note `transferring context` size and confirm `target/`, `.git/`, `.env` appear in `/ctx`.
3. `git stash pop`, rebuild, confirm context size drops and `target/`, `.git/`, `.env`, `*.log` are absent.
4. Repeat with a temporary `frontend/Dockerfile` (same pattern), confirming `node_modules/`, `dist/`, `.env.local` are absent after.
5. Delete both temporary Dockerfiles.
**Acceptance criteria:**
- [ ] Backend context size drops noticeably with `.dockerignore` in place; `target/`, `.git/`, `.env`, `*.log` confirmed absent
- [ ] Frontend context size drops noticeably; `node_modules/`, `dist/`, `.env.local` confirmed absent
- [ ] Both temporary Dockerfiles deleted; `git status` clean except the two new `.dockerignore` files
**Dependencies:** Task 1, Task 2
**Files:** none committed (temporary Dockerfiles are scratch-only)
**Estimated scope:** XS (verification only)

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| No real Dockerfile exists yet, so the task doc's own verification steps don't directly apply | Low | Task 3 substitutes a throwaway Dockerfile, deleted afterward; real Dockerfiles stay owned by TASK-10.44/10.45 |
| `backend/.env` and stray `*.log` files are real local artifacts | Low | `.dockerignore` only keeps them out of the Docker build context — it doesn't remove them from disk or git; unrelated to this task's scope |

## Open Questions

None.

# Implementation Plan: TASK-10.47 — CI/CD: Docker Build & Push

## Overview

Add a `docker-publish` job to the existing `.github/workflows/ci.yml` that builds the backend and frontend Docker images and pushes them to GHCR (`ghcr.io/minhdau156/instagram-backend`, `ghcr.io/minhdau156/instagram-frontend`) after `backend-ci` and `frontend-ci` both pass, tagged with `sha-<short-sha>` and `latest`, and only on pushes to `main`. This is a single-file, additive change — no existing job is modified.

## Current State (verified against the repo, not the task doc)

| Piece | Status | Location |
|---|---|---|
| `backend-ci` job (`mvn verify` + Postgres service) | ✅ exists, unmodified by this task | `.github/workflows/ci.yml` |
| `frontend-ci` job (`npm ci` / `build` / `test`) | ✅ exists, unmodified by this task | `.github/workflows/ci.yml` — note: currently `run: npm run test` (no `\|\| true`), which differs from the task doc's shown "full file". Not this task's concern — left as-is. |
| `backend/Dockerfile` (TASK-10.44) | ✅ exists, multi-stage, exposes 8080 | `backend/Dockerfile` |
| `frontend/Dockerfile` (TASK-10.45) | ✅ exists, multi-stage, exposes 80, build-time `ARG VITE_API_URL` | `frontend/Dockerfile:14-15` |
| `docker-compose.yml` (TASK-10.46) | ✅ already builds/tags `instagram-backend:local` / `instagram-frontend:local`, passes `VITE_API_URL` build arg | `docker-compose.yml:128-195` |
| `docker-publish` job / GHCR / `docker/build-push-action` / `docker/metadata-action` | ❌ none of this exists anywhere in the repo yet | — |
| Repo owner / image namespace | `minhdau156` (from `git remote -v`, already lowercase — safe for GHCR, which rejects uppercase) | — |

## Architecture Decisions (deviations from the task doc)

- **Build arg is `VITE_API_URL`, not `VITE_API_BASE_URL`.** The task doc's sample workflow passes `build-args: VITE_API_BASE_URL=...`, but `frontend/Dockerfile:14` declares `ARG VITE_API_URL`, and `docker-compose.yml:183` already uses `VITE_API_URL` for local builds. Passing an arg name the Dockerfile doesn't declare doesn't fail the build — Docker just warns `InvalidDefaultArgInFrom`/"one or more build-args were not consumed" and the frontend image silently ships with an **empty** API base URL baked in. Using the doc's name verbatim would produce a broken image that still reports success. This plan uses `VITE_API_URL` to match the Dockerfile and stay consistent with how the Compose stack already builds the same image.
- **Everything else in the doc's sample job is accurate against this repo** (job names, `needs: [backend-ci, frontend-ci]`, the `if` condition, `permissions: packages: write`, image names `instagram-backend`/`instagram-frontend` matching the Compose file's local tags, `docker/login-action`, `docker/setup-buildx-action`, `docker/metadata-action`, `docker/build-push-action`, GHA layer caching). No other changes needed.
- **Existing jobs are untouched.** `backend-ci` and `frontend-ci` are left exactly as they are today (including the pre-existing `npm run test` without `|| true`) — this task only appends a new job.

## Task List

### Task 1: Add `docker-publish` job to `ci.yml`

**Description:** Append a `docker-publish` job to `.github/workflows/ci.yml` that builds and pushes both images to GHCR after both CI jobs pass, on `main` pushes only.

**Acceptance criteria:**
- [ ] New job `docker-publish` added with `needs: [backend-ci, frontend-ci]` and `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`
- [ ] Job declares `permissions: contents: read` and `permissions: packages: write`
- [ ] Logs in to `ghcr.io` via `docker/login-action@v3` using `github.actor` / `secrets.GITHUB_TOKEN` (no new secrets needed)
- [ ] Backend image built from `./backend` (`Dockerfile`), tagged `ghcr.io/minhdau156/instagram-backend:sha-<short-sha>` and `:latest` (latest only on default branch), pushed
- [ ] Frontend image built from `./frontend` (`Dockerfile`), same tag scheme, build-arg **`VITE_API_URL=http://localhost:8080`** (not `VITE_API_BASE_URL`), pushed
- [ ] `backend-ci` and `frontend-ci` job definitions are byte-for-byte unchanged

**Verification:**
- [ ] YAML is well-formed: `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))"` (or any YAML linter) succeeds
- [ ] Manual diff review: confirm only an appended job, no edits to the two existing jobs
- [ ] Manual (post-push, requires human): push to `main`, confirm in the **Actions** tab that `docker-publish` runs only after both other jobs succeed, and that a PR run does **not** show `docker-publish` at all

**Dependencies:** None

**Files:**
- `.github/workflows/ci.yml` (modify — append only)

**Estimated scope:** XS (1 file, additive)

## Checkpoint: Complete

- [ ] Acceptance criteria above all met
- [ ] `git diff` shows only an appended block in `ci.yml` — no lines removed or changed in `backend-ci`/`frontend-ci`
- [ ] Ready for human review, commit, and push (pushing to `main` triggers real GHCR pushes — this plan does not push on its own; that's a user action)

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Using the doc's `VITE_API_BASE_URL` build-arg name | High — silently ships a frontend image with no API URL baked in, discovered only at runtime | Use `VITE_API_URL`, matching `frontend/Dockerfile` and `docker-compose.yml` (documented above) |
| First push to GHCR defaults packages to **private** | Low — teammates can't `docker pull` without auth until visibility is changed | Doc's own "Notes / Gotchas" already covers this; flag it again at verification time, not a code change |
| `latest` tag being mutable | Low, by design | Doc already recommends pinning deployments to `sha-<commit>`; no plan action needed |

## Open Questions

None — the only ambiguity (build-arg name) was resolved by reading the actual Dockerfile and Compose file.

## How to Verify (manual, after commit + push to `main`)

1. `git add .github/workflows/ci.yml && git commit -m "..." && git push origin main`
2. GitHub → **Actions** tab: confirm `docker-publish` appears and starts only after `backend-ci`/`frontend-ci` both show ✓.
3. GitHub → repo **Packages** (or `https://github.com/minhdau156?tab=packages`): confirm `instagram-backend` and `instagram-frontend` packages exist with `sha-<short>` and `latest` tags.
4. Open a PR against `main` and confirm the workflow run for that PR has no `docker-publish` job at all.
5. `docker pull ghcr.io/minhdau156/instagram-backend:sha-$(git rev-parse --short HEAD)` succeeds (may require `docker login ghcr.io` first if the package is still private).

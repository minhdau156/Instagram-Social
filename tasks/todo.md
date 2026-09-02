# TODO: TASK-10.47 — CI/CD: Docker Build & Push

See `tasks/plan.md` for full detail, especially **Architecture Decisions** — the task doc's build-arg name (`VITE_API_BASE_URL`) doesn't match this repo's actual Dockerfile/Compose convention (`VITE_API_URL`); the task below uses the real one.

## Task 1: Add `docker-publish` job to `ci.yml`

- [ ] Append `docker-publish` job to `.github/workflows/ci.yml`:
  - [ ] `needs: [backend-ci, frontend-ci]`, `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`
  - [ ] `permissions: contents: read` + `packages: write`
  - [ ] `docker/login-action@v3` to `ghcr.io` using `github.actor` / `secrets.GITHUB_TOKEN`
  - [ ] `docker/setup-buildx-action@v3`
  - [ ] Backend: `docker/metadata-action@v5` + `docker/build-push-action@v6` → `ghcr.io/minhdau156/instagram-backend`, tags `sha-<short>` + `latest`, context `./backend`
  - [ ] Frontend: same, → `ghcr.io/minhdau156/instagram-frontend`, build-arg `VITE_API_URL=http://localhost:8080` (**not** `VITE_API_BASE_URL`)
  - [ ] `backend-ci` / `frontend-ci` jobs left byte-for-byte unchanged
- Files: `.github/workflows/ci.yml`
- Dependencies: None

## Checkpoint: Complete

- [ ] YAML validates (e.g. `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))"`)
- [ ] `git diff` shows only an appended block — no changes to `backend-ci`/`frontend-ci`
- [ ] Ready for human review, commit, and push
- [ ] (Manual, post-push) Actions tab: `docker-publish` runs only after both CI jobs pass, and is absent on PR runs
- [ ] (Manual, post-push) GHCR **Packages** page shows both images with `sha-<short>` and `latest` tags

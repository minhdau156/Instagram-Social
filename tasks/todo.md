# TODO: TASK-10.41 — `.dockerignore` files

See `tasks/plan.md` for full detail.

## Phase 1: Both `.dockerignore` files
- [ ] Task 1: `backend/.dockerignore`
  - Acceptance: excludes `target/`, `.git/`, `src/test/`, `.env`/`.env.*`, `*.log`
  - Files: `backend/.dockerignore`
- [ ] Task 2: `frontend/.dockerignore`
  - Acceptance: excludes `node_modules/`, `dist/`, `.env`/`.env.*`
  - Files: `frontend/.dockerignore`

## Checkpoint: Verification
- [ ] Task 3: Verify via throwaway Dockerfile (no real Dockerfile exists yet — TASK-10.44/10.45 not done)
  - Acceptance: context size drops for both after `.dockerignore` is in place; `target/`, `.git/`, `.env`, `*.log` absent from backend context; `node_modules/`, `dist/`, `.env.local` absent from frontend context
  - Verify: temporary `Dockerfile` (`FROM alpine:3` / `COPY . /ctx` / `RUN ls -la /ctx`) per side, deleted after
  - Dependencies: Task 1, Task 2

## Final Checkpoint
- [ ] Both temporary Dockerfiles deleted
- [ ] `git status` clean except the two new `.dockerignore` files
- [ ] Ready for human review / commit

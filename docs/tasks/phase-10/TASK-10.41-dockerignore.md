# TASK-10.41 — Add `.dockerignore` files

## Overview

A `.dockerignore` file tells the Docker daemon which files and directories to exclude when it assembles the build context — the snapshot of your project that gets sent to the daemon before the first `RUN` instruction executes. Without one, Docker ships your entire `backend/target/` directory (compiled classes, test reports, the fat JAR itself), `node_modules/`, and the full `.git/` history into every build, even when none of that is needed. Adding a `.dockerignore` to each sub-project is a five-minute change that makes every subsequent build meaningfully faster and prevents accidental secrets embedded in untracked files from ending up in an image layer.

## Level

**Warm-up** · Pairs with [TASK-10.44](TASK-10.44-dockerfile-backend.md) (backend Dockerfile) and [TASK-10.45](TASK-10.45-dockerfile-frontend.md) (frontend Dockerfile).

## Why

When Docker builds an image it first compresses the entire build context and streams it to the daemon. On a fresh Maven build, `backend/target/` alone can be 150–300 MB of class files, test reports, and the fat JAR — none of which the Dockerfile needs because `mvn package` inside the image recreates them from source. Likewise, `frontend/node_modules/` can be 200–400 MB and `npm ci` inside the container installs a clean, reproducible copy anyway. Every megabyte you exclude from the context is a megabyte less to copy, hash, and transfer on every `docker build` invocation. Trimming `.git/` from the context also prevents your full commit history (which may contain previously-committed secrets) from riding along inside the image filesystem.

## Prerequisites

- Docker Desktop installed and the `docker` CLI available in your terminal (verify with `docker --version`).
- [TASK-10.44](TASK-10.44-dockerfile-backend.md) and [TASK-10.45](TASK-10.45-dockerfile-frontend.md) either completed already, or drafted so you know what the Dockerfiles copy.
- **Concepts to skim:**
  - _Build context_ — the directory tree Docker snapshots and sends to the daemon; see [Docker build context docs](https://docs.docker.com/build/concepts/context/).
  - _`.dockerignore` pattern syntax_ — mirrors `.gitignore` glob syntax; `**` matches any depth.

## Files to Create / Modify

```
backend/.dockerignore     (new)
frontend/.dockerignore    (new)
```

## Step-by-Step

### 1. Create `backend/.dockerignore`

Create the file at `backend/.dockerignore` (next to `pom.xml`):

```dockerignore
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
```

**Why each entry matters:**

- `target/` — the largest exclusion; on a warm Maven build this directory is 150–300 MB.
- `.git/` — prevents the full commit history from entering the image layer.
- `*.md` / `docs/` — documentation has no runtime value.
- `src/test/` — test code and test configuration never need to be in a production image.

### 2. Create `frontend/.dockerignore`

Create the file at `frontend/.dockerignore` (next to `package.json`):

```dockerignore
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

**Why `node_modules/` matters most:** a typical React project's `node_modules/` is 200–400 MB. Including it in the build context is wasteful and can cause subtle problems if the host OS differs from the Alpine-based build container (native bindings compiled for Windows will not run on Linux).

### 3. Measure the before/after build context size

Run a build without the `.dockerignore` first (if you have not already), then again after, and compare the line that reads `[+] Building ... transferring context`.

PowerShell:

```powershell
# Build backend image — watch the "transferring context" line
docker build -t instagram-backend-test ./backend

# Build frontend image
docker build -t instagram-frontend-test ./frontend
```

Bash (if using the Git Bash terminal):

```bash
docker build -t instagram-backend-test ./backend
docker build -t instagram-frontend-test ./frontend
```

Expected output (with `.dockerignore` in place) — the context line should be well under 10 MB for the backend and well under 5 MB for the frontend:

```
[+] Building 45.2s (12/12) FINISHED
 => [internal] load build definition from Dockerfile
 => [internal] load .dockerignore
 => [internal] load build context
 => => transferring context: 2.87 MB     <--- this is the key number
```

Without `.dockerignore` the same line often reads 150 MB or more.

### 4. (Optional) Verify excluded paths did not sneak in

After building, confirm that `target/` and `node_modules/` are absent from the resulting image's filesystem:

```powershell
# For backend image — should print nothing
docker run --rm instagram-backend-test ls /target 2>&1

# For frontend image — should print nothing
docker run --rm instagram-frontend-test ls /node_modules 2>&1
```

## Checklist

- [ ] Add `backend/.dockerignore` (ignore `target/`, `.git/`, `*.md`)
- [ ] Add `frontend/.dockerignore` (ignore `node_modules/`, `dist/`, `.git/`)
- [ ] Re-run `docker build` and confirm the "transferring context" size is smaller

## How to Verify

1. Run the backend build and note the context size before and after:

   ```powershell
   docker build -t instagram-backend-test ./backend 2>&1 | Select-String "transferring context"
   ```

   Passing result: the line reports a context size that is noticeably smaller (typically drops from 150+ MB to under 5 MB on a project of this size).

2. Run the frontend build and note the context size:

   ```powershell
   docker build -t instagram-frontend-test ./frontend 2>&1 | Select-String "transferring context"
   ```

   Passing result: context size drops from 200+ MB to under 2 MB.

3. Confirm no `target/` directory ended up in the backend image:

   ```powershell
   docker run --rm instagram-backend-test sh -c "ls / | grep target"; echo "exit: $LASTEXITCODE"
   ```

   Passing result: no output (the `ls` finds nothing matching `target`).

## Notes / Gotchas

- **Pattern is relative to the directory containing the Dockerfile.** If you run `docker build -f backend/Dockerfile .` from the repo root, Docker uses the repo root as the context and the `.dockerignore` must live at the repo root too. The recommended approach (and what TASK-10.44 uses) is `docker build ./backend`, which uses `backend/` as the context and picks up `backend/.dockerignore` automatically.

- **`.env` files must be excluded.** If your `backend/` directory ever contains an `.env` with database passwords, add `.env` and `.env.*` to `backend/.dockerignore` immediately. Environment variables must be injected at container runtime, not baked into images.

- **`target/` vs `.gitignore`.** Docker does not read `.gitignore` — it reads only `.dockerignore`. The two files are independent; you need both.

- **Forgetting to save before re-building** is a common beginner mistake on Windows. Ensure the file is saved; VS Code shows unsaved files with a dot in the tab.

- Related tasks: [TASK-10.44](TASK-10.44-dockerfile-backend.md), [TASK-10.45](TASK-10.45-dockerfile-frontend.md), [TASK-10.46](TASK-10.46-docker-compose.md).

- Official reference: [Docker `.dockerignore` file](https://docs.docker.com/reference/dockerfile/#dockerignore-file).

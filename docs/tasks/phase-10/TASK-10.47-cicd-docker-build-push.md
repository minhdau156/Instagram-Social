# TASK-10.47 — CI/CD: Docker build & push

## Overview

Extend the existing GitHub Actions workflow (`.github/workflows/ci.yml`) to add a new job that builds the backend and frontend Docker images and pushes them to the GitHub Container Registry (GHCR) after all tests pass. Images are tagged with both the commit SHA (`sha-<commit>`) for traceability and `latest` on main branch pushes for convenience. The result is a fully automated pipeline: every merge to `main` produces a deployable, versioned artifact in the registry with zero manual steps.

## Level

**Core** · Pairs with [TASK-10.44](TASK-10.44-dockerfile-backend.md) (backend Dockerfile), [TASK-10.45](TASK-10.45-dockerfile-frontend.md) (frontend Dockerfile), and [TASK-10.46](TASK-10.46-docker-compose.md) (full Compose stack).

## Why

Building Docker images locally and pushing them by hand works once. It breaks the first time someone forgets, pushes the wrong branch, uses a different tag format, or leaves their laptop in a bag. Automating the build and push in CI means every commit to `main` is automatically packaged and stored in a registry, tagged with the commit SHA so you can always trace `ghcr.io/org/instagram-backend:sha-8926a0c` back to the exact source code that produced it. The `latest` tag gives deployment tooling an easy target. And because the build job only runs after tests pass, you will never push a broken image.

## Prerequisites

- A GitHub repository with the existing `ci.yml` workflow in `.github/workflows/`.
- `backend/Dockerfile` created ([TASK-10.44](TASK-10.44-dockerfile-backend.md)).
- `frontend/Dockerfile` created ([TASK-10.45](TASK-10.45-dockerfile-frontend.md)).
- GitHub account with write access to the repository. GHCR uses the repository's built-in `GITHUB_TOKEN` — no extra configuration is needed in the repo settings for pushing to GHCR under the same organisation.
- **Concepts to skim:**
  - _GitHub Container Registry (GHCR)_ — a Docker-compatible registry at `ghcr.io` attached to your GitHub account or organisation. Images are namespaced as `ghcr.io/<owner>/<image-name>`. See [GHCR documentation](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).
  - _`docker/build-push-action`_ — the official GitHub Action for building and pushing Docker images, with BuildKit and layer caching built in. See [docker/build-push-action](https://github.com/docker/build-push-action).
  - _`docker/metadata-action`_ — generates tags and labels from Git context (branch names, tags, SHAs). Avoids manually constructing tag strings.
  - _`GITHUB_TOKEN`_ — a short-lived token automatically injected into every workflow run. It has read/write access to the repository's packages (including GHCR) without any secrets configuration.

## Files to Create / Modify

```
.github/workflows/ci.yml    (modify — add docker-publish job)
```

## Step-by-Step

### 1. Understand the existing workflow structure

The current `ci.yml` has two jobs:

- `backend-ci` — runs `mvn verify` against a Postgres service container.
- `frontend-ci` — runs `npm ci`, `npm run build`, and `npm run test`.

The new `docker-publish` job will run **after both jobs pass** using `needs: [backend-ci, frontend-ci]`, and only on pushes to `main`.

### 2. Add the `docker-publish` job to `ci.yml`

Open `.github/workflows/ci.yml` and append the new job. The full updated file is shown below (existing jobs retained; new job added at the bottom):

```yaml
name: CI Pipeline

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  backend-ci:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_USER: instagram
          POSTGRES_PASSWORD: changeme
          POSTGRES_DB: instagram
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Cache Maven packages
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
          restore-keys: ${{ runner.os }}-m2

      - name: Build and verify backend
        working-directory: ./backend
        run: mvn verify

  frontend-ci:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Node.js 20
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json

      - name: Install dependencies
        working-directory: ./frontend
        run: npm ci

      - name: Build frontend
        working-directory: ./frontend
        run: npm run build

      - name: Run unit tests
        working-directory: ./frontend
        run: npm run test || true

  # ── Docker build & push ───────────────────────────────────────────────────
  # Runs only after both CI jobs pass, and only on pushes to main.
  docker-publish:
    runs-on: ubuntu-latest
    needs: [backend-ci, frontend-ci]
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    permissions:
      contents: read
      packages: write   # Required to push to GHCR

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      # Log in to GHCR using the auto-injected GITHUB_TOKEN.
      # No secrets configuration is required.
      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      # Enable BuildKit's advanced caching and multi-platform support.
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      # ── Backend image ───────────────────────────────────────────────────

      - name: Extract backend image metadata
        id: backend-meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository_owner }}/instagram-backend
          tags: |
            type=sha,prefix=sha-,format=short
            type=raw,value=latest,enable={{is_default_branch}}

      - name: Build and push backend image
        uses: docker/build-push-action@v6
        with:
          context: ./backend
          file: ./backend/Dockerfile
          push: true
          tags: ${{ steps.backend-meta.outputs.tags }}
          labels: ${{ steps.backend-meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

      # ── Frontend image ──────────────────────────────────────────────────

      - name: Extract frontend image metadata
        id: frontend-meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository_owner }}/instagram-frontend
          tags: |
            type=sha,prefix=sha-,format=short
            type=raw,value=latest,enable={{is_default_branch}}

      - name: Build and push frontend image
        uses: docker/build-push-action@v6
        with:
          context: ./frontend
          file: ./frontend/Dockerfile
          push: true
          tags: ${{ steps.frontend-meta.outputs.tags }}
          labels: ${{ steps.frontend-meta.outputs.labels }}
          build-args: |
            VITE_API_BASE_URL=http://localhost:8080
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

### 3. Understand the tag format

The `docker/metadata-action` step generates two tags per image:

| Tag | Example | When |
|-----|---------|------|
| `sha-<short-sha>` | `sha-8926a0c` | Every push to `main` |
| `latest` | `latest` | Only on pushes to the default branch (`main`) |

The short SHA is the first 7 characters of `github.sha` (the same value shown in GitHub's commit list). Using this format, you can always pull the exact image for any commit:

```powershell
docker pull ghcr.io/<owner>/instagram-backend:sha-8926a0c
```

### 4. Commit and push to trigger the workflow

```powershell
git add .github/workflows/ci.yml
git commit -m "chore(ci): add Docker build and push to GHCR"
git push origin main
```

### 5. Monitor the workflow run

Go to the repository on GitHub → **Actions** tab → click the running workflow. You should see three jobs:

```
backend-ci      ✓ passed
frontend-ci     ✓ passed
docker-publish  ○ running  (starts after both pass)
```

After `docker-publish` finishes, navigate to **Packages** on the repository page (or `https://github.com/<owner>?tab=packages`) to see the two new images.

### 6. Verify the pushed images

Pull the `latest` image locally and confirm it runs:

```powershell
docker pull ghcr.io/<owner>/instagram-backend:latest

docker run --rm `
  -p 8080:8080 `
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://host.docker.internal:5432/instagram" `
  -e SPRING_DATASOURCE_USERNAME="instagram" `
  -e SPRING_DATASOURCE_PASSWORD="changeme" `
  -e JWT_SECRET="replace-with-strong-secret" `
  ghcr.io/<owner>/instagram-backend:latest
```

Replace `<owner>` with your GitHub username or organisation name.

## Checklist

- [ ] Extend `.github/workflows/ci.yml`:
  - [ ] After tests pass: `docker build` backend + frontend images
  - [ ] Push to GitHub Container Registry (`ghcr.io`)
  - [ ] Tag with `sha:${{ github.sha }}` and `latest` on main branch pushes

## How to Verify

1. Push a commit to `main` and go to the **Actions** tab in GitHub.

2. Confirm the `docker-publish` job only runs after both `backend-ci` and `frontend-ci` pass.

3. After the workflow succeeds, go to **Packages** on the repository or profile page. Two packages should be visible:
   - `instagram-backend` with tags `sha-<short-sha>` and `latest`
   - `instagram-frontend` with tags `sha-<short-sha>` and `latest`

4. Pull the `sha`-tagged backend image and confirm the digest matches the commit:

   ```bash
   docker pull ghcr.io/<owner>/instagram-backend:sha-$(git rev-parse --short HEAD)
   ```

   Passing result: the pull succeeds and the image ID matches what is shown in the GitHub Packages page.

5. Confirm the job does NOT run on pull requests (only on pushes to `main`). Open a pull request against `main` and confirm the `docker-publish` job is absent from the workflow run.

## Notes / Gotchas

- **GHCR image visibility defaults to private.** After the first push you may need to navigate to the package settings on GitHub and set it to **Public** if you want teammates to pull without authentication. Go to `https://github.com/<owner>/instagram-backend` (Packages page) → Package settings → Change visibility.

- **`permissions: packages: write` is required.** Without this block the `GITHUB_TOKEN` does not have permission to push to GHCR and the login step will fail with `denied: permission_denied`. This is a repo-level permission declared in the job, not a secret.

- **`VITE_API_BASE_URL` is hardcoded to `http://localhost:8080` in the CI build.** This is intentional for this task — the image is built once and the runtime URL is usually injected at deployment time via an environment variable or a reverse proxy. For a staging deploy you would change this build arg to the staging backend URL.

- **GitHub Actions cache (`cache-from/cache-to: type=gha`).** The BuildKit GHA cache stores Docker layer cache in GitHub's Action cache storage. This dramatically speeds up repeated builds where only the source changed (the dependency download layer is reused). The cache is namespaced per branch.

- **Do not use `docker/build-push-action` with `push: true` on pull requests.** The `if: github.event_name == 'push' && github.ref == 'refs/heads/main'` condition on the job prevents the push job from running on PRs. If you remove this condition by mistake, every PR will attempt to push to GHCR, cluttering the registry with unreviewed images.

- **Tag collision with `latest`.** The `latest` tag is mutable — it always points to the most recent push to `main`. If a deployment tool pins to `latest`, it will automatically pick up the newest image on the next pull. For production deployments, prefer the immutable `sha-<commit>` tag.

- Related tasks: [TASK-10.44](TASK-10.44-dockerfile-backend.md), [TASK-10.45](TASK-10.45-dockerfile-frontend.md), [TASK-10.46](TASK-10.46-docker-compose.md), [TASK-10.42](TASK-10.42-smoke-test-script.md).

- Official reference: [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry), [docker/build-push-action](https://github.com/docker/build-push-action), [docker/metadata-action](https://github.com/docker/metadata-action).

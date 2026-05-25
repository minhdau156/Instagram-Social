# TASK-10.45 — Dockerfile: frontend

## Overview

Create a production-ready `Dockerfile` for the React frontend using a two-stage build. The first stage uses Node to install dependencies and run `npm run build` (TypeScript compile + Vite bundle), producing a `dist/` directory of static files. The second stage copies only those static files into a minimal nginx image that serves them over HTTP. An nginx configuration file is also written to handle the SPA routing fallback — so that refreshing a deep link like `/search` returns the app shell instead of a 404. The resulting image is roughly 25–40 MB: no Node runtime, no `node_modules`, no TypeScript toolchain.

## Level

**Core** · Pairs with [TASK-10.41](TASK-10.41-dockerignore.md) (`.dockerignore`), [TASK-10.44](TASK-10.44-dockerfile-backend.md) (backend Dockerfile), and [TASK-10.46](TASK-10.46-docker-compose.md) (full Compose stack).

## Why

React's output after `npm run build` is a folder of plain HTML, CSS, and JavaScript files. They do not require Node to be served — Node is a build-time dependency only. Running a Node process in production just to serve static files wastes 80+ MB of image space and keeps an entire runtime exposed on the network that is never used after startup. nginx is purpose-built for serving static files: it handles `gzip`, `Cache-Control`, and thousands of concurrent connections efficiently, and the Alpine variant of the nginx image weighs about 25 MB. The SPA routing fallback (`try_files $uri $uri/ /index.html`) is the one nginx configuration that every React/Vue/Angular app needs, because React Router handles `/search` client-side — the browser never makes a separate request for it, but a direct navigation or page refresh does.

## Prerequisites

- Docker Desktop installed; verify with `docker --version`.
- `frontend/.dockerignore` in place ([TASK-10.41](TASK-10.41-dockerignore.md)).
- `npm run build` runs successfully on your machine (`cd frontend && npm run build`). Fix any TypeScript errors before dockerising — the Dockerfile runs the same command.
- **Concepts to skim:**
  - _Multi-stage Docker builds_ — see [TASK-10.44](TASK-10.44-dockerfile-backend.md) for a detailed explanation; same principle applies here.
  - _nginx `try_files`_ — the directive that makes single-page-app routing work: `try_files $uri $uri/ /index.html` tells nginx to serve the requested file if it exists, fall back to a directory index if it exists, and otherwise serve `index.html` so React Router can handle the path.
  - _Vite build output_ — `npm run build` runs `tsc && vite build` (from `frontend/package.json`), producing `frontend/dist/`.

## Files to Create / Modify

```
frontend/Dockerfile        (new)
frontend/nginx.conf        (new)
frontend/.dockerignore     (must exist — see TASK-10.41)
```

## Step-by-Step

### 1. Create the nginx configuration file

Create `frontend/nginx.conf` (next to `package.json`). This file is copied into the nginx image during the Docker build:

```nginx
server {
    listen 80;
    server_name _;

    # Serve static files from the Vite build output directory.
    root /usr/share/nginx/html;
    index index.html;

    # SPA fallback: if the requested file or directory does not exist,
    # return index.html so React Router can render the correct component.
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Aggressive caching for hashed assets (JS, CSS, images).
    # Vite injects a content hash into the filename (e.g. main.a1b2c3.js),
    # so these files are safe to cache for a long time.
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    # Do not cache index.html — it must always return the latest version
    # so users pick up new hashed asset filenames after a deploy.
    location = /index.html {
        add_header Cache-Control "no-store, no-cache, must-revalidate";
        try_files $uri =404;
    }

    # Gzip compression for text assets.
    gzip on;
    gzip_types text/plain text/css application/javascript application/json
               image/svg+xml;
    gzip_min_length 1024;
}
```

### 2. Create `frontend/Dockerfile`

Create the file at `frontend/Dockerfile` (next to `package.json`):

```dockerfile
# ── Stage 1: build ──────────────────────────────────────────────────────────
# Use the official Node 20 Alpine image to install dependencies and build.
# This stage is discarded after the dist/ output is produced.
FROM node:20-alpine AS build

WORKDIR /app

# Copy package manifests first so the npm install layer is cached independently
# of source code changes. The cache is invalidated only when package-lock.json
# changes (i.e., a dependency was added, removed, or version-bumped).
COPY package.json package-lock.json ./

# npm ci installs exactly what package-lock.json specifies — reproducible and
# faster than npm install for CI/build environments.
RUN npm ci

# Copy the rest of the source tree.
COPY . .

# Build the production bundle.
# This runs: tsc (TypeScript compile) then vite build (bundle + minify).
RUN npm run build

# ── Stage 2: serve ──────────────────────────────────────────────────────────
# Use the minimal nginx Alpine image to serve the static output.
# No Node runtime, no node_modules, no TypeScript toolchain in the final image.
FROM nginx:alpine AS serve

# Remove the default nginx configuration.
RUN rm /etc/nginx/conf.d/default.conf

# Copy our SPA-aware nginx configuration.
COPY nginx.conf /etc/nginx/conf.d/app.conf

# Copy the built assets from the build stage.
COPY --from=build /app/dist /usr/share/nginx/html

# Document the port nginx listens on.
EXPOSE 80

# nginx starts in the foreground by default in the official image.
CMD ["nginx", "-g", "daemon off;"]
```

### 3. Build the image locally

Run from the **repo root** so the build context is `frontend/`:

```powershell
docker build -t instagram-frontend:local ./frontend
```

Bash:

```bash
docker build -t instagram-frontend:local ./frontend
```

Expected output (abbreviated):

```
[+] Building 68.4s (12/12) FINISHED
 => [internal] load build definition from Dockerfile
 => [internal] load .dockerignore
 => [internal] load build context
 => => transferring context: 1.24 MB
 => [build 1/5] FROM node:20-alpine
 => [build 2/5] WORKDIR /app
 => [build 3/5] COPY package.json package-lock.json ./
 => [build 4/5] RUN npm ci
 => [build 5/5] COPY . .
 => [build 6/5] RUN npm run build
 => [serve 1/3] FROM nginx:alpine
 => [serve 2/3] COPY nginx.conf /etc/nginx/conf.d/app.conf
 => [serve 3/3] COPY --from=build /app/dist /usr/share/nginx/html
 => exporting to image
```

### 4. Verify the image size

```powershell
docker image ls instagram-frontend:local
```

Expected output — under 50 MB:

```
REPOSITORY            TAG     IMAGE ID       CREATED          SIZE
instagram-frontend    local   f1e2d3c4b5a6   1 minute ago     37.2MB
```

### 5. Start the container and test SPA routing

```powershell
docker run --rm -d --name frontend-test -p 3000:80 instagram-frontend:local
```

Test the root:

```powershell
curl -s -o $null -w "%{http_code}" http://localhost:3000/
```

Expected: `200`

Test a deep link that only exists in React Router (not as a real file):

```powershell
curl -s -o $null -w "%{http_code}" http://localhost:3000/search
```

Expected: `200` (nginx falls back to `index.html`; React Router renders the search page)

Without the `try_files` fallback this would return `404`.

Test that hashed assets are served:

```powershell
curl -s -I http://localhost:3000/ | Select-String "Cache-Control"
```

Expected: `Cache-Control: no-store, no-cache, must-revalidate` for `index.html`.

### 6. Clean up

```powershell
docker stop frontend-test
```

## Checklist

- [ ] Create `frontend/Dockerfile` (multi-stage):
  - [ ] Stage 1 (`build`): `node:20-alpine` — `npm ci && npm run build`
  - [ ] Stage 2 (`serve`): `nginx:alpine` — copy `dist/`, configure SPA fallback in `nginx.conf`

## How to Verify

1. Build the image:

   ```powershell
   docker build -t instagram-frontend:local ./frontend
   ```

   Passing result: build completes with exit code 0. No TypeScript errors.

2. Start the container:

   ```powershell
   docker run --rm -d --name frontend-test -p 3000:80 instagram-frontend:local
   ```

3. Check the root URL:

   ```powershell
   curl -s -o $null -w "%{http_code}" http://localhost:3000/
   ```

   Passing result: `200`.

4. Check a deep link (SPA fallback test):

   ```powershell
   curl -s -o $null -w "%{http_code}" http://localhost:3000/search
   ```

   Passing result: `200`. If this returns `404`, the `try_files` line in `nginx.conf` is missing or misconfigured.

5. Open `http://localhost:3000/search` in a browser and confirm the React app loads (not a browser 404 page).

6. Confirm image size is reasonable:

   ```powershell
   docker image ls instagram-frontend:local --format "{{.Size}}"
   ```

   Passing result: under 60 MB.

7. Clean up:

   ```powershell
   docker stop frontend-test
   ```

## Notes / Gotchas

- **The `VITE_API_BASE_URL` environment variable must be set at build time**, not at runtime. Vite bakes `import.meta.env.VITE_*` variables into the bundle during `npm run build`. This means you cannot inject the backend URL at container startup — it must be known when `docker build` runs. Pass it as a build argument:

  ```powershell
  docker build `
    --build-arg VITE_API_BASE_URL=http://localhost:8080 `
    -t instagram-frontend:local ./frontend
  ```

  And in the Dockerfile, add before `RUN npm run build`:

  ```dockerfile
  ARG VITE_API_BASE_URL
  ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
  ```

  In the full Compose setup (TASK-10.46) this is typically set in the Compose file's `build.args` section.

- **`npm ci` vs `npm install`.** `npm ci` requires `package-lock.json` to exist and be in sync with `package.json`. If you see the error `npm ci can only install packages when your package.json and package-lock.json or npm-shrinkwrap.json are in sync`, run `npm install` locally and commit the updated `package-lock.json`.

- **TypeScript errors fail the build.** `npm run build` runs `tsc` first. If there are any TypeScript errors they will cause the Docker build to fail at that layer. Fix them locally with `npm run build` before committing.

- **nginx on port 80 inside the container, mapped to port 3000 on the host.** The Compose file (TASK-10.46) maps `"3000:80"`. You can choose any host port; 80 inside the container is conventional for nginx.

- **Do not use `RUN npm install` (without `ci`).** `npm install` can update `package-lock.json` inside the container, leading to different dependency versions than what the rest of the team has installed. Always use `npm ci` in Docker and CI.

- Related tasks: [TASK-10.41](TASK-10.41-dockerignore.md), [TASK-10.44](TASK-10.44-dockerfile-backend.md), [TASK-10.46](TASK-10.46-docker-compose.md), [TASK-10.47](TASK-10.47-cicd-docker-build-push.md).

- Official reference: [nginx beginner's guide](https://nginx.org/en/docs/beginners_guide.html), [Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/).

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Multi-stage build (build → serve)** — compile with Node, serve static files with nginx — https://docs.docker.com/build/building/multi-stage/
- **Deploying a Vite SPA** — building static assets for production — https://vitejs.dev/guide/static-deploy.html
- **nginx for SPA routing** — fall back to `index.html` for client-side routes — https://nginx.org/en/docs/beginners_guide.html

### Official docs (code reference)
- **Dockerfile reference** — https://docs.docker.com/reference/dockerfile/
- **nginx documentation** — https://nginx.org/en/docs/

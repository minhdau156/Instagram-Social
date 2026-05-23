# TASK-10.32 — Frontend RUM & end-to-end error tracking (Sentry)

## Overview

This task adds error tracking and real user monitoring to both the React frontend and the Spring Boot backend using Sentry. On the frontend, Sentry captures uncaught JavaScript errors, React component tree crashes, and Core Web Vitals (LCP, INP, CLS) — the metrics that measure real users' loading and interaction experience. On the backend, the Sentry Spring Boot starter captures unhandled exceptions and links them to the same trace identifier (from TASK-10.28) so a single failed API request appears as a correlated event in both the browser and server dashboards. After this task, a thrown error in the React app appears in Sentry with a source-mapped stack trace, and a backend exception lands in the same project correlated by release and trace.

---

## Level

Core · Builds on [TASK-10.28 — Distributed tracing](TASK-10.28-distributed-tracing.md)

---

## Why

Server metrics are blind to the client — a broken bundle, a slow render, or an uncaught JS exception that swallows a POST response never reaches the backend logs. You can have perfect Prometheus metrics and Zipkin traces and still not know that 20% of mobile users are hitting a `TypeError: Cannot read properties of undefined` on the profile page. RUM (Real User Monitoring) captures what real users experience — not what load test bots experience — including the Core Web Vitals that Google uses to rank pages. Error tracking with source maps means the stack trace in Sentry points to the exact TypeScript line in your editor, not the minified bundle. Correlating frontend errors to backend traces means you can click from a React error report directly to the Zipkin span for the API call that triggered it.

---

## Prerequisites

- [TASK-10.28](TASK-10.28-distributed-tracing.md) is complete — the backend generates `traceId` values and includes them in responses and logs.
- The frontend builds without errors (`npm run build` in the `frontend/` directory).
- You have a free [Sentry.io](https://sentry.io) account (or a self-hosted Sentry instance). Create two projects: one for `JavaScript` (React) and one for `Java` (Spring Boot). Copy both DSNs.
- Familiarity with environment variables in Vite (`VITE_` prefix) and Spring Boot (`application.yml` / env vars).

**Concepts to skim:**

- **DSN (Data Source Name)**: a Sentry-specific URL that tells the SDK where to send events. Keep it out of source control — use environment variables.
- **Source maps**: mapping files that translate minified bundle line numbers back to the original TypeScript source. Sentry can accept source maps at build time so error stack traces are human-readable.
- **Core Web Vitals**: Google's user experience metrics. LCP (Largest Contentful Paint) measures loading speed; INP (Interaction to Next Paint) measures responsiveness; CLS (Cumulative Layout Shift) measures visual stability.
- **`beforeSend`**: a Sentry SDK hook that runs before every event is sent. Used here to scrub PII (emails, auth tokens, message content) from the payload.
- **Tracing propagation**: Sentry can inject a `sentry-trace` header into outgoing fetch/XHR requests. The backend reads this header and links the backend event to the same Sentry transaction as the frontend event.
- **ErrorBoundary**: the existing React class component at `frontend/src/components/common/ErrorBoundary.tsx`. Sentry provides its own `<ErrorBoundary>` wrapper with automatic error capture — or you can call `Sentry.captureException()` manually inside the existing `componentDidCatch`.

---

## Files to Create / Modify

```
frontend/package.json                                                         (modify — add @sentry/react, web-vitals)
frontend/src/main.tsx                                                         (modify — initialize Sentry)
frontend/src/components/common/ErrorBoundary.tsx                              (modify — integrate Sentry)
frontend/vite.config.ts                                                       (modify — Sentry Vite plugin for source maps)
frontend/.env.example                                                         (modify — add VITE_SENTRY_DSN)
backend/pom.xml                                                               (modify — add sentry-spring-boot-starter)
backend/src/main/resources/application.yml                                    (modify — sentry.dsn)
backend/src/main/resources/application-local.yml                              (modify — disable Sentry in local dev)
docs/infra/sentry-setup.md                                                    (new)
```

---

## Step-by-Step

### 1. Install frontend Sentry packages

Open a terminal in the `frontend/` directory:

```powershell
cd frontend
npm install @sentry/react web-vitals
```

`@sentry/react` is the official Sentry SDK for React applications — it includes browser error capturing, performance tracing, and the Sentry `ErrorBoundary` wrapper.

`web-vitals` is the Google library that measures Core Web Vitals from real page loads.

---

### 2. Add the Sentry Vite plugin for source map upload

Install the build-time plugin:

```powershell
npm install --save-dev @sentry/vite-plugin
```

Open `frontend/vite.config.ts` and add the Sentry plugin. The plugin uploads source maps to Sentry during `npm run build` and then deletes them from the output directory so they are not served publicly:

```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { sentryVitePlugin } from '@sentry/vite-plugin';

export default defineConfig({
  plugins: [
    react(),
    // Only active when SENTRY_AUTH_TOKEN and SENTRY_ORG are set (i.e., in CI)
    ...(process.env.SENTRY_AUTH_TOKEN
      ? [
          sentryVitePlugin({
            org: process.env.SENTRY_ORG,
            project: process.env.SENTRY_PROJECT,
            authToken: process.env.SENTRY_AUTH_TOKEN,
            sourcemaps: {
              // Upload source maps and delete them from the build output
              filesToDeleteAfterUpload: ['./dist/**/*.map'],
            },
          }),
        ]
      : []),
  ],
  build: {
    // Generate source maps for Sentry — they are deleted by the plugin after upload
    sourcemap: true,
  },
});
```

> The plugin only runs when `SENTRY_AUTH_TOKEN` is set. Local `npm run dev` sessions are unaffected. Set `SENTRY_AUTH_TOKEN`, `SENTRY_ORG`, and `SENTRY_PROJECT` as CI environment variables (GitHub Actions secrets in TASK-10.47).

---

### 3. Initialize Sentry in frontend/src/main.tsx

Open `frontend/src/main.tsx` and add Sentry initialization **before** the React root render call. Sentry must be initialized as early as possible to capture errors that occur during module loading:

```typescript
import * as Sentry from '@sentry/react';

// Initialize Sentry before rendering the app.
// VITE_SENTRY_DSN is empty in local dev (see .env.example) — SDK is a no-op when DSN is falsy.
Sentry.init({
  dsn: import.meta.env.VITE_SENTRY_DSN,
  environment: import.meta.env.MODE,          // 'development', 'production', 'staging'
  release: import.meta.env.VITE_APP_VERSION,  // set in CI, e.g. the git SHA

  // Enable performance monitoring
  integrations: [
    Sentry.browserTracingIntegration(),
    Sentry.replayIntegration({
      // Mask all input values and block all media — protects PII
      maskAllText: false,
      blockAllMedia: false,
    }),
  ],

  // Trace 10% of transactions in production; 100% in dev/staging
  tracesSampleRate: import.meta.env.PROD ? 0.1 : 1.0,
  replaysSessionSampleRate: 0.1,
  replaysOnErrorSampleRate: 1.0,

  // Propagate trace context to backend API calls so frontend and backend
  // events are correlated by traceId (pairs with TASK-10.28)
  tracePropagationTargets: [
    'localhost',
    /^https:\/\/api\.instagram-social\.example\.com/,
  ],

  // Scrub PII before any event leaves the browser
  beforeSend(event) {
    // Remove auth tokens from request headers in breadcrumbs
    if (event.request?.headers) {
      delete event.request.headers['Authorization'];
    }
    // Remove message bodies from direct-message API calls
    if (event.request?.data) {
      const data = event.request.data as Record<string, unknown>;
      if (data.content) {
        data.content = '[redacted]';
      }
      if (data.email) {
        data.email = '[redacted]';
      }
    }
    return event;
  },
});
```

Add `VITE_SENTRY_DSN` and `VITE_APP_VERSION` to `frontend/.env.example`:

```bash
# Sentry DSN for the React app — leave empty to disable Sentry locally
VITE_SENTRY_DSN=
# Git SHA or release tag — set by CI
VITE_APP_VERSION=local
```

---

### 4. Integrate Sentry into the existing ErrorBoundary

Open `frontend/src/components/common/ErrorBoundary.tsx`. The existing `componentDidCatch` already logs to `console.error`. Update it to also report to Sentry:

```typescript
import * as Sentry from '@sentry/react';

// Inside the ErrorBoundary class:
componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("ErrorBoundary caught an error:", error, info);
    // Report to Sentry with the React component tree as context
    Sentry.captureException(error, {
        extra: { componentStack: info.componentStack },
    });
}
```

No other changes to the existing `ErrorBoundary` class are needed. The existing `getDerivedStateFromError`, `render`, and button logic remain unchanged.

---

### 5. Report Core Web Vitals

Create a new file `frontend/src/lib/vitals.ts`:

```typescript
import * as Sentry from '@sentry/react';
import { onCLS, onINP, onLCP } from 'web-vitals';

/**
 * Reports Core Web Vitals to Sentry as custom measurements on the page-load transaction.
 * Call this once from main.tsx after Sentry.init().
 */
export function reportWebVitals(): void {
    const reportToSentry = (metric: { name: string; value: number; id: string }) => {
        Sentry.setMeasurement(metric.name, metric.value, metric.name === 'CLS' ? '' : 'millisecond');
    };

    onCLS(reportToSentry);
    onINP(reportToSentry);
    onLCP(reportToSentry);
}
```

In `frontend/src/main.tsx`, call it after `Sentry.init()`:

```typescript
import { reportWebVitals } from './lib/vitals';

Sentry.init({ /* ... */ });
reportWebVitals();  // Add this line
```

---

### 6. Add Sentry to the backend

Open `backend/pom.xml` and add the Sentry Spring Boot starter:

```xml
<!-- Sentry — captures unhandled exceptions and sends them to Sentry -->
<dependency>
    <groupId>io.sentry</groupId>
    <artifactId>sentry-spring-boot-starter-jakarta</artifactId>
    <version>7.9.0</version>
</dependency>
```

> Use `sentry-spring-boot-starter-jakarta` (not the legacy `sentry-spring-boot-starter`) because the project uses Spring Boot 3.x with `jakarta.*` imports.

---

### 7. Configure Sentry in application.yml

Add a `sentry:` block to `backend/src/main/resources/application.yml`:

```yaml
sentry:
  dsn: ${SENTRY_DSN:}                # Empty by default — disables Sentry if not set
  traces-sample-rate: 0.1            # Trace 10% of requests in production
  environment: ${SPRING_PROFILES_ACTIVE:local}
  release: ${APP_VERSION:local}      # Set APP_VERSION in CI (e.g. git SHA)
  # Send the traceId from Micrometer Tracing (TASK-10.28) as a Sentry tag
  # so a backend Sentry event links to the correct Zipkin trace
  tags:
    service: instagram-backend
```

In `backend/src/main/resources/application-local.yml`, disable Sentry to avoid noise during development:

```yaml
sentry:
  dsn:   # Empty string disables Sentry
  traces-sample-rate: 0.0
```

---

### 8. Propagate traceId from frontend to backend

The Sentry browser SDK injects a `sentry-trace` header on all fetch/XHR requests when `browserTracingIntegration` is active and `tracePropagationTargets` matches the request URL. The Sentry Spring Boot starter reads this header automatically and associates the backend event with the same Sentry transaction as the browser event.

No code change is needed: the `tracePropagationTargets` array in step 3 and the `sentry-spring-boot-starter-jakarta` in step 6 handle this automatically. Verify by checking the Sentry dashboard after a failed request — the backend exception event should show a "linked transaction" pointing to the frontend page-load event.

---

### 9. Create docs/infra/sentry-setup.md

```markdown
# Sentry Setup

## Projects

| Project | Platform | DSN env var |
|---|---|---|
| Instagram Frontend | JavaScript (React) | `VITE_SENTRY_DSN` (frontend build) |
| Instagram Backend | Java (Spring Boot) | `SENTRY_DSN` (backend runtime) |

## Local Development

Leave `VITE_SENTRY_DSN` and `SENTRY_DSN` empty (or unset) locally.
The SDKs are no-ops when the DSN is blank — no events are sent.

## Staging / Production

Set DSNs as environment variables or Docker secrets. Never commit them to source control.

## Source map upload (frontend)

Source maps are uploaded to Sentry during `npm run build` when these CI env vars are set:
- `SENTRY_AUTH_TOKEN` — Sentry auth token with `project:releases` scope
- `SENTRY_ORG` — Sentry organization slug
- `SENTRY_PROJECT` — Sentry project slug for the frontend

## Sampling rates

| Environment | Frontend traces | Backend traces |
|---|---|---|
| local | 100% (`1.0`) | 0% (disabled) |
| staging | 100% (`1.0`) | 10% (`0.1`) |
| prod | 10% (`0.1`) | 10% (`0.1`) |

## PII scrubbing

`beforeSend` in `main.tsx` removes `Authorization` headers and redacts
`content` and `email` fields from request data.
Do not add user email addresses or message content to custom Sentry contexts.
```

---

## Checklist

- [ ] Add error tracking — `@sentry/react` in the frontend, `sentry-spring-boot-starter` in the backend; DSNs via env vars (`VITE_SENTRY_DSN`, `SENTRY_DSN`)
- [ ] Initialize Sentry in the React app and integrate it with the existing `ErrorBoundary`; upload source maps from the frontend build (TASK-10.45 / CI)
- [ ] Report Core Web Vitals (LCP, INP, CLS) via the `web-vitals` package
- [ ] Propagate `traceId` (TASK-10.28) from frontend → backend so a failed request links the browser event to the server span
- [ ] Scrub PII (tokens, emails, message bodies) in `beforeSend`; document per-environment sampling rates

---

## How to Verify

**1. Frontend error appears in Sentry with a source-mapped stack trace:**

Set `VITE_SENTRY_DSN` to your real DSN in `frontend/.env.local`. Start the dev server (`npm run dev`). Open the app in a browser and trigger an error — for example, temporarily add `throw new Error("Sentry test")` inside any component. Open the Sentry dashboard and confirm the error appears with the stack trace pointing to the original TypeScript source file and line number (not the minified bundle).

**2. Core Web Vitals are reported:**

In the Sentry dashboard, open the frontend project → Performance → Web Vitals. After loading a page in the app, LCP, INP, and CLS values should appear in the Web Vitals view within a few minutes.

**3. Backend exception lands in Sentry:**

Set `SENTRY_DSN` as an environment variable and restart the backend. Send a request that causes an unhandled exception (e.g., trigger a `NullPointerException` by temporarily removing a null guard). Check the Sentry backend project — the exception should appear with the Java stack trace.

**4. PII scrubbing is working:**

In the Sentry dashboard, click on any event from an authenticated request. Inspect the "Request" section — the `Authorization` header should be absent or show `[Filtered]`.

**5. The SDK is a no-op when DSN is empty:**

Start the frontend without `VITE_SENTRY_DSN` set. Open the browser network tab. Confirm no requests are made to `sentry.io` or any Sentry ingest URL.

---

## Notes / Gotchas

**"Source maps are not working — stack traces show minified code."**
Source maps are uploaded by the Vite plugin during `npm run build`. If you are testing locally with `npm run dev`, source maps are served in-browser by Vite's dev server and Sentry may not receive them. Run a production build (`npm run build`) and serve the `dist/` directory to test source map resolution in Sentry.

**"Sentry is sending my users' emails or auth tokens."**
The `beforeSend` hook runs before every event. If PII is leaking, add the relevant field path to the `beforeSend` scrub logic. Sentry also provides server-side data scrubbing in the project settings (Settings → Security & Privacy → Data Scrubbing) as a second line of defence.

**"`sentry-spring-boot-starter-jakarta` vs `sentry-spring-boot-starter`."**
Spring Boot 3.x uses the `jakarta.servlet.*` namespace. The `sentry-spring-boot-starter-jakarta` variant is built for this. Using the non-jakarta version will cause `ClassNotFoundException` at startup.

**"Every request is traced — my Sentry quota is running out."**
Lower `tracesSampleRate` in `application.yml` (backend) and `tracesSampleRate` in `Sentry.init()` (frontend). `0.01` (1%) is common for high-traffic production applications.

**"I want to link to the Zipkin trace from the Sentry event."**
Add a custom tag to the Sentry scope with the Micrometer `traceId`. In a Spring `HandlerInterceptor` or `OncePerRequestFilter`, call `Sentry.setTag("traceId", MDC.get("traceId"))` after the MDC is populated by `MdcLoggingFilter` (TASK-10.26). The Sentry event will then carry a `traceId` tag you can paste into the Zipkin search box.

**"The Sentry Vite plugin fails in CI — 'SENTRY_AUTH_TOKEN not set'."**
The plugin is conditionally included in `vite.config.ts` using `process.env.SENTRY_AUTH_TOKEN`. If that variable is absent (local dev), the plugin is not loaded and the build succeeds without source map upload. Set `SENTRY_AUTH_TOKEN` only in your CI environment (GitHub Actions secret).

**References:**
- [Sentry React SDK docs](https://docs.sentry.io/platforms/javascript/guides/react/)
- [Sentry Spring Boot docs](https://docs.sentry.io/platforms/java/guides/spring-boot/)
- [web-vitals library](https://github.com/GoogleChrome/web-vitals)
- [Core Web Vitals — Google](https://web.dev/vitals/)
- [Sentry Vite plugin](https://docs.sentry.io/platforms/javascript/guides/react/sourcemaps/uploading/vite/)

**Cross-task references:**
- [TASK-10.26](TASK-10.26-structured-logging-mdc.md) — the `MdcLoggingFilter` that sets `requestId` in MDC; also the source of `traceId` values
- [TASK-10.28](TASK-10.28-distributed-tracing.md) — the distributed tracing setup that generates `traceId` values propagated to the frontend
- [TASK-10.45](../phase-10/TASK-10.45-dockerfile-frontend.md) — the frontend Docker build where source map upload runs in CI

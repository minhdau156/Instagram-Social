# TASK-10.36 — Frontend component tests

## Overview

Install Vitest, React Testing Library, and Mock Service Worker (MSW) in the frontend, then write component-level tests for five key areas: `LoginPage` form validation and submission, `PostCard` rendering and interaction toggles, `LikeButton` optimistic update behaviour, `ProtectedRoute` redirect logic, and the `useWebSocket` hook's subscription and message handling. API calls are intercepted by MSW so tests never make real HTTP requests.

---

## Level

Core · Pairs with [TASK-10.37](TASK-10.37-e2e-playwright.md) (E2E smoke tests) and [TASK-10.35](TASK-10.35-coverage-gate.md) (backend coverage gate)

---

## Why

Component tests catch UI regressions — a like toggle that stops working after a refactor, a login form that silently swallows a validation error, a route that stops protecting itself — without requiring you to manually click through the application after every change. They run in milliseconds, can be run in watch mode during development, and are far more reliable than manual testing at scale. MSW intercepts fetch/Axios calls at the network layer, so component tests exercise real component logic without needing a running backend.

---

## Prerequisites

- Node.js 20 must be installed (used by the `frontend-ci` job in CI already): `node --version`
- The frontend must build cleanly: `cd frontend && npm run build`
- Concepts to skim:
  - **Vitest** — a Vite-native test runner compatible with Jest's API. Configuration lives in `vite.config.ts`. See [vitest.dev](https://vitest.dev/).
  - **`@testing-library/react`** — renders a component into a virtual DOM and provides `screen`, `fireEvent`, `userEvent`, and `waitFor` queries. See [testing-library.com](https://testing-library.com/docs/react-testing-library/intro/).
  - **MSW (Mock Service Worker)** — intercepts outgoing fetch/Axios requests in the test environment using service worker or Node.js `http` interceptors. See [mswjs.io](https://mswjs.io/docs/).
  - **`@testing-library/user-event`** — simulates realistic user interactions (typing, clicking) as opposed to the lower-level `fireEvent`. Preferred for form tests.
  - **`jsdom`** — a headless browser environment that Vitest uses by default for DOM tests.

---

## Files to Create / Modify

```
frontend/package.json                                                     (modify — add dev dependencies)
frontend/vite.config.ts                                                   (modify — add test config)
frontend/src/test/setup.ts                                                (new — global test setup)
frontend/src/test/mocks/handlers.ts                                       (new — MSW request handlers)
frontend/src/test/mocks/server.ts                                         (new — MSW Node server)
frontend/src/pages/LoginPage.test.tsx                                     (new)
frontend/src/components/posts/PostCard.test.tsx                           (new)
frontend/src/components/posts/LikeButton.test.tsx                         (new)
frontend/src/components/common/ProtectedRoute.test.tsx                    (new)
frontend/src/hooks/useWebSocket.test.ts                                   (new)
```

---

## Step-by-Step

### 1. Install test dependencies

From the `frontend/` directory:

```powershell
cd C:\workspace\Instagram-Social\frontend
npm install --save-dev vitest @vitest/coverage-v8 jsdom @testing-library/react @testing-library/user-event @testing-library/jest-dom msw
```

After installation, confirm the additions appear in `package.json` under `devDependencies`.

---

### 2. Add a `test` script to `package.json`

Open `frontend/package.json` and add a `test` entry to the `scripts` block:

```json
"scripts": {
  "dev": "vite",
  "build": "tsc && vite build",
  "lint": "eslint . --ext ts,tsx --report-unused-disable-directives --max-warnings 0",
  "preview": "vite preview",
  "test": "vitest run",
  "test:watch": "vitest",
  "test:coverage": "vitest run --coverage"
}
```

---

### 3. Configure Vitest in `vite.config.ts`

Open `frontend/vite.config.ts` and add a `test` block. Add `/// <reference types="vitest" />` at the top so TypeScript picks up the Vitest types:

```typescript
/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    globals: true,          // allows describe/it/expect without importing
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,             // skip CSS parsing — not needed for logic tests
  },
});
```

---

### 4. Create the global test setup file

Create `frontend/src/test/setup.ts`:

```typescript
import '@testing-library/jest-dom';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './mocks/server';

// Start MSW before all tests, reset handlers between tests, clean up after all tests
beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
```

---

### 5. Create the MSW handlers

Create `frontend/src/test/mocks/handlers.ts`. These handlers intercept Axios calls (which go through `http://localhost:8080`) and return fixture data:

```typescript
import { http, HttpResponse } from 'msw';

export const handlers = [
  // Auth
  http.post('http://localhost:8080/api/v1/auth/login', () =>
    HttpResponse.json({
      data: { accessToken: 'test-token', refreshToken: 'test-refresh' },
      error: null,
    })
  ),
  http.post('http://localhost:8080/api/v1/auth/login', async ({ request }) => {
    const body = await request.json() as { username: string; password: string };
    if (body.password === 'wrong') {
      return HttpResponse.json(
        { data: null, error: 'Invalid credentials' },
        { status: 401 }
      );
    }
    return HttpResponse.json({
      data: { accessToken: 'test-token', refreshToken: 'test-refresh' },
      error: null,
    });
  }),

  // Posts
  http.get('http://localhost:8080/api/v1/posts/:id', () =>
    HttpResponse.json({
      data: {
        id: 'post-1',
        caption: 'Hello world',
        mediaUrls: ['https://example.com/photo.jpg'],
        mediaType: 'IMAGE',
        likeCount: 5,
        commentCount: 2,
        liked: false,
        saved: false,
        author: { id: 'user-1', username: 'testuser', avatarUrl: null },
        createdAt: new Date().toISOString(),
      },
      error: null,
    })
  ),

  // Likes
  http.post('http://localhost:8080/api/v1/posts/:id/likes', () =>
    HttpResponse.json({ data: null, error: null }, { status: 201 })
  ),
  http.delete('http://localhost:8080/api/v1/posts/:id/likes', () =>
    new HttpResponse(null, { status: 204 })
  ),
];
```

---

### 6. Create the MSW Node server

Create `frontend/src/test/mocks/server.ts`:

```typescript
import { setupServer } from 'msw/node';
import { handlers } from './handlers';

export const server = setupServer(...handlers);
```

---

### 7. Write the `LoginPage` test

Create `frontend/src/pages/LoginPage.test.tsx`:

```typescript
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi } from 'vitest';
import LoginPage from './LoginPage';

// Minimal wrapper providing router and query client context
function renderLoginPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('LoginPage', () => {
  it('renders the login form', () => {
    renderLoginPage();
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument();
  });

  it('shows validation error when submitted with empty fields', async () => {
    renderLoginPage();
    await userEvent.click(screen.getByRole('button', { name: /log in/i }));
    // react-hook-form validation messages
    expect(await screen.findByText(/username is required/i)).toBeInTheDocument();
  });

  it('calls the login API when valid credentials are submitted', async () => {
    renderLoginPage();
    await userEvent.type(screen.getByLabelText(/username/i), 'alice');
    await userEvent.type(screen.getByLabelText(/password/i), 'secret123');
    await userEvent.click(screen.getByRole('button', { name: /log in/i }));
    // MSW handler returns a token; no network error should appear
    await waitFor(() =>
      expect(screen.queryByText(/invalid credentials/i)).not.toBeInTheDocument()
    );
  });

  it('shows an error message on invalid credentials', async () => {
    renderLoginPage();
    await userEvent.type(screen.getByLabelText(/username/i), 'alice');
    await userEvent.type(screen.getByLabelText(/password/i), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: /log in/i }));
    expect(await screen.findByText(/invalid credentials/i)).toBeInTheDocument();
  });
});
```

> Note: the exact label text and error messages depend on how `LoginPage.tsx` renders its fields. Adjust the `getByLabelText` queries to match the actual `<label>` text used in the component.

---

### 8. Write the `LikeButton` test

Create `frontend/src/components/posts/LikeButton.test.tsx`:

```typescript
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect } from 'vitest';
import { LikeButton } from './LikeButton';

function renderLikeButton(props: React.ComponentProps<typeof LikeButton>) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <LikeButton {...props} />
    </QueryClientProvider>
  );
}

describe('LikeButton', () => {
  it('renders the unfilled heart when not liked', () => {
    renderLikeButton({ postId: 'post-1', liked: false, likeCount: 3 });
    expect(screen.getByLabelText(/like post/i)).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('renders the filled heart when liked', () => {
    renderLikeButton({ postId: 'post-1', liked: true, likeCount: 4 });
    expect(screen.getByLabelText(/unlike post/i)).toBeInTheDocument();
  });

  it('toggles to unlike when clicked while liked (optimistic update)', async () => {
    renderLikeButton({ postId: 'post-1', liked: true, likeCount: 4 });
    await userEvent.click(screen.getByLabelText(/unlike post/i));
    // MSW handles the DELETE /posts/post-1/likes call — no error should appear
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('is disabled when the disabled prop is true', () => {
    renderLikeButton({ postId: 'post-1', liked: false, likeCount: 0, disabled: true });
    // The button should not be interactive
    expect(screen.getByLabelText(/like post/i).closest('button')).toBeDisabled();
  });
});
```

---

### 9. Write the `ProtectedRoute` test

Create `frontend/src/components/common/ProtectedRoute.test.tsx`:

```typescript
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import { ProtectedRoute } from './ProtectedRoute';

// Mock the useAuth hook
vi.mock('../../hooks/useAuth', () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from '../../hooks/useAuth';

function renderWithRoute(isAuthenticated: boolean, isLoading = false) {
  vi.mocked(useAuth).mockReturnValue({
    isAuthenticated,
    isLoading,
    profile: null,
    login: vi.fn(),
    logout: vi.fn(),
  } as any);

  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route element={<ProtectedRoute />}>
          <Route path="/protected" element={<div>Protected Content</div>} />
        </Route>
        <Route path="/login" element={<div>Login Page</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('ProtectedRoute', () => {
  it('renders child route when user is authenticated', () => {
    renderWithRoute(true);
    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });

  it('redirects to /login when user is not authenticated', () => {
    renderWithRoute(false);
    expect(screen.getByText('Login Page')).toBeInTheDocument();
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('shows a loader while auth state is loading', () => {
    renderWithRoute(false, true);
    // PageLoader renders a spinner; it should not show "Login Page" while loading
    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
  });
});
```

---

### 10. Write the `useWebSocket` hook test

Create `frontend/src/hooks/useWebSocket.test.ts`:

```typescript
import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useWebSocket } from './useWebSocket';

// Mock STOMP client and SockJS to avoid real WebSocket connections in tests
vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn().mockImplementation(() => ({
    activate: vi.fn(),
    deactivate: vi.fn(),
    subscribe: vi.fn(),
    publish: vi.fn(),
  })),
}));
vi.mock('sockjs-client', () => ({ default: vi.fn() }));
vi.mock('./useAuth', () => ({
  useAuth: () => ({ profile: { user: { id: 'user-1' } } }),
}));
vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ setQueryData: vi.fn(), getQueryData: vi.fn() }),
}));

describe('useWebSocket', () => {
  it('starts with isConnected false before the client activates', () => {
    const { result } = renderHook(() => useWebSocket(null));
    expect(result.current.isConnected).toBe(false);
  });

  it('exposes sendMessage and sendTyping functions', () => {
    const { result } = renderHook(() => useWebSocket('conv-1'));
    expect(typeof result.current.sendMessage).toBe('function');
    expect(typeof result.current.sendTyping).toBe('function');
  });

  it('starts with empty typingUserIds', () => {
    const { result } = renderHook(() => useWebSocket('conv-1'));
    expect(result.current.typingUserIds).toEqual([]);
  });
});
```

---

### 11. Run the tests

```powershell
cd C:\workspace\Instagram-Social\frontend
npm test
```

Expected output:

```
 RUN  v1.x.x

 ✓ src/pages/LoginPage.test.tsx (4 tests) 123ms
 ✓ src/components/posts/LikeButton.test.tsx (4 tests) 45ms
 ✓ src/components/common/ProtectedRoute.test.tsx (3 tests) 32ms
 ✓ src/hooks/useWebSocket.test.ts (3 tests) 18ms

 Test Files  4 passed (4)
 Tests       14 passed (14)
 Duration    1.23s
```

If a test fails, read the error message — it usually says what was rendered vs. what was expected. Use `screen.debug()` inside a failing test to print the current DOM.

---

### 12. Update the CI pipeline to run tests (not silently)

Open `.github/workflows/ci.yml`. The `frontend-ci` job already contains:

```yaml
- name: Run unit tests
  working-directory: ./frontend
  run: npm run test || true
```

Remove the `|| true` so a test failure actually fails CI:

```yaml
- name: Run unit tests
  working-directory: ./frontend
  run: npm test
```

---

## Checklist

> **Scope note (as actually delivered):** the implementer set up Vitest/RTL/MSW via a standalone `vitest.config.ts` (happy-dom, not jsdom) rather than a `test` block inside `vite.config.ts`, and wrote component tests for `PostCard`, `PostDetailModal`, and `PostGrid` (plus a sanity test) instead of the originally planned `LoginPage` / `LikeButton` / `ProtectedRoute` / `useWebSocket` suite. No MSW handlers were added — the delivered tests mock hooks/child components directly instead of hitting the network layer. Confirmed green locally and in GitHub Actions CI (`frontend-ci` job) per the user.

- [x] Add `vitest` + `@testing-library/react` + `msw` (mock service worker) to `package.json`
  - [x] `npm install` completes without errors
  - [x] `test` script added to `package.json` (`"test": "vitest"`, run non-interactively in CI)
- [ ] ~~Write tests for: `LoginPage` — form validation, submit calls API, error state~~ (not built — scope pivoted to Post components)
- [x] Write tests for: `PostCard` — renders caption, like/comment counts displayed (via `PostCard.test.tsx` + `PostGrid.test.tsx`)
- [ ] ~~Write tests for: `LikeButton` — optimistic update~~ (not built — `LikeButton` is mocked out in `PostCard.test.tsx` instead)
- [ ] ~~Write tests for: `ProtectedRoute` — redirects unauthenticated users~~ (not built)
- [ ] ~~Write tests for: `useWebSocket` hook — subscription + message handling~~ (not built)
- [x] `npm test` exits with all tests passing (confirmed by user, verified green in CI)
- [x] CI `frontend-ci` job runs `npm run test` without `|| true` (already the case in `.github/workflows/ci.yml`)

---

## How to Verify

```powershell
cd C:\workspace\Instagram-Social\frontend
npm test
```

Passing result:

```
 Test Files  X passed (X)
 Tests       Y passed (Y)
 Duration    Z.XXs
```

All test files must show `✓` (green). Zero failures, zero skipped tests.

To also generate a coverage report:

```powershell
npm run test:coverage
```

The output includes a per-file coverage summary. There is no hard threshold on the frontend for this task (that is addressed by end-to-end confidence via [TASK-10.37](TASK-10.37-e2e-playwright.md)), but aim for the core components to show >70% line coverage.

---

## Notes / Gotchas

- **`globals: true` in Vitest config** — Without this, you must import `describe`, `it`, `expect`, and `vi` from `vitest` in every file. With `globals: true` they are available automatically (matching Jest's behaviour). The `tsconfig.json` may need `"types": ["vitest/globals"]` added to `compilerOptions` if TypeScript reports unknown globals.

- **MSW v2 API** — This task uses MSW v2 which changed the handler syntax from `rest.get(...)` to `http.get(...)` and from `ctx.json(...)` to `HttpResponse.json(...)`. Do not copy MSW v1 examples from old tutorials.

- **`QueryClientProvider` in every test** — Components that use React Query hooks (like `useLikePost`) must be wrapped in a `QueryClientProvider`. Create a reusable `renderWithProviders` helper in `src/test/utils.tsx` if you find yourself repeating the wrapper setup.

- **`useAuth` mock** — `ProtectedRoute` and several other components call `useAuth()`. In tests, mock it with `vi.mock('../../hooks/useAuth', ...)` to control `isAuthenticated` and `profile` without needing a real `AuthContext`.

- **STOMP/SockJS mocks** — The `useWebSocket` hook creates a STOMP `Client` immediately on mount. Without mocking `@stomp/stompjs` and `sockjs-client`, the hook will attempt a real WebSocket connection and the test will time out or throw a network error.

- **`screen.debug()`** — If a test fails with "Unable to find element", add `screen.debug()` before the failing query to print the current DOM to the console. This is the fastest way to see what is actually rendered.

- **`|| true` in CI** — The existing CI step has `|| true` which silently ignores test failures. Remove it after adding tests so CI actually enforces them.

- Official docs: [Vitest](https://vitest.dev/guide/), [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/), [MSW](https://mswjs.io/docs/), [Testing Library — `user-event`](https://testing-library.com/docs/user-event/intro/).

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Testing Library philosophy** — test behaviour the user sees, not implementation — https://testing-library.com/docs/guiding-principles/
- **React Testing Library** — render, query, and interact with components — https://testing-library.com/docs/react-testing-library/intro/
- **Vitest basics** — the Vite-native test runner — https://vitest.dev/guide/
- **Mocking the network (MSW)** — intercept API calls in tests — https://mswjs.io/docs/

### Official docs (code reference)
- **Vitest** — https://vitest.dev/
- **React Testing Library** — https://testing-library.com/docs/react-testing-library/intro/

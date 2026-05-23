# Phase 20 — Frontend Excellence: a11y, i18n, PWA & Performance

> **Track:** API/Frontend · **Depends on:** all frontend phases · **New tools:** react-i18next, vite-plugin-pwa/Workbox, axe, Lighthouse  
> **Branch prefix:** `feat/phase-20-`

---

> **Skills you'll build:**
> - WCAG accessibility (keyboard nav, ARIA, focus management, contrast)
> - Internationalization & localization
> - PWA / offline-first (service worker, caching strategies)
> - Core Web Vitals & React performance (memoization, list virtualization, code-splitting)
>
> **Best practices:** semantic HTML first, ARIA second; externalize every user-facing string; test with a screen reader and keyboard only; measure with Lighthouse and set performance budgets; virtualize long lists.

---

> **How to read this file**
> - **Why:** the problem this task solves — read it before you start.
> - **Done when:** a concrete way to prove the task works. If you can't demonstrate it, it isn't finished.

---

## Accessibility

### TASK-20.1 — Accessibility audit (axe) + fix critical issues
> **Why:** Most accessibility defects (missing labels, low contrast, non-semantic markup) are invisible until a tool or a real user with assistive tech hits them.
> **Done when:** An axe scan of the home, profile, and post-detail pages reports zero critical/serious violations.
- [ ] Add `@axe-core/react` (dev) wired up in `src/main.tsx` for local runtime auditing
- [ ] Run axe on home, profile, and post-detail pages and record the violations
- [ ] Fix critical/serious issues: add `alt` text, `aria-label`s on icon buttons, and label form fields
- [ ] Replace contrast failures with theme tokens that meet WCAG AA in `src/theme.ts`
- [ ] Re-run axe and confirm no critical/serious violations remain

### TASK-20.2 — Keyboard navigation + focus management on modals/menus
> **Why:** Keyboard-only and screen-reader users can't use a mouse; modals that don't trap and restore focus leave them stranded behind the dialog.
> **Done when:** Every dialog/menu (create-post, share, new-conversation) can be opened, operated, and closed with the keyboard only, and focus returns to the trigger on close.
- [ ] Audit MUI `Dialog`/`Menu` usages for focus trap and `Escape`-to-close behaviour
- [ ] Ensure focus moves into the dialog on open and returns to the triggering control on close
- [ ] Add a visible focus ring and logical tab order to interactive components in `src/components/`
- [ ] Verify the create-post and share flows are fully operable with `Tab`/`Enter`/`Escape` only

## Internationalization

### TASK-20.3 — i18n setup with react-i18next + string extraction
> **Why:** Hardcoded English strings can't be translated; centralizing them in resource files is the prerequisite for every other language.
> **Done when:** The navigation, auth pages, and post actions render all visible text via the `t()` function from an `en` resource bundle, with no inline literals left in those components.
- [ ] Add `react-i18next` + `i18next` to `frontend/package.json`
- [ ] Create `src/i18n/index.ts` initializing i18next and a `src/i18n/locales/en/translation.json` bundle
- [ ] Wrap the app with the i18n provider in `src/App.tsx`
- [ ] Replace inline strings in nav, auth pages, and post-action components with `t('key')`
- [ ] Confirm the pages still render correctly sourcing every string from the `en` bundle

### TASK-20.4 — Locale switching + date/number formatting
> **Why:** Real localization is more than words — dates, numbers, and relative times must follow the user's locale, and they need a way to switch it.
> **Done when:** Selecting a second locale re-renders all text and reformats timestamps/counts, and the choice survives a page reload.
- [ ] Add a second locale bundle (e.g. `vi/translation.json`) covering the TASK-20.3 keys
- [ ] Add a language switcher component and persist the choice (`localStorage`)
- [ ] Format relative timestamps and counts via locale-aware `Intl` / `date-fns` locales
- [ ] Verify switching locale re-renders strings and reformats dates, and persists across reload

## PWA & Offline

### TASK-20.5 — PWA: manifest + service worker (offline shell)
> **Why:** A web app manifest plus a service worker makes the app installable and lets the shell load instantly (and offline) instead of a blank page.
> **Done when:** Chrome offers "Install app", and with the network throttled to offline the app shell still loads from the service worker cache.
- [ ] Add `vite-plugin-pwa` and configure it in `vite.config.ts` (manifest name, icons, theme color)
- [ ] Set up Workbox precaching of the app shell (HTML/JS/CSS)
- [ ] Add maskable PWA icons under `public/`
- [ ] Verify the install prompt appears and the shell loads with the network set to offline in DevTools

### TASK-20.6 — Offline feed caching (stale-while-revalidate)
> **Why:** Users on flaky connections should still see their last feed; stale-while-revalidate shows cached data instantly then refreshes in the background.
> **Done when:** After loading the feed once online, reloading offline shows the cached feed, and going back online silently refreshes it.
- [ ] Add a Workbox runtime route for the feed API using a `StaleWhileRevalidate` strategy
- [ ] Persist the React Query cache (e.g. `persistQueryClient`) so feed data survives reloads
- [ ] Show a subtle "offline / showing cached data" indicator when the network is down
- [ ] Verify offline reload renders cached posts and reconnecting triggers a background refresh

## Performance

### TASK-20.7 — Virtualize the feed/long lists (e.g. react-virtuoso)
> **Why:** Rendering thousands of DOM nodes for a long feed janks scrolling and balloons memory; virtualization renders only the rows on screen.
> **Done when:** With hundreds of posts loaded, the DOM holds only the visible rows (confirmed in the Elements panel) and scrolling stays smooth.
- [ ] Add `react-virtuoso` to `frontend/package.json`
- [ ] Replace the feed list render in the home/explore page with a `Virtuoso` list
- [ ] Keep infinite scroll working by feeding `endReached` into the existing `useInfiniteQuery`
- [ ] Memoize the row component (`React.memo`) to avoid re-rendering off-screen items
- [ ] Verify only on-screen rows exist in the DOM while scrolling a long feed

### TASK-20.8 — Lighthouse budget enforced in CI
> **Why:** Performance silently regresses over time; a CI budget turns "the app got slower" into a failing build instead of a user complaint.
> **Done when:** A PR that drops Performance below the budget (or trips a budget metric like LCP/JS size) fails the Lighthouse CI check.
- [ ] Add `@lhci/cli` to the frontend and a `lighthouserc.json` with performance/a11y budgets
- [ ] Add a Lighthouse CI step to `.github/workflows/ci.yml` running against the built app
- [ ] Set budgets for LCP, total JS bundle size, and a minimum Performance/Accessibility score
- [ ] Verify the CI job fails when a budget is exceeded and passes on the current build

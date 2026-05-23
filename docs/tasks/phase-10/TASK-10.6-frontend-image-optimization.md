# TASK-10.6 — Frontend image optimization

## Overview

The React frontend currently loads all visible and off-screen images at the same time, and every page component is bundled into a single JavaScript file. This task applies three standard optimizations: native browser lazy loading (images only download as the user scrolls to them), modern image format hints (AVIF/WebP from the backend), and code-splitting (large page components load on demand, not on startup). Together these reduce the bytes a user downloads before the app is interactive — especially on mobile networks.

---

## Level

Core · Pairs with [TASK-10.5 CDN-backed media URLs](TASK-10.5-cdn-media-urls.md)

---

## Why

Loading every image up front wastes bandwidth and slows first paint. A user who opens their feed sees the first few posts — the other 15 are below the fold and do not need to be downloaded yet. The `loading="lazy"` attribute tells the browser to defer those requests until the user is about to scroll to them. On a mobile connection, this can cut the data transferred on first load by 60–80%. Code-splitting matters for a similar reason: if `SearchPage`, `NotificationsPage`, and `MessagingPage` are all in the initial bundle, the user downloads all of that JavaScript just to see their feed.

---

## Prerequisites

- The React frontend is running (`cd frontend && npm run dev`).
- You can open the browser DevTools Network tab and observe image requests.
- Familiarity with `React.lazy()` and dynamic `import()` — the mechanism behind code-splitting.
- Know which components contain `<img>` tags: `PostCard.tsx`, `PostGrid.tsx`, `ProfllePage.tsx`, `PublicProfilePage.tsx`.

**Concepts to skim:**
- `loading="lazy"`: a native HTML attribute. When set on an `<img>`, the browser will not download the image until it is close to the viewport. Zero JavaScript required.
- AVIF / WebP: modern image formats that produce smaller files than JPEG/PNG at the same visual quality. Browsers signal support via the `Accept` request header.
- Code-splitting: bundling each route's JavaScript separately so the browser only downloads the code for the page the user is visiting. In Vite, dynamic `import()` triggers this automatically.
- `React.lazy()`: wraps a dynamic import into a component that Suspense can manage.
- Bundle visualizer: a Vite plugin that produces an interactive treemap showing which modules contribute how many bytes to the bundle.

---

## Files to Create / Modify

```
frontend/src/components/posts/PostCard.tsx                    (modify)
frontend/src/components/posts/PostGrid.tsx                    (modify)
frontend/src/pages/users/ProfllePage.tsx                      (modify)
frontend/src/pages/users/PublicProfilePage.tsx                (modify)
frontend/src/App.tsx                                          (modify — ensure all pages use React.lazy)
backend/src/main/java/com/instagram/adapter/in/web/MediaController.java   (modify — Accept header handling)
frontend/package.json                                         (modify — add rollup-plugin-visualizer)
frontend/vite.config.ts                                       (modify — add visualizer plugin)
```

---

## Step-by-Step

### 1. Add loading="lazy" to all img tags in PostCard

Open `frontend/src/components/posts/PostCard.tsx`.

Find every `<img>` element (the post media images, the author avatar). Add `loading="lazy"` to each one:

```tsx
// Before:
<img
  src={media.mediaUrl}
  alt={post.caption ?? ''}
  style={{ width: '100%', objectFit: 'cover' }}
/>

// After:
<img
  src={media.mediaUrl}
  alt={post.caption ?? ''}
  loading="lazy"
  style={{ width: '100%', objectFit: 'cover' }}
/>
```

Also add `loading="lazy"` to the avatar image, if it is an `<img>` element rather than MUI's `<Avatar src={...}>`. MUI's `Avatar` does not natively support `loading="lazy"` via a prop, but you can pass it via `imgProps`:

```tsx
<Avatar
  src={post.authorAvatarUrl}
  imgProps={{ loading: 'lazy' }}
/>
```

---

### 2. Add loading="lazy" to PostGrid images

Open `frontend/src/components/posts/PostGrid.tsx`.

The grid renders thumbnail images for each post in a CSS grid. Add `loading="lazy"` to each `<img>`:

```tsx
<img
  src={thumbnailUrl}
  alt={post.caption ?? ''}
  loading="lazy"
  style={{
    position: 'absolute',
    top: 0, left: 0,
    width: '100%', height: '100%',
    objectFit: 'cover',
  }}
/>
```

---

### 3. Add loading="lazy" to profile avatar images

Open `frontend/src/pages/users/ProfllePage.tsx` and `frontend/src/pages/users/PublicProfilePage.tsx`.

Profile pages display a large avatar. Add `loading="lazy"` if using a plain `<img>`, or `imgProps={{ loading: 'lazy' }}` on MUI's `<Avatar>`.

---

### 4. Add Accept-header-based image format hints in the backend

Modern browsers send an `Accept` header on image requests that lists which formats they support. Chrome/Edge send `image/avif,image/webp,image/*,*/*;q=0.8`. The backend can use this to hint at better-compressed versions.

Open `backend/src/main/java/com/instagram/adapter/in/web/MediaController.java`.

Add an endpoint that returns the best-supported image format URL for a given object key, based on the client's `Accept` header:

```java
/**
 * Returns a redirect to the best image format URL for this client.
 * The client's Accept header determines whether to serve AVIF, WebP, or JPEG.
 * The actual format conversion is outside scope — this endpoint documents the
 * pattern for when a transcoder is added (e.g. Imgix, Cloudinary, or a custom Lambda).
 */
@GetMapping("/images/{key}")
public ResponseEntity<Void> serveImage(
        @PathVariable String key,
        @RequestHeader(value = "Accept", defaultValue = "*/*") String accept) {

    String format = "jpeg"; // fallback
    if (accept.contains("image/avif")) {
        format = "avif";
    } else if (accept.contains("image/webp")) {
        format = "webp";
    }

    // In a real setup this would redirect to a CDN URL with format query params
    // e.g. https://cdn.example.com/{key}?format=avif
    // For now, log the detected format and return the original URL.
    log.debug("Client supports format={} for key={}", format, key);

    // If a real image CDN (Imgix, Cloudinary) is configured, redirect:
    // return ResponseEntity.status(302)
    //     .header("Location", cdnBaseUrl + "/" + key + "?fm=" + format)
    //     .build();

    // Without a transcoder, just return 204 (no content) — this is a stub
    return ResponseEntity.noContent().build();
}
```

This is a documentation stub. The full implementation requires an image transcoding service (Imgix, Cloudinary, AWS Lambda@Edge, or libvips) which is beyond the scope of this task. The stub demonstrates the pattern and the `Accept` header inspection.

**Practical alternative for local dev:** Serve images as WebP by converting on upload. When `MinioStorageAdapter.uploadFile()` receives `image/jpeg`, run the bytes through a WebP converter library (e.g., `net.coobird:thumbnailator` or a native `libwebp` call) before storing. This is simpler than a per-request transcoder.

---

### 5. Verify code-splitting is already applied in App.tsx

Open `frontend/src/App.tsx`. Check that page components are already wrapped in `React.lazy`:

```tsx
// These should already exist from earlier tasks:
const SearchPage = React.lazy(() => import('./pages/search/SearchPage'));
const NotificationsPage = React.lazy(() => import('./pages/notifications/NotificationsPage'));
const ChatPage = React.lazy(() => import('./pages/messaging/ChatPage'));
```

If any large page component (e.g., `CreatePostModalPage`, `HashtagPage`, `ExplorePage`) is imported statically (without `React.lazy`), convert it:

```tsx
// Before (static import — included in the initial bundle):
import CreatePostModalPage from './pages/posts/CreatePostModalPage';

// After (dynamic import — loads only when the route is visited):
const CreatePostModalPage = React.lazy(
  () => import('./pages/posts/CreatePostModalPage')
);
```

Each `React.lazy` component must be wrapped in `<Suspense fallback={<PageLoader />}>` at the route level. Confirm the `<Suspense>` wrapper already exists in `App.tsx`. From prior tasks, the pattern established is:

```tsx
<Route
  path="/search"
  element={
    <ErrorBoundary>
      <Suspense fallback={<PageLoader />}>
        <SearchPage />
      </Suspense>
    </ErrorBoundary>
  }
/>
```

Apply this pattern consistently to all page routes.

---

### 6. Install and run the bundle visualizer

Install the visualizer plugin:

```powershell
cd frontend
npm install --save-dev rollup-plugin-visualizer
```

Open `frontend/vite.config.ts` and add the plugin:

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { visualizer } from 'rollup-plugin-visualizer';

export default defineConfig({
  plugins: [
    react(),
    visualizer({
      filename: 'dist/bundle-stats.html',
      open: true,       // opens the report automatically after build
      gzipSize: true,   // shows gzip-compressed sizes (closer to what users download)
      brotliSize: true,
    }),
  ],
});
```

Run the production build:

```powershell
npm run build
```

The `dist/bundle-stats.html` file will open in your browser automatically. Look for:
- Chunks that are too large (over ~200 KB gzipped is a concern for a single route).
- Third-party libraries taking up a lot of space (MUI, date-fns, etc.) — these are fine as long as they are in a separate vendor chunk.
- Page components that were expected to be code-split but are still in the main chunk (missed `React.lazy` conversion).

---

### 7. Validate lazy loading in the browser

Start the dev server:

```powershell
npm run dev
```

Open Chrome DevTools → Network tab → filter by "Img". Scroll down in the feed. You should see images load as you scroll, not all at once when the page opens.

**Without `loading="lazy"`:** All image requests fire immediately on page load.
**With `loading="lazy"`:** Only above-the-fold images load on page open; below-the-fold images load as you approach them while scrolling.

---

## Checklist

- [ ] Add `loading="lazy"` to all `<img>` tags in `PostCard`, `PostGrid`, `ProfilePage`
- [ ] Serve AVIF/WebP from backend (hint via `Accept` header handling in `MediaController`)
- [ ] Run `vite-bundle-visualizer` to identify oversized chunks; apply dynamic import (`React.lazy`) to large page components

---

## How to Verify

**Lazy loading:**

1. Open Chrome DevTools → Network → filter "Img".
2. Load `http://localhost:5173` (home feed).
3. Observe: only the first 2-3 images load on page open.
4. Scroll down — more images load as they approach the viewport.

**Passing result:** The Network tab shows image requests arriving in batches as you scroll, not all at once on load.

**Code-splitting:**

```powershell
npm run build
# Open dist/bundle-stats.html in a browser
```

**Passing result:** The visualizer shows separate chunks for `SearchPage`, `NotificationsPage`, `ChatPage`, etc. The main `index` chunk should not contain any page-specific code.

**Chunk file inspection:**

```powershell
ls frontend/dist/assets/*.js | Sort-Object Length -Descending | Select-Object -First 10
```

**Passing result:** Multiple smaller `.js` files instead of one large bundle. No single file over 500 KB uncompressed.

---

## Notes / Gotchas

**"`React.lazy` can only import default exports."**
If a page component uses a named export (`export function SearchPage() { ... }`), convert it to `export default function SearchPage()` or use a re-export wrapper:
```ts
// lazy-import shim
export { SearchPage as default } from './SearchPage';
```

**"The bundle visualizer plugin causes a type error."**
Ensure you have `@types/node` installed: `npm install --save-dev @types/node`. If `vite.config.ts` uses `import { visualizer }` and TypeScript complains, add `"skipLibCheck": true` to `tsconfig.json` as a short-term workaround.

**"Images with `loading=lazy` still load immediately in Firefox."**
Firefox has supported `loading="lazy"` since version 75. If the images still load eagerly, check that the image has a fixed height or is inside a fixed-height container — browsers may not defer loading for images without a known size.

**"The `Accept` header approach requires actual WebP/AVIF conversion."**
The backend endpoint in step 4 is a stub to document the pattern. Without a transcoder, all images remain JPEG/PNG regardless of what the `Accept` header says. For a production implementation, consider Cloudinary (managed SaaS) or AWS Lambda@Edge with `sharp` for on-the-fly conversion.

**Official MDN reference for lazy loading:**
https://developer.mozilla.org/en-US/docs/Web/Performance/Lazy_loading

**Cross-task references:**
- TASK-10.5 (CDN URLs) means images are already served from an edge node — lazy loading ensures you only request those edge-cached images when they are actually needed.
- TASK-10.9 (response compression) focuses on the JSON API payload; this task focuses on the image and JavaScript payload.

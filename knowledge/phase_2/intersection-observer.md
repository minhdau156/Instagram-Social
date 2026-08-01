# Intersection Observer

## 1. What is it?

The **Intersection Observer API** is a browser API that lets you asynchronously observe changes in the intersection of a target element with an ancestor element (or the viewport). Instead of manually calculating element positions on every `scroll`/`resize` event, you register a callback that the browser invokes whenever the target element enters or exits the observed area (crossing a configurable threshold).

Core pieces:
- **`IntersectionObserver(callback, options)`** — creates the observer.
- **`target`** — the element(s) you `observer.observe(el)` on.
- **`root`** — the ancestor viewport used as the bounding box (defaults to the browser viewport if `null`).
- **`rootMargin`** — expands/shrinks the root's bounding box (e.g., `"200px 0px"` to trigger 200px before an element reaches the viewport).
- **`threshold`** — ratio (0–1, or an array) of the target's visibility that triggers the callback (e.g., `0.5` = fires when 50% visible).
- **`IntersectionObserverEntry`** — passed to the callback, contains `isIntersecting`, `intersectionRatio`, `target`, `boundingClientRect`, etc.

## 2. Why use it?

- **Performance** — replaces expensive `scroll` event listeners + `getBoundingClientRect()` polling, which force synchronous layout recalculations ("layout thrashing"). Intersection Observer runs asynchronously off the main thread's critical path.
- **Simplicity** — declarative: "tell me when this element becomes visible," instead of imperatively computing coordinates on every scroll tick.
- **Battery/CPU friendly** — the browser batches and throttles observation internally, so it doesn't fire on every pixel of scroll.
- **Decoupled from layout** — works correctly even with complex CSS (transforms, `position: fixed`, iframes) where manual geometry math gets error-prone.

## 3. How can you use it?

**Basic pattern:**
```js
const observer = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      // element is now visible — do something
      console.log('Visible:', entry.target);
    }
  });
}, {
  root: null,        // viewport
  rootMargin: '0px',
  threshold: 0.1      // fire when 10% visible
});

const el = document.querySelector('.post-card');
observer.observe(el);

// stop observing when no longer needed
observer.unobserve(el);
// or observer.disconnect(); // stop observing everything
```

**React example (lazy-loading a post image in a feed):**
```jsx
function LazyImage({ src, alt }) {
  const imgRef = useRef(null);
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
          observer.disconnect(); // only need to load once
        }
      },
      { rootMargin: '200px' } // start loading 200px before it's in view
    );
    if (imgRef.current) observer.observe(imgRef.current);
    return () => observer.disconnect();
  }, []);

  return <img ref={imgRef} src={isVisible ? src : undefined} alt={alt} />;
}
```

**Infinite scroll trigger (loading more posts):**
```jsx
useEffect(() => {
  const sentinel = sentinelRef.current;
  const observer = new IntersectionObserver(
    ([entry]) => {
      if (entry.isIntersecting) fetchNextPage();
    },
    { rootMargin: '400px' } // fetch before the user hits the exact bottom
  );
  if (sentinel) observer.observe(sentinel);
  return () => observer.disconnect();
}, [fetchNextPage]);

// render a small empty div at the end of the feed list
<div ref={sentinelRef} style={{ height: 1 }} />
```

## 4. When to use it in real life

- **Infinite scroll feeds** — Instagram-style timelines: observe a sentinel element near the bottom of the list to trigger loading the next page of posts.
- **Lazy-loading images/videos** — only fetch/decode media when a post scrolls into view, cutting initial page weight and bandwidth.
- **Auto-play/pause videos in feed** — play a reel/video only when it's sufficiently visible (common `threshold: 0.5+`), pause when scrolled away — exactly how Instagram/TikTok-style feeds behave.
- **"Seen" / view-count tracking & ad impressions** — mark a post as "viewed" or fire an analytics/impression event only once it's been visibly on-screen for a moment.
- **Sticky/reveal UI animations** — trigger fade-in/slide-in animations as cards scroll into the viewport.
- **Sticky header/nav state changes** — detect when a hero section scrolls out of view to swap a transparent header for a solid one.

---

## Summary

The Intersection Observer API lets you asynchronously detect when an element enters or exits the viewport (or another container) without expensive scroll-event polling.

- **What**: A browser API (`IntersectionObserver`) that observes target elements and fires a callback with visibility data (`isIntersecting`, `intersectionRatio`) when they cross a configured `threshold`/`rootMargin` relative to a `root`.
- **Why**: Far more performant than manual `scroll` + `getBoundingClientRect()` checks — it's async, browser-optimized, and avoids layout thrashing and battery drain.
- **How**: Create an observer with a callback and options (`root`, `rootMargin`, `threshold`), call `observer.observe(el)` on target elements, react to `entry.isIntersecting` in the callback, and `disconnect()`/`unobserve()` when done.
- **When**: Infinite-scroll feeds (load more posts), lazy-loading images/video in a social feed, auto-play/pause of reels based on visibility, view/impression tracking, scroll-triggered animations, and sticky-header visibility toggling.

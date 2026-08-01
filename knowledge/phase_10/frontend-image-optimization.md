# Frontend Image Optimization

## What is it
Frontend image optimization is the set of techniques for delivering images to the browser in the smallest, fastest-loading form that still looks correct — choosing the right format, size, resolution, and loading strategy for each image rather than shipping one large original file to every device.

## Why use it
- **Performance**: images are typically the largest byte contributor to a page; unoptimized images directly hurt Largest Contentful Paint (LCP) and page load time.
- **Bandwidth/cost**: smaller payloads reduce data transfer for users (especially mobile) and egress cost for the server/CDN.
- **UX**: faster perceived load, less layout jank (via reserved dimensions), smoother scrolling feeds (e.g., Instagram-style grids).
- **SEO/Core Web Vitals**: Google ranks and reports on LCP/CLS, both of which images heavily influence.

## How to use it
1. **Modern formats**: serve WebP/AVIF with a `<picture>`/`srcset` fallback to JPEG/PNG for unsupported browsers.
2. **Responsive images**: use `srcset` + `sizes` so the browser picks the right resolution for its viewport/DPR instead of downloading a huge desktop image on mobile.
3. **Lazy loading**: `loading="lazy"` (or an IntersectionObserver) for below-the-fold images so they don't block initial render.
4. **Reserve dimensions**: set explicit `width`/`height` (or `aspect-ratio`) to prevent layout shift (CLS) while the image loads.
5. **Compression**: compress at upload/build time (tools like `sharp`, `imagemin`, or an image CDN's on-the-fly resizing) instead of shipping raw originals.
6. **CDN-backed resizing**: request pre-resized/pre-compressed variants from a CDN or image service (ties into cdn-for-media-url) instead of resizing full-size images client-side.
7. **Priority hints**: mark the hero/above-the-fold image with `fetchpriority="high"` and preload it; lazy-load everything else.
8. **Placeholders**: use blurred low-res placeholders (LQIP) or dominant-color placeholders while the real image loads, common in feed UIs.

## When to use it in real life
- Social feeds/grids with many images per screen (Instagram-like post grids, profile avatars, stories thumbnails) — biggest real-world win.
- E-commerce product listing pages with many thumbnails.
- Any marketing/landing page where LCP is measured and matters for conversion or SEO.
- Skip heavy optimization pipelines for low-traffic internal admin tools where a few unoptimized images won't meaningfully affect UX.

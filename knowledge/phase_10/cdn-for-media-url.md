# CDN for Media URL

## What is it
A CDN (Content Delivery Network) is a geographically distributed network of edge/proxy servers that cache and serve static media (images, videos, avatars, thumbnails) closer to end users, instead of every request hitting the origin storage (e.g., S3) or app server directly. The app returns a CDN URL (e.g., `https://cdn.myapp.com/posts/abc.jpg`) instead of a raw storage URL.

## Why use it
- **Latency**: edge nodes are physically closer to users worldwide, so media loads faster than a single-region origin.
- **Offload origin**: caches responses so repeat requests don't hit storage/backend, reducing load and bandwidth cost.
- **Scalability**: absorbs traffic spikes (viral content) without hammering origin or DB.
- **Cost**: CDN egress is often cheaper than direct storage egress at scale.
- **Extra features**: on-the-fly image resizing, HTTPS termination, DDoS protection, signed URLs for access control.

## How to use it
1. Store original media in object storage (S3, GCS, Azure Blob).
2. Put a CDN (CloudFront, Cloudflare, Fastly, Akamai) in front of that origin.
3. Application layer returns the CDN URL, not the raw bucket URL — build it in a mapper/resolver (e.g., `s3Key` → `https://cdn.domain.com/{s3Key}`).
4. Configure cache headers (`Cache-Control`, `ETag`) to control how long the CDN caches content.
5. For private/expiring content, use signed URLs/cookies (CloudFront signed URL, S3 pre-signed URL through the CDN) so links expire after N minutes.
6. Invalidate/purge CDN cache on media update or deletion so stale content isn't served.
7. In a Spring Boot app: persist only the object key in the DB, and construct the full CDN URL in the response DTO/mapper layer — never persist the CDN domain itself, so the CDN provider can be swapped without a data migration.

## When to use it in real life
- Social media apps (Instagram-like) serving profile pictures, post images, reel thumbnails to a global user base.
- E-commerce product images.
- Video streaming platforms delivering HLS/DASH segments.
- Any app where static asset load time affects UX/Core Web Vitals across regions.
- Skip it for small internal tools with few users in one region — the added complexity isn't worth it.

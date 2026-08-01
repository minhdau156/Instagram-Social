# HTTP Response Compression

## What is it?
A mechanism where the server compresses the response body (JSON, HTML, CSS, JS, etc.) before sending it over the network, and the client decompresses it upon receipt. Common algorithms: **Gzip**, **Deflate**, **Brotli (br)**. The client advertises support via the `Accept-Encoding` request header; the server responds with `Content-Encoding: gzip` (or `br`) plus the compressed bytes.

## Why use it?
- Reduces payload size — text formats (JSON, HTML, XML) often compress 60-90%, cutting bandwidth usage and cost.
- Faster response times — smaller payloads transfer faster, especially on slow/high-latency networks.
- Improves perceived performance for APIs returning large JSON arrays/pages.
- Cost savings at scale — less data transferred means lower egress/CDN costs.

## How to use it

### Spring Boot (server-side, easiest way)
```yaml
server:
  compression:
    enabled: true
    mime-types: application/json,application/xml,text/html,text/plain
    min-response-size: 1024   # only compress responses > 1KB
```
Spring Boot's embedded Tomcat/Jetty/Undertow negotiates automatically based on the client's `Accept-Encoding` header — no code changes needed.

### Manual/Servlet Filter approach
Use a `CompressingFilter` (e.g., `com.github.ziplet:ziplet`) when finer control is needed outside Boot's built-in support.

### Infrastructure layer (often preferred in production)
Enable compression at Nginx, API Gateway, CDN (CloudFront, Cloudflare), or load balancer level instead of the app — offloads CPU cost from the application server and centralizes config.

### Client-side
Most HTTP clients (RestTemplate, WebClient, OkHttp, Axios, browsers) automatically send `Accept-Encoding` and decompress transparently.

## When to use it in real life
- REST APIs returning large JSON payloads (list endpoints, search results, reports).
- Public-facing web apps serving HTML/CSS/JS bundles.
- Mobile clients on constrained/bandwidth-limited networks.
- **Avoid** for already-compressed payloads (images, videos, zip, protobuf) — double compression wastes CPU for no gain.
- **Avoid** for very small responses — compression overhead can exceed savings (use `min-response-size` thresholds).
- **Security note**: be cautious combining compression with sensitive/reflected data over TLS (BREACH attack) — disable compression on endpoints mixing secret tokens with attacker-controlled input.

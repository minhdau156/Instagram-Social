# TASK-10.9 — HTTP response compression & payload slimming

## Overview

The feed endpoint returns JSON with large nested objects for every post, including fields the frontend never reads. Enabling gzip compression in Spring Boot is a two-line YAML change that tells the embedded Tomcat server to compress responses before sending them. Alongside that, auditing the response DTOs to drop unused fields reduces the uncompressed payload size so compression has less to work with in the first place. Together these changes can cut the feed response size by 50–80% with no visible change to the user.

---

## Level

Core · Pairs with [TASK-10.5 CDN-backed media URLs](TASK-10.5-cdn-media-urls.md)

---

## Why

Feed and profile responses are JSON-heavy. A feed page with 20 posts might return 40–80 KB of JSON, and mobile users on 4G download this on every scroll. Gzip/Brotli compress text by exploiting repeated patterns — JSON with repetitive field names like `"created_at"`, `"user_id"`, `"is_liked"` compresses very well, often reaching 5:1 or better ratios. Trimming unused fields reduces payload size with almost no code — it also reduces the risk that a client accidentally depends on a field that was never meant to be part of the public API contract.

---

## Prerequisites

- The backend is running and returns JSON responses from the feed and profile endpoints.
- You can use `curl` or Invoke-WebRequest to inspect response headers.
- Familiarity with HTTP `Accept-Encoding: gzip` and `Content-Encoding: gzip` headers.

**Concepts to skim:**
- Gzip: a compression algorithm that reduces file sizes by encoding repeated sequences. Supported by all browsers and HTTP clients since the early 2000s.
- Brotli: a newer compression algorithm by Google that achieves slightly better ratios than gzip. Requires HTTPS; not supported by Tomcat's built-in compression (use nginx or a CDN for Brotli).
- `Content-Encoding: gzip`: the HTTP response header that tells the client "this body is gzip-compressed". The client decompresses it automatically.
- `Accept-Encoding: gzip, deflate, br`: the HTTP request header that browsers send automatically to declare which compression formats they accept.
- `min-response-size`: the Spring Boot compression threshold. Responses smaller than this value are not compressed (compression overhead > savings for tiny responses).

---

## Files to Create / Modify

```
backend/src/main/resources/application.yml                                               (modify)
backend/src/main/java/com/instagram/adapter/in/web/dto/response/FeedPageResponse.java   (review)
backend/src/main/java/com/instagram/adapter/in/web/dto/response/PostResponse.java       (review)
docs/infra/cdn-setup.md                                                                  (modify — add Brotli nginx note)
```

---

## Step-by-Step

### 1. Enable response compression in application.yml

Open `backend/src/main/resources/application.yml`. Add the following under the `server:` key:

```yaml
server:
  port: 8080
  compression:
    enabled: true
    # Only compress responses larger than this threshold (bytes).
    # Small responses like 200 OK health checks are not worth compressing.
    min-response-size: 1024
    # Compress these content types. JSON and plain text compress very well.
    # Do NOT add image/* or video/* — binary media is already compressed
    # and attempting to gzip it wastes CPU with negligible size savings.
    mime-types:
      - application/json
      - application/xml
      - text/html
      - text/plain
      - text/xml
      - application/javascript
      - text/css
```

That is the entire change for enabling gzip. Spring Boot's embedded Tomcat will now:
1. Check if the client sent `Accept-Encoding: gzip` (all browsers do).
2. Check if the response body is larger than `min-response-size`.
3. Check if the `Content-Type` is in the `mime-types` list.
4. If all three conditions are true, compress the response body and add `Content-Encoding: gzip`.

---

### 2. Restart the backend and verify compression

Restart the backend. Then test with a feed request using PowerShell:

```powershell
$response = Invoke-WebRequest `
    -Uri "http://localhost:8080/api/v1/feed" `
    -Headers @{
        Authorization     = "Bearer <token>"
        "Accept-Encoding" = "gzip, deflate"
    } `
    -UseBasicParsing

$response.Headers["Content-Encoding"]
$response.Headers["Content-Length"]
```

**Passing result:** `Content-Encoding` should be `gzip` and `Content-Length` should be significantly smaller than the uncompressed JSON size.

Alternatively, using `curl` in Git Bash or WSL:

```bash
curl -s -I \
  -H "Authorization: Bearer <token>" \
  -H "Accept-Encoding: gzip" \
  http://localhost:8080/api/v1/feed \
  | grep -i "content-encoding"
```

Expected: `content-encoding: gzip`

---

### 3. Measure the compression ratio

Make two requests — one without `Accept-Encoding` and one with — and compare the sizes:

```powershell
# Without compression
$uncompressed = (Invoke-WebRequest `
    -Uri "http://localhost:8080/api/v1/feed" `
    -Headers @{Authorization="Bearer <token>"} `
    -UseBasicParsing).RawContentLength

# With compression
$compressed = (Invoke-WebRequest `
    -Uri "http://localhost:8080/api/v1/feed" `
    -Headers @{Authorization="Bearer <token>"; "Accept-Encoding"="gzip"} `
    -UseBasicParsing).RawContentLength

Write-Host "Uncompressed: $uncompressed bytes"
Write-Host "Compressed:   $compressed bytes"
Write-Host "Ratio: $([math]::Round($uncompressed / $compressed, 1))x"
```

A well-structured JSON response typically compresses at 4:1 to 8:1. If you see less than 2:1, check that the mime-type matches exactly (e.g., `application/json` vs. `application/json;charset=UTF-8` — Spring Boot's mime-type matching includes the charset suffix).

---

### 4. Audit response DTOs for unused fields

Open `backend/src/main/java/com/instagram/adapter/in/web/dto/response/PostResponse.java`.

Review each field and ask: "Does the frontend `PostCard.tsx` component read this field?" If a field is in the response but never referenced in any frontend file, it is a candidate for removal.

Search which fields are used:

```powershell
# For each field name in PostResponse, check if the frontend reads it
Select-String -Path frontend/src -Pattern "\.shareCount" -Recurse
Select-String -Path frontend/src -Pattern "\.viewCount" -Recurse
```

If `viewCount` is never read by any frontend component, remove it from `PostResponse`. This reduces both the SQL query (no need to SELECT it) and the JSON payload size.

**Principle: split list DTOs from detail DTOs.**

If `PostResponse` is used for both the feed list (20 posts per page) and the single-post detail view (`GET /api/v1/posts/{id}`), consider creating a lighter `PostSummaryResponse` for the feed:

```java
// PostSummaryResponse — lightweight, for list endpoints
public record PostSummaryResponse(
        UUID id,
        UUID userId,
        String username,
        String caption,         // truncated to 150 chars for feed preview
        int likeCount,
        int commentCount,
        List<MediaItemResponse> media
        // No: shareCount, saveCount, viewCount, location, updatedAt
) {
    public static PostSummaryResponse from(Post post, List<PostMedia> media) { ... }
}
```

The full `PostResponse` is used for the single-post detail view where all fields are displayed.

---

### 5. Document Brotli at the nginx layer

Open `docs/infra/cdn-setup.md` (created in TASK-10.5) and add a section:

```markdown
## Brotli Compression

Spring Boot's Tomcat only supports gzip. For Brotli compression (which achieves
10–20% better ratios than gzip), configure it at the nginx reverse proxy layer:

```nginx
# nginx.conf — Brotli requires the ngx_brotli module
brotli on;
brotli_comp_level 6;
brotli_types application/json application/javascript text/css text/html;

# Also keep gzip as a fallback for older clients
gzip on;
gzip_types application/json application/javascript text/css text/html;
gzip_comp_level 6;
```

Brotli requires HTTPS (it does not apply to HTTP/1.1 over plain text).
```

---

## Checklist

- [ ] Enable response compression in `application.yml` (`server.compression.enabled=true`, mime-types, `min-response-size`)
- [ ] Audit response DTOs for fields the client never reads; drop them or split a lighter list DTO from the detail DTO
- [ ] Confirm an `Accept-Encoding: gzip` request returns `Content-Encoding: gzip`
- [ ] (Optional) Document enabling Brotli at the nginx/CDN layer (pairs with TASK-10.5)

---

## How to Verify

**Compression active:**

```powershell
$r = Invoke-WebRequest `
    -Uri "http://localhost:8080/api/v1/feed" `
    -Headers @{Authorization="Bearer <token>"; "Accept-Encoding"="gzip"} `
    -UseBasicParsing
$r.Headers["Content-Encoding"]   # must be "gzip"
```

**Payload size reduction visible in DevTools:**

1. Open Chrome DevTools → Network tab.
2. Navigate to `http://localhost:5173` (the React frontend — it will call `/api/v1/feed` automatically).
3. Find the `/api/v1/feed` request.
4. In the response headers panel, confirm `Content-Encoding: gzip`.
5. In the Size column, the "transferred" value (compressed) should be noticeably smaller than the "resource" value (uncompressed). For example: `12.4 kB transferred` / `48.2 kB resource`.

**No compression on small responses:**

```powershell
# Health endpoint returns ~100 bytes — should NOT be compressed
$r = Invoke-WebRequest `
    -Uri "http://localhost:8080/actuator/health" `
    -Headers @{"Accept-Encoding"="gzip"} `
    -UseBasicParsing
$r.Headers["Content-Encoding"]   # should be null/absent
```

---

## Notes / Gotchas

**"The response is not gzip even after enabling compression."**
Check:
1. The client is sending `Accept-Encoding: gzip`. PowerShell's `Invoke-WebRequest` does not add this header by default — add it explicitly.
2. The response body is larger than `min-response-size`. Try setting `min-response-size: 1` temporarily to verify compression works for any size response.
3. The `Content-Type` matches one of the `mime-types` list entries. Log the actual Content-Type the response is returning: it may be `application/json;charset=UTF-8` while you have `application/json` in the list. Spring Boot 3.x handles this correctly, but older versions need both forms.

**"Gzip compression for images wastes CPU."**
Never add `image/jpeg`, `image/png`, `image/webp`, or `video/mp4` to the `mime-types` list. These formats are already internally compressed. Attempting to gzip them again wastes CPU, may slightly inflate the size, and adds latency. The compression settings should only apply to text-based formats.

**"I'm seeing `Content-Encoding: gzip` but `Content-Length` is absent."**
When gzip is enabled with a streaming response (e.g., chunked transfer encoding), Tomcat cannot know the final compressed size in advance and omits `Content-Length`. The browser and HTTP clients handle this correctly. This is expected behavior.

**"Brotli in Spring Boot 3."**
As of Spring Boot 3.3.4 (the version in this project), the embedded Tomcat does not support Brotli natively. Brotli is available at the nginx or CDN layer as noted in step 5.

**Cross-task references:**
- TASK-10.5 (CDN URLs) is complementary — CDNs also compress responses at the edge and serve them from edge nodes.
- TASK-10.8 (keyset pagination) reduces the number of fields returned per page by giving you control over what the page contains, further reducing payload size.

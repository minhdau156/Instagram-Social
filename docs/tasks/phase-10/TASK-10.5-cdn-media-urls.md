# TASK-10.5 — CDN-backed media URLs

## Overview

Every uploaded image and video URL currently points directly at the MinIO server running on `localhost:9000`. In production this means every image request hits the application's origin server, which is slow for users far away, expensive in bandwidth, and a single point of failure. A Content Delivery Network (CDN) caches media at edge nodes close to each user so the origin server only handles the first request for each file. This task updates `MinioStorageAdapter` to produce URLs that point at the CDN base URL instead of directly at MinIO, and documents a simple CDN proxy setup for reference.

---

## Level

Core · Pairs with [TASK-10.9 response compression](TASK-10.9-response-compression.md)

---

## Why

Serving images directly from the app server is slow and costly. When a user in Tokyo requests an image stored in a US data center, every byte travels across the Pacific on every request. A CDN node in Tokyo caches the image after the first request: subsequent requests are served locally with millisecond latency instead of 150+ ms round-trip. The app server never touches those bytes again. This task is a configuration change — it does not move the files, it just changes which URL the server hands back to the client.

---

## Prerequisites

- Understand the current flow: the frontend calls `POST /api/v1/media/upload-url` to get a presigned PUT URL, uploads directly to MinIO, then stores the returned object URL as `media_url` in the post.
- Know where URLs are generated: `MinioStorageAdapter.generatePresignedPutUrl()` in `backend/src/main/java/com/instagram/adapter/out/storage/MinioStorageAdapter.java`.
- Know where URLs are returned: `GenerateUploadUrlUseCase` → `MediaController` → `UploadUrlResponse`.

**Concepts to skim:**
- CDN (Content Delivery Network): a geographically distributed network of servers that cache static content (images, videos, JS bundles) close to end users.
- Origin server: the authoritative source of a file — in this project, MinIO.
- Edge node / PoP (Point of Presence): a CDN server in a specific geographic location.
- Presigned URL: a time-limited, cryptographically signed URL that allows a client to upload or download an object directly to/from object storage without going through your application server.
- CloudFront / MinIO CDN proxy: AWS CloudFront can be placed in front of MinIO or S3 as a CDN layer; Cloudflare or nginx can serve the same role in a self-hosted setup.

---

## Files to Create / Modify

```
backend/src/main/resources/application.yml                                              (modify)
backend/src/main/resources/application-local.yml                                        (modify)
backend/src/main/java/com/instagram/adapter/out/storage/MinioStorageAdapter.java        (modify)
docs/infra/cdn-setup.md                                                                  (new)
frontend/.env.example                                                                    (modify — add VITE_CDN_BASE_URL)
```

---

## Step-by-Step

### 1. Add the CDN base URL to application configuration

Open `backend/src/main/resources/application.yml`. Add a new property under the `app:` section:

```yaml
app:
  minio:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
    bucket: ${MINIO_BUCKET:instagram-media}
    # CDN base URL for public media delivery. In local dev this equals the MinIO
    # endpoint so URLs resolve without a CDN. In production set this to the
    # CloudFront distribution URL (e.g. https://d1234abcd.cloudfront.net).
    cdn-base-url: ${CDN_BASE_URL:${MINIO_ENDPOINT:http://localhost:9000}}
```

In `application-local.yml`, confirm the local default:

```yaml
app:
  minio:
    cdn-base-url: http://localhost:9000   # local: serve directly from MinIO
```

This means in local development nothing changes — the CDN URL equals the MinIO URL. In production you set `CDN_BASE_URL=https://d1234abcd.cloudfront.net` as an environment variable.

---

### 2. Update MinioStorageAdapter to use the CDN base URL

Open `backend/src/main/java/com/instagram/adapter/out/storage/MinioStorageAdapter.java`.

The current adapter injects `endpoint` and `bucket`. Add a third injected value for `cdn-base-url`:

```java
@Component
public class MinioStorageAdapter implements MediaStoragePort {

    private final MinioClient minioClient;
    private final String bucket;
    private final String endpoint;
    private final String cdnBaseUrl;

    public MinioStorageAdapter(
            MinioClient minioClient,
            @Value("${app.minio.bucket}") String bucket,
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.cdn-base-url}") String cdnBaseUrl) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.endpoint = endpoint;
        this.cdnBaseUrl = cdnBaseUrl;
    }
```

Now update `uploadFile` so the returned URL uses the CDN base rather than the MinIO endpoint:

```java
@Override
public String uploadFile(String key, byte[] data, String contentType) {
    try {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .stream(new ByteArrayInputStream(data), data.length, -1)
                .contentType(contentType)
                .build());
        // Use CDN base URL so clients fetch from the CDN edge, not the origin
        return String.format("%s/%s/%s", cdnBaseUrl, bucket, key);
    } catch (Exception e) {
        throw new MediaUploadException("Failed to upload file to MinIO");
    }
}
```

For `generatePresignedPutUrl`, the presigned URL must still point at MinIO (the CDN does not accept PUT uploads — it only serves GET requests). However, the URL stored in the database after upload should be the CDN URL. This is handled correctly because `uploadFile` now returns the CDN URL; the presigned PUT path goes directly to MinIO.

If `generatePresignedPutUrl` is also used to generate a publicly-readable GET URL (check callers), you should rewrite the returned URL to replace the MinIO origin with the CDN base:

```java
@Override
public String generatePresignedPutUrl(String key, Duration expiry) {
    try {
        String presignedUrl = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(bucket)
                        .object(key)
                        .expiry((int) expiry.toSeconds(), TimeUnit.SECONDS)
                        .build());
        // The presigned PUT URL must target MinIO directly (CDN does not accept PUTs)
        return presignedUrl;
    } catch (Exception e) {
        throw new MediaUploadException("Failed to generate presigned put URL for MinIO");
    }
}

/**
 * Returns the public CDN URL for an already-uploaded object.
 * Clients should store this URL in the database (not the presigned PUT URL).
 */
public String getPublicUrl(String key) {
    return String.format("%s/%s/%s", cdnBaseUrl, bucket, key);
}
```

Add `getPublicUrl(String key)` to the `MediaStoragePort` out-port interface if other adapters will need it.

---

### 3. Update the frontend environment example

Open (or create) `frontend/.env.example`:

```
# CDN base URL for media delivery. In local dev set to MinIO endpoint.
# In production set to your CloudFront or Cloudflare distribution URL.
VITE_CDN_BASE_URL=http://localhost:9000
```

The frontend does not currently construct media URLs itself (the backend returns them), so this env var is documentation for developers setting up a production environment. If any frontend code ever needs to construct a thumbnail URL client-side, it should read `import.meta.env.VITE_CDN_BASE_URL`.

---

### 4. Create docs/infra/cdn-setup.md

Create the directory if it does not exist:

```powershell
New-Item -ItemType Directory -Force -Path C:\workspace\Instagram-Social\docs\infra
```

Then create the file `docs/infra/cdn-setup.md` with a reference configuration:

```markdown
# CDN Setup Guide

## Overview

Media files are stored in MinIO (local) or AWS S3 (production).
The application's `CDN_BASE_URL` environment variable controls which
host is embedded in media URLs returned to clients.

## Local Development

No CDN required. Set:

```env
CDN_BASE_URL=http://localhost:9000
MINIO_ENDPOINT=http://localhost:9000
```

## Production — AWS CloudFront + S3

1. Create an S3 bucket (replace MinIO with S3 by swapping the `MinioStorageAdapter`
   for an S3 adapter using `software.amazon.awssdk:s3`).
2. Create a CloudFront distribution pointing at the S3 bucket as origin.
3. Set `CDN_BASE_URL=https://d1234abcd.cloudfront.net` in the production environment.
4. The bucket should be private; CloudFront uses an Origin Access Control (OAC) to read it.

## Production — Self-Hosted (nginx + MinIO)

Place nginx in front of MinIO as a caching reverse proxy:

```nginx
server {
    listen 443 ssl;
    server_name cdn.example.com;

    proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=media_cache:100m
                     max_size=10g inactive=7d use_temp_path=off;

    location / {
        proxy_pass http://minio:9000;
        proxy_cache media_cache;
        proxy_cache_valid 200 7d;
        proxy_cache_use_stale error timeout updating;
        add_header X-Cache-Status $upstream_cache_status;
    }
}
```

Set `CDN_BASE_URL=https://cdn.example.com` in your production `.env`.
```

---

## Checklist

- [x] Update `MinioStorageAdapter.generatePresignedPutUrl` to produce URLs rooted at `VITE_CDN_BASE_URL` env var
- [x] Add CloudFront / MinIO CDN proxy config example to `docs/` (optional: `docs/infra/cdn-setup.md`)

---

## How to Verify

**Step 1 — upload an image and check the returned URL:**

```powershell
# 1. Get a presigned URL
$upload = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/media/upload-url" `
    -Method POST `
    -Headers @{Authorization="Bearer <token>"; "Content-Type"="application/json"} `
    -Body '{"filename":"test.jpg","contentType":"image/jpeg"}'

$upload.data.uploadUrl   # should be a MinIO presigned PUT URL (direct to MinIO for upload)
$upload.data.publicUrl   # should be rooted at CDN_BASE_URL (localhost:9000 in local dev)
```

**Step 2 — confirm environment variable substitution works:**

In `application-local.yml`, temporarily change `cdn-base-url` to a fake CDN URL:

```yaml
app:
  minio:
    cdn-base-url: https://fake-cdn.example.com
```

Restart the backend, upload a file, and confirm the returned URL starts with `https://fake-cdn.example.com`. Then revert the change.

**Step 3 — confirm the image loads through the CDN URL:**

In local dev the "CDN" URL equals `http://localhost:9000/instagram-media/<key>`. Open that URL in a browser — you should see the uploaded image.

**Passing result:** The URL returned from the API starts with the configured `CDN_BASE_URL` and the image renders in the browser.

---

## Notes / Gotchas

**"The presigned PUT URL still points at MinIO — is that correct?"**
Yes. CloudFront and other CDNs only cache GET responses — they do not accept PUT requests for upload. The upload must go directly to the origin (MinIO or S3). After upload, the stored `media_url` in the database should be the CDN URL, not the presigned URL. The `getPublicUrl(key)` method in step 2 handles this distinction.

**"Local MinIO presigned URLs contain `localhost` — they break in Docker."**
If your backend runs inside Docker and MinIO runs as a sibling container, the presigned URL may contain `localhost` which a browser (outside Docker) cannot resolve. Use `MINIO_ENDPOINT=http://minio:9000` for internal communication and `CDN_BASE_URL=http://localhost:9000` for the public-facing URL. This is an address rewriting problem common in local container setups.

**"Do I need to update the database for existing URLs?"**
Existing `media_url` values in the database point at the old origin. A migration to rewrite them is out of scope for this task. In practice, a CDN is transparent at the origin level — you can configure CloudFront to point at the same MinIO/S3 origin and existing URLs that point to the origin will continue to work (they just bypass the CDN). New uploads will use CDN URLs going forward.

**Cross-task references:**
- TASK-10.9 (response compression) pairs with this task — together they reduce both image payload size (CDN) and API JSON payload size (compression).
- TASK-10.12 (chunked upload) extends `MinioStorageAdapter` with multipart upload support and will need to follow the same CDN URL pattern.

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **What is a CDN** — edge caching of static assets, and why it cuts latency — https://developer.mozilla.org/en-US/docs/Glossary/CDN
- **Caching static assets** — long `max-age` + content hashing — https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cache-Control
- **CloudFront + S3 overview** — fronting object storage with a CDN — https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Introduction.html

### Official docs (code reference)
- **CloudFront signed URLs (private content)** — https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/PrivateContent.html
- **MinIO documentation** — https://min.io/docs/minio/linux/index.html

# CDN Setup Guide

## Overview

Media files are stored in MinIO (local) or AWS S3 (production).
The application's `CDN_BASE_URL` environment variable controls which
host is embedded in media URLs returned to clients.

`MinioStorageAdapter.uploadFile` returns a URL rooted at `cdn-base-url`.
`generatePresignedPutUrl` always targets MinIO directly — CDNs only serve GET requests,
not PUT uploads.

---

## Local Development

No CDN required. The default configuration in `application-local.yml` sets:

```yaml
app:
  minio:
    cdn-base-url: http://localhost:9000
```

URLs returned by the API will be `http://localhost:9000/instagram-media/<key>`,
which resolves directly to MinIO running on your machine.

**Docker note:** If the backend runs inside Docker and MinIO is a sibling container,
the presigned PUT URL may contain `localhost` which a browser outside Docker cannot
resolve. Use separate values:

```env
MINIO_ENDPOINT=http://minio:9000    # internal — used by the backend to talk to MinIO
CDN_BASE_URL=http://localhost:9000  # external — embedded in URLs returned to the browser
```

---

## Production — AWS CloudFront + S3

1. Create an S3 bucket (replace MinIO with S3 by swapping `MinioStorageAdapter`
   for an S3 adapter using `software.amazon.awssdk:s3`).
2. Create a CloudFront distribution pointing at the S3 bucket as origin.
   Use an Origin Access Control (OAC) so the bucket stays private.
3. Set the environment variable in your production deployment:

```env
CDN_BASE_URL=https://d1234abcd.cloudfront.net
```

Uploads still go directly to S3 via presigned PUT URL. CloudFront only serves reads.

---

## Production — Self-Hosted (nginx + MinIO)

Place nginx in front of MinIO as a caching reverse proxy.

**`/etc/nginx/nginx.conf`** — `proxy_cache_path` must be at the `http` level:

```nginx
http {
    proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=media_cache:100m
                     max_size=10g inactive=7d use_temp_path=off;

    include /etc/nginx/conf.d/*.conf;
}
```

**`/etc/nginx/conf.d/cdn.conf`** — the virtual host:

```nginx
server {
    listen 443 ssl;
    server_name cdn.example.com;

    location / {
        proxy_pass http://minio:9000;
        proxy_cache media_cache;
        proxy_cache_valid 200 7d;
        proxy_cache_use_stale error timeout updating;
        add_header X-Cache-Status $upstream_cache_status;
    }
}
```

Set the environment variable:

```env
CDN_BASE_URL=https://cdn.example.com
```

---

## Verifying the Configuration

**Step 1 — check the URL returned by the API:**

```powershell
$upload = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/media/upload-url" `
    -Method POST `
    -Headers @{Authorization="Bearer <token>"; "Content-Type"="application/json"} `
    -Body '{"filename":"test.jpg","contentType":"image/jpeg"}'

$upload.data.uploadUrl   # MinIO presigned PUT URL (direct to MinIO for upload)
$upload.data.publicUrl   # rooted at CDN_BASE_URL
```

**Step 2 — smoke-test a fake CDN URL:**

Temporarily set in `application-local.yml`:

```yaml
app:
  minio:
    cdn-base-url: https://fake-cdn.example.com
```

Restart the backend, upload a file, and confirm the returned URL starts with
`https://fake-cdn.example.com`. Revert afterwards.

---

## Cross-Task References

- **TASK-10.9** (response compression) — reduces API JSON payload size; pairs with this task to cut both image bandwidth and JSON bandwidth.
- **TASK-10.12** (chunked/multipart upload) — extends `MinioStorageAdapter` with multipart support; must follow the same CDN URL pattern (`getPublicUrl` for stored URLs, presigned PUT for uploads).

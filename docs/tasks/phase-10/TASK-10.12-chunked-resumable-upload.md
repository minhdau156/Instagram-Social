# TASK-10.12 — Chunked / resumable large-file upload (up to 2 GB)

## Overview

The existing `POST /api/v1/media/upload-url` generates a single presigned PUT URL for direct MinIO uploads. A single PUT request for a 2 GB video will hit Spring Boot's multipart size limit (`10MB` in `application.yml`), time out on a slow connection, and force the user to restart from zero if anything goes wrong. MinIO (like S3) supports multipart upload: the file is split into parts of 5–10 MB, each part is uploaded independently with its own presigned URL, and MinIO reassembles them into one object once all parts are present. If the network drops after part 7, only parts 8+ need to be re-uploaded.

---

## Level

Stretch · Pairs with [TASK-10.10 async processing](TASK-10.10-async-processing-virtual-threads.md) / [TASK-10.13 Spring Batch](TASK-10.13-spring-batch-bulk-import.md)

---

## Why

A single multipart POST of a 2 GB video times out, exhausts server memory, and forces the user to restart from zero on any network blip. MinIO's multipart upload protocol lets the client upload the file in small parts that can be retried individually. The server tracks which parts have been received (in the `upload_sessions` table), so if the user pauses and resumes, the client can ask `GET /uploads/{uploadId}/parts` to find out which parts arrived and resume from the first missing one. Chunked upload is the industry standard for large media uploads — S3, GCS, and MinIO all support it natively.

---

## Prerequisites

- The MinIO Java SDK (`io.minio:minio:8.5.11`) is already in `pom.xml`.
- The `MediaStoragePort` out-port and `MinioStorageAdapter` are already in place (from Phase 2).
- The existing `application.yml` has `spring.servlet.multipart.max-file-size: 10MB` — multipart upload bypasses this limit because the client uploads directly to MinIO, not through the Spring Boot server.
- Familiarity with the MinIO multipart upload lifecycle: `createMultipartUpload` → `n × uploadPart` → `completeMultipartUpload` (or `abortMultipartUpload`).

**Concepts to skim:**
- Multipart upload: a protocol where a file is split into numbered parts (1-based). Each part is uploaded independently. The storage service assembles them in order when you call `complete`. Parts can be uploaded in parallel.
- `uploadId`: a server-assigned string that identifies the in-progress multipart upload. All part uploads reference this ID.
- ETag: a hash of each uploaded part's bytes, returned by the storage service on each `uploadPart`. The client must send all ETags when calling `complete`.
- Presigned `uploadPart` URL: a time-limited signed URL that lets the browser PUT a specific part number directly to MinIO without any server intermediary.
- `upload_sessions` table: an application-level table (not in MinIO) that tracks the `uploadId`, object key, expected part count, and status. It enables the resume flow.

---

## Files to Create / Modify

```
backend/src/main/java/com/instagram/domain/port/out/MediaStoragePort.java             (modify — add multipart methods)
backend/src/main/java/com/instagram/adapter/out/storage/MinioStorageAdapter.java       (modify — implement multipart methods)
backend/src/main/resources/db/migration/V5__upload_sessions.sql                        (new)
backend/src/main/java/com/instagram/adapter/out/persistence/entity/UploadSessionJpaEntity.java  (new)
backend/src/main/java/com/instagram/adapter/out/persistence/repository/UploadSessionJpaRepository.java  (new)
backend/src/main/java/com/instagram/adapter/in/web/MediaController.java                (modify — add multipart endpoints)
backend/src/main/java/com/instagram/adapter/in/web/dto/request/InitiateUploadRequest.java  (new)
backend/src/main/java/com/instagram/adapter/in/web/dto/request/CompleteUploadRequest.java  (new)
backend/src/main/java/com/instagram/adapter/in/web/dto/response/InitiateUploadResponse.java  (new)
backend/src/main/java/com/instagram/adapter/in/web/dto/response/UploadPartsResponse.java    (new)
```

---

## Step-by-Step

### 1. Add multipart methods to MediaStoragePort

Open `backend/src/main/java/com/instagram/domain/port/out/MediaStoragePort.java`.

Add four new methods for the multipart lifecycle:

```java
package com.instagram.domain.port.out;

import java.time.Duration;
import java.util.List;

public interface MediaStoragePort {

    // Existing methods:
    String uploadFile(String key, byte[] data, String contentType);
    String generatePresignedPutUrl(String key, Duration expiry);

    // New multipart methods:

    /** Initiates a multipart upload and returns the uploadId assigned by MinIO. */
    String initiateMultipartUpload(String key, String contentType);

    /**
     * Generates a presigned URL for uploading one part.
     *
     * @param key        the object key
     * @param uploadId   the uploadId from initiateMultipartUpload
     * @param partNumber 1-based part number (1–10,000)
     * @param expiry     URL expiry duration
     */
    String generatePresignedPartUrl(String key, String uploadId, int partNumber, Duration expiry);

    /**
     * Completes the multipart upload by assembling all parts.
     * Returns the public CDN URL of the assembled object.
     *
     * @param key       the object key
     * @param uploadId  the uploadId
     * @param partETags list of (partNumber, etag) pairs in ascending part-number order
     */
    String completeMultipartUpload(String key, String uploadId, List<PartETag> partETags);

    /** Aborts a multipart upload and frees the partial data in MinIO. */
    void abortMultipartUpload(String key, String uploadId);

    /** Value type for part number + ETag pairs used in completeMultipartUpload. */
    record PartETag(int partNumber, String etag) {}
}
```

---

### 2. Implement multipart methods in MinioStorageAdapter

Open `backend/src/main/java/com/instagram/adapter/out/storage/MinioStorageAdapter.java`.

Add the four implementations. The MinIO Java SDK provides all required methods:

```java
import io.minio.CreateMultipartUploadResponse;
import io.minio.UploadPartArgs;
import io.minio.CompleteMultipartUploadArgs;
import io.minio.AbortMultipartUploadArgs;
import io.minio.messages.Part;

// Inside MinioStorageAdapter:

@Override
public String initiateMultipartUpload(String key, String contentType) {
    try {
        CreateMultipartUploadResponse response = minioClient.createMultipartUpload(
                io.minio.CreateMultipartUploadArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .build());
        return response.result().uploadId();
    } catch (Exception e) {
        throw new MediaUploadException("Failed to initiate multipart upload: " + e.getMessage());
    }
}

@Override
public String generatePresignedPartUrl(String key, String uploadId,
                                        int partNumber, Duration expiry) {
    try {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(bucket)
                        .object(key)
                        .expiry((int) expiry.toSeconds(), TimeUnit.SECONDS)
                        .extraQueryParams(Map.of(
                                "uploadId", uploadId,
                                "partNumber", String.valueOf(partNumber)))
                        .build());
    } catch (Exception e) {
        throw new MediaUploadException("Failed to generate presigned part URL: " + e.getMessage());
    }
}

@Override
public String completeMultipartUpload(String key, String uploadId,
                                       List<PartETag> partETags) {
    try {
        Part[] parts = partETags.stream()
                .map(p -> new Part(p.partNumber(), p.etag()))
                .toArray(Part[]::new);
        minioClient.completeMultipartUpload(
                CompleteMultipartUploadArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .uploadId(uploadId)
                        .parts(parts)
                        .build());
        return String.format("%s/%s/%s", cdnBaseUrl, bucket, key);
    } catch (Exception e) {
        throw new MediaUploadException("Failed to complete multipart upload: " + e.getMessage());
    }
}

@Override
public void abortMultipartUpload(String key, String uploadId) {
    try {
        minioClient.abortMultipartUpload(
                AbortMultipartUploadArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .uploadId(uploadId)
                        .build());
    } catch (Exception e) {
        log.warn("Failed to abort multipart upload key={} uploadId={}: {}",
                key, uploadId, e.getMessage());
    }
}
```

Add the missing import for `Map`:

```java
import java.util.Map;
```

---

### 3. Create the Flyway migration for upload_sessions

Create `backend/src/main/resources/db/migration/V5__upload_sessions.sql`:

```sql
-- =============================================================================
-- V5 — Upload Sessions
-- Tracks in-progress multipart uploads for resumable large-file uploads.
-- =============================================================================

CREATE TABLE upload_sessions (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    upload_id       TEXT        NOT NULL UNIQUE,  -- MinIO-assigned multipart upload ID
    object_key      TEXT        NOT NULL,          -- MinIO object key
    content_type    VARCHAR(100) NOT NULL,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    total_parts     INT,                           -- expected part count (nullable until client knows)
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    -- status: IN_PROGRESS | COMPLETED | ABORTED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '24 hours'
);

CREATE INDEX idx_upload_sessions_user   ON upload_sessions (user_id);
CREATE INDEX idx_upload_sessions_status ON upload_sessions (status, expires_at);
```

---

### 4. Create the JPA entity and repository

Create `backend/src/main/java/com/instagram/adapter/out/persistence/entity/UploadSessionJpaEntity.java`:

```java
package com.instagram.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "upload_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadSessionJpaEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "upload_id", nullable = false, unique = true)
    private String uploadId;

    @Column(name = "object_key", nullable = false, columnDefinition = "TEXT")
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "total_parts")
    private Integer totalParts;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (expiresAt == null) expiresAt = OffsetDateTime.now().plusHours(24);
        if (status == null) status = "IN_PROGRESS";
    }
}
```

Create `backend/src/main/java/com/instagram/adapter/out/persistence/repository/UploadSessionJpaRepository.java`:

```java
package com.instagram.adapter.out.persistence.repository;

import com.instagram.adapter.out.persistence.entity.UploadSessionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UploadSessionJpaRepository extends JpaRepository<UploadSessionJpaEntity, UUID> {

    Optional<UploadSessionJpaEntity> findByUploadId(String uploadId);

    boolean existsByUploadIdAndUserId(String uploadId, UUID userId);
}
```

---

### 5. Add the multipart endpoints to MediaController

Open `backend/src/main/java/com/instagram/adapter/in/web/MediaController.java`.

Add three endpoints (initiate, list parts for resume, complete):

```java
// POST /api/v1/media/uploads — initiate a multipart upload
@PostMapping("/uploads")
@PreAuthorize("isAuthenticated()")
@Operation(summary = "Initiate a multipart upload session")
public ResponseEntity<ApiResponse<InitiateUploadResponse>> initiateUpload(
        @RequestBody @Valid InitiateUploadRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {

    UUID userId = UUID.fromString(userDetails.getUsername());

    // Validate file size cap: 2 GB
    if (request.totalSizeBytes() > 2L * 1024 * 1024 * 1024) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("File exceeds 2 GB limit"));
    }
    // Validate part size: 5–10 MB per part
    if (request.partSizeBytes() < 5 * 1024 * 1024 || request.partSizeBytes() > 10 * 1024 * 1024) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Part size must be between 5 MB and 10 MB"));
    }

    String key = "uploads/" + userId + "/" + UUID.randomUUID() + "/" + request.filename();
    String uploadId = mediaStoragePort.initiateMultipartUpload(key, request.contentType());

    UploadSessionJpaEntity session = UploadSessionJpaEntity.builder()
            .uploadId(uploadId)
            .objectKey(key)
            .contentType(request.contentType())
            .userId(userId)
            .totalParts(request.totalParts())
            .build();
    uploadSessionRepository.save(session);

    // Generate presigned URLs for all parts
    List<String> partUrls = IntStream.rangeClosed(1, request.totalParts())
            .mapToObj(partNum -> mediaStoragePort.generatePresignedPartUrl(
                    key, uploadId, partNum, Duration.ofHours(2)))
            .toList();

    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(new InitiateUploadResponse(uploadId, key, partUrls)));
}

// POST /api/v1/media/uploads/{uploadId}/complete
@PostMapping("/uploads/{uploadId}/complete")
@PreAuthorize("isAuthenticated()")
@Operation(summary = "Complete a multipart upload by providing all part ETags")
public ResponseEntity<ApiResponse<Map<String, String>>> completeUpload(
        @PathVariable String uploadId,
        @RequestBody @Valid CompleteUploadRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {

    UUID userId = UUID.fromString(userDetails.getUsername());

    UploadSessionJpaEntity session = uploadSessionRepository.findByUploadId(uploadId)
            .filter(s -> s.getUserId().equals(userId))
            .orElseThrow(() -> new IllegalArgumentException("Upload session not found"));

    List<MediaStoragePort.PartETag> partETags = request.parts().stream()
            .map(p -> new MediaStoragePort.PartETag(p.partNumber(), p.etag()))
            .toList();

    String publicUrl = mediaStoragePort.completeMultipartUpload(
            session.getObjectKey(), uploadId, partETags);

    session.setStatus("COMPLETED");
    uploadSessionRepository.save(session);

    return ResponseEntity.ok(ApiResponse.ok(Map.of("url", publicUrl)));
}
```

---

## Checklist

- [ ] Extend the storage out-port + `MinioStorageAdapter` to use multipart upload (`createMultipartUpload`, presigned `uploadPart` URLs, `completeMultipartUpload`, `abortMultipartUpload`)
- [ ] Add `MediaController` endpoints: `POST /api/v1/media/uploads` (initiate → `uploadId`), `GET .../uploads/{uploadId}/parts` (which parts exist, for resume), `POST .../uploads/{uploadId}/complete`
- [ ] Flyway migration for an `upload_session` table (uploadId, key, parts, status, created_at)
- [ ] Enforce a 2 GB total cap + a per-part size (e.g. 5–10 MB) and validate part numbers
- [ ] Abort/cleanup path for stale or cancelled sessions

---

## How to Verify

**Initiate and complete a 3-part upload:**

```powershell
# 1. Initiate (3 parts of 5 MB each = 15 MB total)
$init = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/media/uploads" `
    -Method POST `
    -Headers @{Authorization="Bearer <token>"; "Content-Type"="application/json"} `
    -Body '{"filename":"test.mp4","contentType":"video/mp4","totalParts":3,"totalSizeBytes":15728640,"partSizeBytes":5242880}'

$uploadId = $init.data.uploadId
$partUrls = $init.data.partUrls

# 2. Upload 3 fake 5 MB parts (random bytes) using the presigned URLs
1..3 | ForEach-Object {
    $data = New-Object byte[] (5 * 1024 * 1024)
    [System.Random]::new().NextBytes($data)
    Invoke-WebRequest -Uri $partUrls[$_ - 1] -Method PUT -Body $data -UseBasicParsing
}

# 3. Complete — in a real flow, each part upload returns an ETag header
# For this test, use placeholder ETags
$body = '{"parts":[{"partNumber":1,"etag":"etag1"},{"partNumber":2,"etag":"etag2"},{"partNumber":3,"etag":"etag3"}]}'
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/media/uploads/$uploadId/complete" `
    -Method POST `
    -Headers @{Authorization="Bearer <token>"; "Content-Type"="application/json"} `
    -Body $body
```

**Confirm session is in the database:**

```sql
SELECT upload_id, status, created_at FROM upload_sessions ORDER BY created_at DESC LIMIT 5;
```

**File size validation:**

```powershell
# 3 GB file — should return 400
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/media/uploads" `
    -Method POST `
    -Headers @{Authorization="Bearer <token>"; "Content-Type"="application/json"} `
    -Body '{"filename":"big.mp4","contentType":"video/mp4","totalParts":1,"totalSizeBytes":3221225472,"partSizeBytes":5242880}'
# Expected: 400 Bad Request
```

---

## Notes / Gotchas

**"MinIO's presigned `uploadPart` URL includes `uploadId` and `partNumber` as query parameters."**
The `extraQueryParams` map in `generatePresignedPartUrl` passes these to the MinIO SDK. Without them, the presigned URL points at the plain object PUT endpoint (overwrite), not the multipart part endpoint.

**"The client must send the ETag from each part upload response when calling `complete`."**
MinIO returns an `ETag` response header for each successful part upload. The client must store these ETags and send them in the `CompleteUploadRequest`. If any ETag is wrong, MinIO returns an error and the assembly fails. In the test above, placeholder ETags will cause the `complete` call to fail — use a real MinIO upload to get real ETags.

**"Stale upload sessions accumulate in the database."**
Add a scheduled cleanup job (TASK-10.48) that runs nightly:

```sql
SELECT upload_id, object_key FROM upload_sessions
WHERE status = 'IN_PROGRESS' AND expires_at < NOW();
```

For each row, call `mediaStoragePort.abortMultipartUpload(objectKey, uploadId)` to free the partial data in MinIO, then delete the row.

**"Part number validation — what are the limits?"**
MinIO follows the S3 spec: part numbers must be between 1 and 10,000 (inclusive). Each part must be at least 5 MB except the last part. The total upload must not exceed 5 TB. Enforce the 2 GB cap in the `InitiateUploadRequest` validation.

**Cross-task references:**
- TASK-10.10 (async processing) — thumbnail generation is triggered asynchronously after `complete` succeeds.
- TASK-10.48 (ShedLock scheduled jobs) — the stale session cleanup job described in the Gotchas section.
- TASK-10.5 (CDN URLs) — `completeMultipartUpload` returns a CDN URL for the assembled object.

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Resumable upload protocol** — the tus open standard for chunked, resumable uploads — https://tus.io/protocols/resumable-upload
- **HTTP Range requests** — `Content-Range` and partial transfers — https://developer.mozilla.org/en-US/docs/Web/HTTP/Range_requests
- **S3/MinIO multipart upload** — uploading a large object in parts, then completing — https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html

### Official docs (code reference)
- **AWS S3 multipart upload** — https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html
- **MinIO documentation** — https://min.io/docs/minio/linux/index.html

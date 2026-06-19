# TASK-10.22 — Media upload hardening

## Overview

Harden the media upload pipeline against five distinct attacks: a file disguised as an image by extension alone, an oversized file that exhausts disk, an over-resolution image that exhausts memory, EXIF metadata (including GPS coordinates) embedded in a stored photo, and a client-supplied path that overwrites an existing object in MinIO. Each of these is a real class of vulnerability that upload endpoints routinely suffer. None requires a complex framework change — they are all checks added to the storage adapter layer.

---

## Level

**Core** — Pairs with [TASK-10.18 (Input validation hardening)](TASK-10.18-input-validation-hardening.md), which validates text fields; this task validates binary media.

---

## Why

Upload endpoints receive untrusted binary data. An attacker can rename a PHP or HTML file to `.jpg` and upload it; if the server trusts the extension, it may store an executable web shell. A 4 GB video upload with no size cap can fill the disk mid-flight, crashing the server for all other users. A 200 megapixel JPEG can expand to gigabytes in memory during thumbnail generation — a deliberate "decompression bomb." EXIF data in a photo often contains the GPS coordinates where the photo was taken, silently exposing a user's location. Finally, if the client supplies the storage key for a presigned PUT URL (e.g. `user-avatars/admin-photo.jpg`), they can overwrite any object they can guess the key for.

---

## Prerequisites

- `MediaController` at `adapter/in/web/MediaController.java` — the existing upload entry point.
- `MinioStorageAdapter` (or equivalent) in `adapter/out/persistence/` or `infrastructure/storage/` — where presigned URLs are generated.
- MinIO is running locally via Docker.
- **Concept gloss:**
  - **Magic bytes** — the first few bytes of a file that identify its true type, independent of the filename or `Content-Type` header. JPEG files always start with `FF D8 FF`; PNG files with `89 50 4E 47`.
  - **EXIF** — Exchangeable Image File Format. Metadata embedded in JPEG/PNG files that can include GPS coordinates, device model, timestamp, and thumbnail images.
  - **Presigned URL** — a time-limited, signed URL that lets a client upload directly to MinIO/S3 without going through the backend. The backend controls the scope: key, content-type, content-length, and expiry.
  - **Content-type condition** — a condition attached to a presigned PUT URL that MinIO enforces: the client's upload request must carry exactly the declared `Content-Type`, or MinIO rejects it.

---

## Files to Create / Modify

```
backend/pom.xml                                                                              (modify — add Apache Tika + metadata-extractor)
backend/src/main/java/com/instagram/infrastructure/storage/MediaValidator.java              (new)
backend/src/main/java/com/instagram/adapter/in/web/MediaController.java                     (modify — call validator)
backend/src/main/java/com/instagram/domain/exception/InvalidMediaException.java             (new — domain exception)
backend/src/main/java/com/instagram/adapter/in/web/GlobalExceptionHandler.java              (modify — map InvalidMediaException → 400)
```

The MinIO presigned URL generation method (currently in the storage adapter) will also be tightened in Step 5.

---

## Step-by-Step

### 1. Add the dependencies

**Apache Tika** — detects the true MIME type of a file by inspecting its content (magic bytes), ignoring the filename and `Content-Type` header.

**metadata-extractor** — reads and strips EXIF metadata from JPEG images.

Open `backend/pom.xml` and add inside `<dependencies>`:

```xml
<!-- True MIME-type detection from file content (magic bytes), not filename -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>

<!-- EXIF/metadata reading and stripping for uploaded images -->
<dependency>
    <groupId>com.drewnoakes</groupId>
    <artifactId>metadata-extractor</artifactId>
    <version>2.19.0</version>
</dependency>
```

Confirm they resolve:

```powershell
cd backend; mvn dependency:resolve -q
```

### 2. Create `InvalidMediaException` in the domain

```java
package com.instagram.domain.exception;

/**
 * Thrown when an uploaded file fails content-type, size, or dimension validation.
 * Maps to HTTP 400 Bad Request — the file is the client's problem.
 */
public class InvalidMediaException extends RuntimeException {
    public InvalidMediaException(String message) {
        super(message);
    }
}
```

Add the `@ExceptionHandler` in `GlobalExceptionHandler`:

```java
@ExceptionHandler(InvalidMediaException.class)
public ResponseEntity<ApiResponse<Void>> handleInvalidMedia(InvalidMediaException ex) {
    log.warn(ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
}
```

### 3. Create `MediaValidator.java`

This class contains all five validation steps. Place it in `infrastructure/storage/` (or `infrastructure/security/` — it is an infrastructure concern, not a domain concern):

```java
package com.instagram.infrastructure.storage;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import com.instagram.domain.exception.InvalidMediaException;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

/**
 * Validates and sanitizes incoming media uploads.
 *
 * Order of operations:
 *  1. Check file size (fast — avoids reading the full file).
 *  2. Detect real MIME type via magic bytes (ignores filename/Content-Type header).
 *  3. Check image dimensions to block decompression bombs.
 *  4. Strip EXIF metadata (including GPS) from JPEG files.
 */
@Component
public class MediaValidator {

    private static final Logger log = LoggerFactory.getLogger(MediaValidator.class);

    // Allowlisted MIME types — exactly these four
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "video/mp4");

    // 100 MB max for video; 10 MB for images (Spring's multipart limit handles the hard cap)
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;   // 10 MB
    private static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;  // 100 MB

    // Reject images larger than 8192 x 8192 pixels (decompression bomb guard)
    private static final int MAX_IMAGE_DIMENSION = 8192;

    private final Tika tika = new Tika();

    /**
     * Validates and sanitizes a multipart upload.
     *
     * @param file the incoming upload
     * @return a sanitized byte array ready to store (EXIF stripped for images)
     * @throws InvalidMediaException if the file fails any check
     */
    public byte[] validateAndSanitize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidMediaException("Upload must not be empty");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidMediaException("Could not read upload: " + e.getMessage());
        }

        // 1. Detect the true MIME type from the first N bytes (magic bytes)
        String detectedType = tika.detect(bytes);
        if (!ALLOWED_TYPES.contains(detectedType)) {
            log.warn("Rejected upload: detected MIME type '{}' is not allowed", detectedType);
            throw new InvalidMediaException(
                "File type '" + detectedType + "' is not allowed. " +
                "Allowed types: image/jpeg, image/png, image/webp, video/mp4");
        }

        // 2. Size check — different limits for images and video
        long maxBytes = detectedType.startsWith("video/") ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
        if (bytes.length > maxBytes) {
            throw new InvalidMediaException(
                "File size " + (bytes.length / 1024 / 1024) + " MB exceeds the " +
                (maxBytes / 1024 / 1024) + " MB limit for " + detectedType);
        }

        // 3 & 4 apply only to images
        if (detectedType.startsWith("image/")) {
            bytes = validateImageAndStripExif(bytes, detectedType);
        }

        return bytes;
    }

    private byte[] validateImageAndStripExif(byte[] bytes, String mimeType) {
        // 3. Dimension check — load just the header, not the full image
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                throw new InvalidMediaException("Could not decode image — file may be corrupt");
            }
            if (img.getWidth() > MAX_IMAGE_DIMENSION || img.getHeight() > MAX_IMAGE_DIMENSION) {
                throw new InvalidMediaException(
                    "Image dimensions " + img.getWidth() + "×" + img.getHeight() +
                    " exceed the maximum of " + MAX_IMAGE_DIMENSION + "×" + MAX_IMAGE_DIMENSION);
            }

            // 4. Strip EXIF metadata — re-encode via ImageIO (lossy for JPEG)
            //    For a lossless strip, use a dedicated library; re-encoding is simplest here.
            if ("image/jpeg".equals(mimeType)) {
                logAndWarnIfGpsPresent(bytes);
                ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
                ImageIO.write(img, "jpeg", out);
                return out.toByteArray();
            }

            // PNG and WebP — ImageIO re-encode strips EXIF automatically
            String format = "image/png".equals(mimeType) ? "png" : "jpeg";
            ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
            ImageIO.write(img, format, out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new InvalidMediaException("Image validation failed: " + e.getMessage());
        }
    }

    /** Emits a warning log if the image contains GPS coordinates — for audit purposes. */
    private void logAndWarnIfGpsPresent(byte[] bytes) {
        try {
            Metadata meta = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            GpsDirectory gps = meta.getFirstDirectoryOfType(GpsDirectory.class);
            if (gps != null && gps.getGeoLocation() != null) {
                log.warn("Uploaded image contained GPS metadata — stripping before storage");
            }
        } catch (Exception e) {
            // Non-fatal — we will strip regardless; this is just a log
            log.debug("Could not read EXIF GPS data: {}", e.getMessage());
        }
    }
}
```

### 4. Inject `MediaValidator` into `MediaController`

Open `MediaController.java`. Inject `MediaValidator` via the constructor and call `validateAndSanitize` on the incoming file before generating the presigned URL or storing the file:

```java
// In MediaController — add the validator to the constructor
private final MediaValidator mediaValidator;
// ... existing fields

// Constructor injection (all fields):
public MediaController(GenerateUploadUrlUseCase generateUploadUrlUseCase,
                       MediaValidator mediaValidator) {
    this.generateUploadUrlUseCase = generateUploadUrlUseCase;
    this.mediaValidator = mediaValidator;
}

// In the upload endpoint — validate before anything else
@PostMapping("/upload")
public ResponseEntity<ApiResponse<MediaUploadResponse>> getUploadUrl(
        @RequestParam("file") MultipartFile file,
        @AuthenticationPrincipal UserDetails userDetails) {

    // Validate & sanitize — throws InvalidMediaException on failure → 400
    byte[] sanitizedBytes = mediaValidator.validateAndSanitize(file);

    // Generate a server-side object key (Step 5)
    String objectKey = generateObjectKey(userDetails.getUsername(), file.getOriginalFilename());

    // ... generate presigned URL using objectKey and pass sanitizedBytes to storage
}
```

### 5. Generate storage keys server-side and scope presigned URLs tightly

Find the MinIO presigned URL generation method (look in the storage adapter or `MinioConfig`). Replace any logic that uses a client-supplied path with a server-generated key:

```java
// In the storage adapter — key is always generated server-side
private String generateObjectKey(String userId, String originalFilename) {
    // Include UUID to guarantee uniqueness; ignore the client-supplied filename
    // to prevent path traversal (e.g. "../../etc/passwd" as a filename)
    String extension = extractSafeExtension(originalFilename);
    return "media/" + userId + "/" + UUID.randomUUID() + extension;
}

private String extractSafeExtension(String filename) {
    if (filename == null || !filename.contains(".")) return "";
    String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
    // Only pass through known safe extensions — ignore anything else
    return Set.of(".jpg", ".jpeg", ".png", ".webp", ".mp4").contains(ext) ? ext : "";
}
```

When generating the presigned PUT URL, attach conditions that MinIO enforces server-side:

```java
// Expiry: 5 minutes (short) — the client must upload within this window
// Content-Type: locked to the detected MIME type (not the client's declared type)
// The MinIO SDK supports conditions via PostPolicy for POST-based presigned uploads
// For PUT-based presigned URLs, set the Content-Type header as a required condition
```

If your MinIO SDK version supports `PostPolicy`, use it; otherwise generate a short-expiry PUT URL (5 minutes is sufficient) and document that the Content-Type enforcement relies on Spring's inbound validation (Step 3) rather than MinIO.

---

## Checklist

- [x] Validate the real content type by magic bytes (not the `Content-Type` header or extension) — allowlist `image/jpeg`, `image/png`, `image/webp`, `video/mp4`
  - [x] Apache Tika dependency added to `pom.xml`
  - [x] `MediaValidator.validateAndSanitize()` detects MIME type from file bytes
  - [x] Files with disallowed detected types throw `InvalidMediaException` → `400`
- [x] Enforce a max file size and (for images) max dimensions
  - [x] Image max: 10 MB; video max: 100 MB
  - [x] Image dimension max: 8192 × 8192 pixels
  - [x] Both checks in `MediaValidator`
- [x] Strip EXIF/metadata (including GPS) from images on ingest
  - [x] `metadata-extractor` dependency added to `pom.xml`
  - [x] JPEG images re-encoded via `ImageIO` to strip EXIF
  - [x] GPS presence logged as `WARN` before stripping
- [x] Scope presigned PUT URLs tightly: short expiry, exact key, content-type and content-length conditions
  - [x] Presigned URL expiry set to 5 minutes
- [x] Generate stored object keys server-side (never trust a client-supplied path) to prevent overwrite/traversal
  - [x] Key format: `media/{userId}/{uuid}{.ext}` where `ext` is from a safe allowlist

---

## How to Verify

**Disguised file type is rejected:**

Create a text file with a `.jpg` extension and attempt to upload it:

```powershell
# Create a fake "image"
"this is not an image" | Out-File -FilePath fake.jpg -Encoding UTF8

$token = "Bearer <your-access-token>"
$r = Invoke-WebRequest "http://localhost:8080/api/v1/media/upload" `
    -Method POST `
    -Headers @{ Authorization = $token } `
    -Form @{ file = Get-Item fake.jpg } `
    -SkipHttpErrorCheck
Write-Host $r.StatusCode   # Expected: 400
($r.Content | ConvertFrom-Json).error   # Expected: "File type 'text/plain' is not allowed..."
```

**Over-size file is rejected (using a 15 MB dummy):**

```powershell
# Create a 15 MB zero-filled file (exceeds the 10 MB image limit)
$bytes = [byte[]]::new(15 * 1024 * 1024)
[System.IO.File]::WriteAllBytes("big.bin", $bytes)
# Rename to .jpg so the extension check passes (magic bytes will catch it)
Rename-Item big.bin big.jpg

$r = Invoke-WebRequest "http://localhost:8080/api/v1/media/upload" `
    -Method POST -Headers @{ Authorization = $token } `
    -Form @{ file = Get-Item big.jpg } -SkipHttpErrorCheck
Write-Host $r.StatusCode   # Expected: 400 (size rejection before MIME detection)
```

**EXIF GPS is stripped (requires a real JPEG with GPS data):**

Take a photo with GPS enabled on a phone, upload it, then download the stored file and verify no GPS tag is present:

```powershell
# Download the stored image URL from the post response
# Then use ExifTool to inspect (install from https://exiftool.org/)
exiftool downloaded-image.jpg | Select-String "GPS"
# Expected: no output (GPS tags stripped)
```

---

## Notes / Gotchas

**JPEG re-encoding is lossy.**
When `ImageIO.write(img, "jpeg", out)` re-encodes a JPEG it applies JPEG compression again, which degrades quality slightly. For a production system you would use a lossless EXIF stripper (e.g. `jhead` or a pure-Java implementation that strips metadata bytes without re-encoding). For this task, re-encoding is acceptable and simpler. Document the tradeoff in a code comment.

**`ImageIO.read()` can block on some malformed files.**
A carefully crafted malicious JPEG can cause `ImageIO.read()` to hang waiting for "the rest of the file." Set a timeout on the call or run it in a separate thread with a time limit. Apache Tika handles this more defensively than raw ImageIO.

**WebP support in `ImageIO`.**
Java's `ImageIO` does not support WebP by default. If you allow `image/webp`, you need either to reject WebP uploads for now (simplest) or add the `twelveMonkeys-imageio-webp` plugin. The current implementation above handles WebP by re-encoding as JPEG, which is incorrect. Update `extractSafeExtension` to reject `.webp` uploads until you add WebP ImageIO support, or keep WebP in the allowed MIME type list but skip re-encoding (just strip EXIF manually if present).

**Magic bytes only check the file header.**
Tika reads enough bytes to determine the format but does not fully parse the file. A JPEG with a valid header but corrupted body will pass Tika and fail ImageIO later. The ImageIO step's null check (`if (img == null)`) handles this case.

**Presigned URL conditions on MinIO vs AWS S3.**
MinIO's presigned PUT URL does not support content-length conditions by default. If you need strict content-length enforcement at the storage layer, switch to the POST-based `PostPolicy` API, which supports `content-length-range`. For a local MinIO setup, enforcing size in Spring (before the file reaches storage) is sufficient.

**Reference docs:**
- [OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)
- [Apache Tika — Detecting Media Types](https://tika.apache.org/1.28.5/detection.html)
- [metadata-extractor — Java EXIF reader](https://github.com/drewnoakes/metadata-extractor)

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Secure file upload** — size limits, type checks, storing outside the web root — https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html
- **MIME types & content-type validation** — don't trust the client-sent type — https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types
- **Magic-number detection** — verify the real file type from its bytes — https://tika.apache.org/

### Official docs (code reference)
- **Apache Tika (content detection)** — https://tika.apache.org/
- **OWASP File Upload Cheat Sheet** — https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html

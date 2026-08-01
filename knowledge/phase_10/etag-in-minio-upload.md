# ETag in MinIO Upload

## What is it
An ETag (Entity Tag) is an HTTP response header returned by MinIO (and S3-compatible storage in general) that acts as a unique fingerprint/version identifier for an object. For a single-part upload, the ETag is simply the MD5 hash of the object's content (hex-encoded). For a multipart upload (chunked upload), the ETag is instead the MD5 hash of the concatenated MD5 hashes of each part, followed by a `-` and the number of parts (e.g., `"d41d8cd98f00b204e9800998ecf8427e-5"` for a 5-part upload) — so a multipart ETag is not a straightforward MD5 of the whole file content.

## Why use it
- **Integrity verification**: Client can compare the ETag returned after upload against a locally computed hash to confirm the file wasn't corrupted in transit.
- **Change detection / caching**: Since ETag changes whenever content changes, it's used in `If-Match` / `If-None-Match` conditional requests to avoid re-downloading unchanged objects (similar to HTTP caching for web resources).
- **Deduplication checks**: Some systems compare ETags to detect if an identical file already exists before re-uploading.
- **Concurrency control**: ETag can be used as an optimistic-lock token — e.g., "only overwrite this object if its ETag still matches what I last read."
- **Multipart completion validation**: When completing a multipart upload, MinIO/S3 returns a final ETag that confirms all parts were assembled correctly; mismatches signal a corrupted or incomplete upload.

## How can use it
1. **On upload**: MinIO returns the ETag in the response header (`ETag`) of a `PUT` (single-part) or `CompleteMultipartUpload` (multipart) call.
2. **Per-part in chunked upload**: Each `UploadPart` call returns its own ETag for that part; the client must collect all part ETags and pass them (with part numbers) in the final `CompleteMultipartUpload` request — MinIO needs this list to assemble and validate the object.
3. **Verify integrity**:
   - Single-part: compute MD5 of the file locally and compare to the returned ETag directly.
   - Multipart: cannot compare directly to whole-file MD5; instead compute MD5 of each part, then MD5 of the concatenated part-MD5s, matching the `-{partCount}` suffix format.
4. **Java (MinIO SDK) example**: `ObjectWriteResponse response = minioClient.putObject(...)`; `response.etag()` gives the ETag.
5. **Conditional requests**: Use the ETag value in `If-Match` headers on subsequent requests to implement optimistic concurrency (not all MinIO deployments enforce this the same as AWS S3 — check version support).

## When use it in real life
- Confirming a chunked/resumable large-file upload (like the 2GB upload flow) completed without corruption before marking it "done" in your app's database.
- Implementing client-side caching for media/CDN-served files backed by MinIO (skip re-fetch if ETag unchanged).
- Deduplicating uploads (e.g., user re-uploads the same profile picture — check ETag/hash before storing again).
- Auditing/verifying data integrity in backup or archival systems using MinIO as object storage.

# Chunked / Resumable Large-File Upload (up to 2 GB)

## What is it
A file-upload strategy where a large file is split into smaller chunks (e.g., 5–10 MB pieces) client-side, each chunk uploaded independently with an identifier tracking its position, and the server reassembles them once all chunks arrive. "Resumable" means if the upload is interrupted (network drop, browser close, app crash), the client can query which chunks the server already has and resume from there instead of restarting from byte 0.

## Why use it
- **Reliability over unstable networks**: A single 2GB request failing at 99% means resending everything; chunking means only the failed chunk is resent.
- **Avoids server/proxy limits**: Many servers, load balancers, and CDNs cap request body size (e.g., default limits around 1–100MB); chunking keeps each request small.
- **Memory efficiency**: Server never has to buffer the entire 2GB file in memory at once — it can stream/write each chunk directly to disk/object storage.
- **Progress tracking & UX**: Enables accurate progress bars, pause/resume, and parallel chunk uploads for speed.
- **Timeout avoidance**: Long-running single requests risk hitting HTTP timeout limits; chunks complete quickly.

## How can use it
1. **Client side**: Split file using `Blob.slice()` (browser) into fixed-size chunks; assign each chunk an index and a shared `uploadId`.
2. **Init**: Client calls an endpoint like `POST /uploads/init` to get an `uploadId` (server creates a temp record/dir).
3. **Upload chunks**: Client sends `PUT /uploads/{uploadId}/chunks/{index}` for each chunk, ideally with retry logic per chunk.
4. **Resume check**: On reconnect, client calls `GET /uploads/{uploadId}/status` to see which chunk indices are already received, and only sends missing ones.
5. **Complete**: Client calls `POST /uploads/{uploadId}/complete`; server verifies all chunks present (checksum per chunk optional), concatenates them in order, and finalizes the file (e.g., moves to permanent storage / triggers processing).
6. **Implementation options**:
   - Roll your own with Spring Boot (store chunks on disk keyed by uploadId+index, use `RandomAccessFile` or `FileChannel` to reassemble).
   - Use existing protocols/libraries: **tus.io** (open resumable upload protocol, has Java server implementations), or cloud-native multipart upload (AWS S3 Multipart Upload, which natively supports chunks up to 5GB per part and resumability).
   - Frontend libraries: `tus-js-client`, `resumable.js`, `uppy`.

## When use it in real life
- Video/media upload platforms (Instagram-style apps uploading Reels/long videos, YouTube).
- Cloud storage/backup apps (Google Drive, Dropbox) uploading large files over flaky mobile connections.
- Enterprise file-sharing systems where users upload large datasets, backups, or archives.
- Mobile apps where connectivity drops are common and users shouldn't have to restart a 30-minute upload from zero.

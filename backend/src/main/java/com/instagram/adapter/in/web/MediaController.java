package com.instagram.adapter.in.web;

import org.springframework.web.bind.annotation.RestController;

import com.instagram.adapter.in.web.dto.request.CompleteUploadRequest;
import com.instagram.adapter.in.web.dto.request.InitiateUploadRequest;
import com.instagram.adapter.in.web.dto.request.ProcessMediaRequest;
import com.instagram.adapter.in.web.dto.request.UploadUrlRequest;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.InitiateUploadResponse;
import com.instagram.adapter.in.web.dto.response.UploadPartsResponse;
import com.instagram.adapter.in.web.dto.response.UploadUrlResponse;
import com.instagram.adapter.out.persistence.entity.UploadSessionJpaEntity;
import com.instagram.adapter.out.persistence.repository.UploadSessionJpaRepository;
import com.instagram.application.service.PostService;
import com.instagram.domain.port.in.GenerateUploadUrlUseCase;
import com.instagram.domain.port.out.MediaStoragePort;
import com.instagram.infrastructure.storage.MediaValidator;

import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Media upload and management endpoints.
 *
 * <h2>Upload architecture — why MediaValidator is not called here</h2>
 *
 * All upload flows in this controller are <em>presigned-URL based</em>: the
 * server
 * generates a time-limited, signed URL and returns it to the client; the client
 * then
 * uploads the file bytes <em>directly to MinIO</em>, never sending the binary
 * payload
 * through this server.
 *
 * <ul>
 * <li>{@code POST /upload-url} — issues a presigned PUT URL; body contains only
 * {@code filename} + {@code contentType} (no file bytes).</li>
 * <li>{@code POST /uploads} — initiates a multipart session; body contains
 * session
 * metadata (part count, size, content-type) but no file bytes.</li>
 * <li>{@code POST /uploads/{id}/complete} — assembles already-uploaded parts;
 * no
 * file bytes.</li>
 * </ul>
 *
 * Because no endpoint in this controller receives a {@code MultipartFile},
 * {@link MediaValidator#validateAndSanitize} cannot be invoked at the HTTP
 * layer.
 * The validator is wired in and ready for a future <em>direct-upload</em>
 * endpoint
 * ({@code POST /upload} accepting {@code multipart/form-data}), which would
 * send
 * file bytes to the server, validate them, and then call
 * {@link MediaStoragePort#uploadFile} — bypassing the presigned-URL step
 * entirely.
 *
 * <h2>What IS enforced in the current presigned-URL flow</h2>
 * <ul>
 * <li>Server-side object key generation (UUID + safe extension allowlist)
 * prevents
 * path traversal and key overwrite attacks —
 * {@link PostService#generateUploadUrl}.</li>
 * <li>Short presigned-URL expiry (5 minutes) limits the replay window.</li>
 * <li>MinIO bucket policy restricts writes to authenticated paths only.</li>
 * </ul>
 *
 * <h2>Remaining gap</h2>
 * Magic-byte MIME validation, file-size enforcement, dimension checks, and EXIF
 * stripping are <em>not</em> applied to files uploaded via presigned URLs —
 * MinIO
 * stores whatever the client PUTs. Closing this gap requires either adding a
 * direct-upload endpoint or a post-upload scan job that downloads, validates,
 * and
 * quarantines non-conforming objects.
 */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Slf4j
public class MediaController {
        private final GenerateUploadUrlUseCase generateUploadUrlUseCase;
        private final PostService postService;
        private final MediaStoragePort mediaStoragePort;
        private final UploadSessionJpaRepository uploadSessionRepository;
        /**
         * Reserved for a future direct-upload endpoint. Not called by any current
         * endpoint because no endpoint in this controller receives file bytes.
         * See class-level Javadoc for details.
         */
        private final MediaValidator mediaValidator;

        @Nullable
        private UUID currentUserIdOrNull() {
                org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext()
                                .getAuthentication();
                if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                        return null;
                }
                if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
                        return UUID.fromString(userDetails.getUsername());
                }
                return UUID.fromString(auth.getPrincipal().toString());
        }

        private UUID currentUserId() {
                UUID userId = currentUserIdOrNull();
                if (userId == null) {
                        throw new IllegalStateException("User is not authenticated");
                }
                return userId;
        }

        @PostMapping("/upload-url")
        @PreAuthorize("isAuthenticated()")
        @Operation(summary = "Generate a pre-signed PUT URL to upload a file directly to MinIO")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pre-signed URL returned"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid content type"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
        })
        public ResponseEntity<ApiResponse<UploadUrlResponse>> getUploadUrl(
                        @RequestBody @Valid UploadUrlRequest req,
                        @AuthenticationPrincipal UserDetails userDetails) {

                UUID userId = UUID.fromString(userDetails.getUsername());
                GenerateUploadUrlUseCase.UploadUrl result = generateUploadUrlUseCase.generateUploadUrl(
                                new GenerateUploadUrlUseCase.Command(userId, req.filename(), req.contentType()));
                log.info("Upload URL generated userId={} filename={}", userId, req.filename());
                return ResponseEntity.ok(ApiResponse.ok(UploadUrlResponse.from(result)));
        }

        /**
         * Stub: detects the best image format the client supports via the Accept header
         * (AVIF > WebP > JPEG fallback) and logs it. A real implementation would
         * redirect
         * to a CDN URL with a format query parameter once an image transcoder is
         * configured
         * (e.g. Imgix, Cloudinary, or AWS Lambda@Edge with sharp).
         */
        @GetMapping("/images/{key}")
        @Operation(summary = "Serve image with best format for client (Accept-header hint stub)")
        public ResponseEntity<Void> serveImage(
                        @PathVariable String key,
                        @RequestHeader(value = "Accept", defaultValue = "*/*") String accept) {

                String format = "jpeg";
                if (accept.contains("image/avif")) {
                        format = "avif";
                } else if (accept.contains("image/webp")) {
                        format = "webp";
                }

                log.debug("Client supports format={} for key={}", format, key);

                // When a CDN transcoder is available, replace with:
                // return ResponseEntity.status(HttpStatus.FOUND)
                // .header("Location", cdnBaseUrl + "/" + key + "?fm=" + format)
                // .build();
                return ResponseEntity.noContent().build();
        }

        @PostMapping("/process")
        @PreAuthorize("isAuthenticated()")
        @Operation(summary = "Trigger async media processing for an uploaded object")
        public ResponseEntity<ApiResponse<Map<String, String>>> processMedia(
                        @RequestBody @Valid ProcessMediaRequest request,
                        @AuthenticationPrincipal UserDetails userDetails) {

                UUID userId = UUID.fromString(userDetails.getUsername());
                String jobId = UUID.randomUUID().toString();

                postService.generateThumbnailAsync(request.mediaUrl(), UUID.fromString(request.postId()));

                log.info("Media processing job accepted: jobId={} userId={}", jobId, userId);

                return ResponseEntity
                                .status(HttpStatus.ACCEPTED)
                                .body(ApiResponse.ok(Map.of(
                                                "jobId", jobId,
                                                "status", "accepted",
                                                "statusUrl", "/api/v1/media/jobs/" + jobId)));
        }

        @PostMapping("/uploads")
        @PreAuthorize("isAuthenticated()")
        @Operation(summary = "Initiate a multipart upload session")
        public ResponseEntity<ApiResponse<Object>> initiateUpload(
                        @RequestBody @Valid InitiateUploadRequest request) {

                UUID userId = currentUserId();

                // Validate file size cap: 2 GB
                if (request.totalSizeBytes() > 2L * 1024 * 1024 * 1024) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(ApiResponse.ok("file exceed 2 gb limit"));
                }
                // Validate part size: 5–10 MB per part
                if (request.partSizeBytes() < 5 * 1024 * 1024 || request.partSizeBytes() > 10 * 1024 * 1024) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(ApiResponse.ok("Part size must be between 5 MB and 10 MB"));
                }

                String key = "uploads/" + userId + "/" + UUID.randomUUID() + extractSafeExtension(request.filename());
                String uploadId = mediaStoragePort.initiateMultipartUpload(key, request.contentType());
                log.info("Multipart upload initiated userId={} key={}", userId, key);

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

        @GetMapping("/uploads/{uploadId}/parts")
        @PreAuthorize("isAuthenticated()")
        @Operation(summary = "List uploaded parts for a multipart upload session (used to resume)")
        public ResponseEntity<ApiResponse<UploadPartsResponse>> listUploadedParts(
                        @PathVariable String uploadId) {

                UUID userId = currentUserId();
                log.debug("listUploadedParts uploadId={}", uploadId);

                UploadSessionJpaEntity session = uploadSessionRepository.findByUploadId(uploadId)
                                .filter(s -> s.getUserId().equals(userId))
                                .orElseThrow(() -> new IllegalArgumentException("Upload session not found"));

                List<UploadPartsResponse.UploadedPart> parts = mediaStoragePort
                                .listUploadedParts(session.getObjectKey(), uploadId)
                                .stream()
                                .map(p -> new UploadPartsResponse.UploadedPart(p.partNumber(), p.etag(), p.sizeBytes()))
                                .toList();

                return ResponseEntity.ok(ApiResponse.ok(new UploadPartsResponse(uploadId, parts)));
        }

        @PostMapping("/uploads/{uploadId}/complete")
        @PreAuthorize("isAuthenticated()")
        @Operation(summary = "Complete a multipart upload by providing all part ETags")
        public ResponseEntity<ApiResponse<Map<String, String>>> completeUpload(
                        @PathVariable String uploadId,
                        @RequestBody @Valid CompleteUploadRequest request) {

                UUID userId = currentUserId();

                UploadSessionJpaEntity session = uploadSessionRepository.findByUploadId(uploadId)
                                .filter(s -> s.getUserId().equals(userId))
                                .orElseThrow(() -> new IllegalArgumentException("Upload session not found"));

                List<MediaStoragePort.PartETag> partETags = request.parts().stream()
                                .map(p -> new MediaStoragePort.PartETag(p.partNumber(), p.etag()))
                                .toList();

                String publicUrl = mediaStoragePort.completeMultipartUpload(
                                session.getObjectKey(), uploadId, partETags);
                log.info("Multipart upload completed uploadId={}", uploadId);

                session.setStatus("COMPLETED");
                uploadSessionRepository.save(session);

                return ResponseEntity.ok(ApiResponse.ok(Map.of("url", publicUrl)));
        }

        private String extractSafeExtension(String filename) {
                if (filename == null || !filename.contains("."))
                        return "";
                String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
                return java.util.Set.of(".jpg", ".jpeg", ".png", ".webp", ".mp4").contains(ext) ? ext : "";
        }
}

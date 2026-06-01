package com.instagram.adapter.in.web;

import org.springframework.web.bind.annotation.RestController;

import com.instagram.adapter.in.web.dto.request.UploadUrlRequest;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.UploadUrlResponse;
import com.instagram.domain.port.in.GenerateUploadUrlUseCase;

import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Slf4j
public class MediaController {
    private final GenerateUploadUrlUseCase generateUploadUrlUseCase;

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
        return ResponseEntity.ok(ApiResponse.ok(UploadUrlResponse.from(result)));
    }

    /**
     * Stub: detects the best image format the client supports via the Accept header
     * (AVIF > WebP > JPEG fallback) and logs it. A real implementation would redirect
     * to a CDN URL with a format query parameter once an image transcoder is configured
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
        //     .header("Location", cdnBaseUrl + "/" + key + "?fm=" + format)
        //     .build();
        return ResponseEntity.noContent().build();
    }
}

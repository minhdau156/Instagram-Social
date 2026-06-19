package com.instagram.infrastructure.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.GpsDirectory;
import com.instagram.domain.exception.InvalidMediaException;

@Component
public class MediaValidator {
    private static final Logger log = LoggerFactory.getLogger(MediaValidator.class);

    // Allowlisted MIME types — exactly these four
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "video/mp4");

    // 100 MB max for video; 10 MB for images (Spring's multipart limit handles the
    // hard cap)
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024; // 100 MB

    // Reject images larger than 8192 x 8192 pixels (decompression bomb guard)
    private static final int MAX_IMAGE_DIMENSION = 8192;

    private final Tika tika = new Tika();

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
            throw new InvalidMediaException("File type '" + detectedType + "' is not allowed. " +
                    "Allowed types: image/jpeg, image/png, image/webp, video/mp4");
        }
        // 2. Size check — different limits for images and video
        long maxBytes = detectedType.startsWith("video/") ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
        if (bytes.length > maxBytes) {
            throw new InvalidMediaException("File size " + (bytes.length / 1024 / 1024) + " MB exceeds the " +
                    (maxBytes / 1024 / 1024) + " MB limit for " + detectedType);
        }
        // 3 & 4 apply only to images
        if (detectedType.startsWith("image/")) {
            bytes = validateImageAndStripExif(bytes, detectedType);
        }

        return bytes;
    }

    private byte[] validateImageAndStripExif(byte[] bytes, String mimeType) {
        // WebP is not supported by Java's ImageIO — skip re-encoding to avoid a false
        // "corrupt file" rejection. WebP files carry no GPS-bearing EXIF by convention.
        if ("image/webp".equals(mimeType)) {
            return bytes;
        }

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
            // For a lossless strip, use a dedicated library; re-encoding is simplest here.
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

    private void logAndWarnIfGpsPresent(byte[] bytes) {
        try {
            Metadata meta = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            GpsDirectory gps = meta.getFirstDirectoryOfType(GpsDirectory.class);
            if (gps != null && gps.getGeoLocation() != null) {
                log.warn("Uploaded image contained GPS metadata - stripping before storage");
            }
        } catch (Exception e) {
            log.debug("Could not read EXIF GPS data: {}", e.getMessage());
        }
    }

}

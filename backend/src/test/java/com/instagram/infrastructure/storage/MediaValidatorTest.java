package com.instagram.infrastructure.storage;

import com.instagram.domain.exception.InvalidMediaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaValidatorTest {

    private MediaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MediaValidator();
    }

    private byte[] smallJpeg() throws Exception {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 100, 100);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", out);
        return out.toByteArray();
    }

    private byte[] smallPng() throws Exception {
        BufferedImage img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    // ── null / empty ──────────────────────────────────────────────────────────

    @Test
    void validateAndSanitize_throwsWhenFileIsNull() {
        assertThatThrownBy(() -> validator.validateAndSanitize(null))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void validateAndSanitize_throwsWhenFileIsEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> validator.validateAndSanitize(file))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("empty");
    }

    // ── MIME type ─────────────────────────────────────────────────────────────

    @Test
    void validateAndSanitize_throwsForDisallowedMimeType() {
        byte[] pdfBytes = "%PDF-1.4 fake content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdfBytes);

        assertThatThrownBy(() -> validator.validateAndSanitize(file))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void validateAndSanitize_throwsForTextContent() {
        byte[] textBytes = "Hello, world!".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "text.txt", "text/plain", textBytes);

        assertThatThrownBy(() -> validator.validateAndSanitize(file))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("not allowed");
    }

    // ── size limits ───────────────────────────────────────────────────────────

    @Test
    void validateAndSanitize_throwsWhenImageExceedsMaxSize() throws Exception {
        byte[] jpegHeader = smallJpeg();
        // Embed the real JPEG header but append enough bytes to exceed 10 MB
        // We'll use a mock that returns oversized bytes; Tika detects from magic bytes
        byte[] oversized = new byte[11 * 1024 * 1024];
        // Copy JPEG magic bytes at the start so Tika detects it as image/jpeg
        System.arraycopy(jpegHeader, 0, oversized, 0, Math.min(jpegHeader.length, oversized.length));

        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", oversized);

        assertThatThrownBy(() -> validator.validateAndSanitize(file))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("exceeds");
    }

    // ── valid JPEG ────────────────────────────────────────────────────────────

    @Test
    void validateAndSanitize_returnsNonEmptyBytesForValidJpeg() throws Exception {
        byte[] jpegBytes = smallJpeg();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes);

        byte[] result = validator.validateAndSanitize(file);

        assertThat(result).isNotEmpty();
    }

    @Test
    void validateAndSanitize_returnsJpegMagicBytesAfterSanitization() throws Exception {
        byte[] jpegBytes = smallJpeg();
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes);

        byte[] result = validator.validateAndSanitize(file);

        // JPEG magic bytes: FF D8
        assertThat(result[0] & 0xFF).isEqualTo(0xFF);
        assertThat(result[1] & 0xFF).isEqualTo(0xD8);
    }

    // ── valid PNG ─────────────────────────────────────────────────────────────

    @Test
    void validateAndSanitize_returnsNonEmptyBytesForValidPng() throws Exception {
        byte[] pngBytes = smallPng();
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", pngBytes);

        byte[] result = validator.validateAndSanitize(file);

        assertThat(result).isNotEmpty();
    }

    @Test
    void validateAndSanitize_returnsPngMagicBytesAfterSanitization() throws Exception {
        byte[] pngBytes = smallPng();
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", pngBytes);

        byte[] result = validator.validateAndSanitize(file);

        // PNG magic: 89 50 4E 47
        assertThat(result[0] & 0xFF).isEqualTo(0x89);
        assertThat(result[1] & 0xFF).isEqualTo(0x50);
    }

    // ── dimension check ───────────────────────────────────────────────────────

    @Test
    void validateAndSanitize_throwsWhenImageTooLarge() throws Exception {
        // Create a 9000x9000 image (exceeds 8192 limit)
        BufferedImage huge = new BufferedImage(9000, 9000, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(huge, "jpeg", out);
        byte[] bytes = out.toByteArray();

        MockMultipartFile file = new MockMultipartFile("file", "huge.jpg", "image/jpeg", bytes);

        assertThatThrownBy(() -> validator.validateAndSanitize(file))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("exceed");
    }
}

package com.instagram.infrastructure.util;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

public final class CursorEncoder {

    private CursorEncoder() {
    }

    /** Encodes a (createdAt, id) pair into an opaque cursor string. */
    public static String encode(Instant createdAt, UUID id) {
        String raw = createdAt.toString() + "|" + id.toString();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes an opaque cursor into its component parts. */
    public static DecodedCursor decode(String cursor) {
        try {
            String raw = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new DecodedCursor(
                    OffsetDateTime.parse(parts[0]),
                    UUID.fromString(parts[1]));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor, e);
        }
    }

    public record DecodedCursor(OffsetDateTime createdAt, UUID id) {
    }
}

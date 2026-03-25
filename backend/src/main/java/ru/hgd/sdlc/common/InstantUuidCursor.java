package ru.hgd.sdlc.common;

import java.time.Instant;
import java.util.UUID;

public final class InstantUuidCursor {
    private InstantUuidCursor() {
    }

    public static String encode(Instant savedAt, UUID id) {
        if (savedAt == null || id == null) {
            return null;
        }
        return savedAt.toString() + "|" + id;
    }

    public static Parsed decode(String cursor, String fieldName) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String[] parts = cursor.split("\\|", 2);
        if (parts.length != 2) {
            throw new ValidationException(fieldName + " has invalid format");
        }
        try {
            return new Parsed(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(fieldName + " has invalid format");
        }
    }

    public record Parsed(
            Instant savedAt,
            UUID id
    ) {
    }
}

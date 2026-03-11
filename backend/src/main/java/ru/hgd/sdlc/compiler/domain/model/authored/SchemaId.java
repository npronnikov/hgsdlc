package ru.hgd.sdlc.compiler.domain.model.authored;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value type representing a Schema identifier.
 * Can be a URL, URN, or simple identifier.
 */
public final class SchemaId {

    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_./:-]+$");

    private final String value;

    private SchemaId(String value) {
        this.value = value;
    }

    public static SchemaId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SchemaId cannot be null or blank");
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "SchemaId contains invalid characters: " + value);
        }
        return new SchemaId(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SchemaId schemaId = (SchemaId) o;
        return value.equals(schemaId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "SchemaId{" + value + "}";
    }
}

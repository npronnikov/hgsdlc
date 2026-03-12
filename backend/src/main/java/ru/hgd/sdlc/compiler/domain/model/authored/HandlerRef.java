package ru.hgd.sdlc.compiler.domain.model.authored;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reference to a handler (skill, builtin, or script).
 * Format: "skill://skill-id", "builtin://capability", or "script://path".
 */
public final class HandlerRef {

    private static final Pattern VALID_PATTERN = Pattern.compile("^(skill|builtin|script)://(.+)$");

    private final HandlerKind kind;
    private final String reference;
    private final String formatted;

    private HandlerRef(HandlerKind kind, String reference, String formatted) {
        this.kind = kind;
        this.reference = reference;
        this.formatted = formatted;
    }

    /**
     * Parses a handler reference string.
     *
     * @param value the handler reference (e.g., "skill://my-skill", "builtin://validate")
     * @return a new HandlerRef instance
     */
    @JsonCreator
    public static HandlerRef of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("HandlerRef cannot be null or blank");
        }

        String trimmed = value.trim();
        var matcher = VALID_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "HandlerRef must be in format 'skill://id', 'builtin://name', or 'script://path': " + value);
        }

        HandlerKind kind = HandlerKind.valueOf(matcher.group(1).toUpperCase());
        String ref = matcher.group(2);

        return new HandlerRef(kind, ref, trimmed);
    }

    /**
     * Creates a skill handler reference.
     *
     * @param skillId the skill ID
     * @return a new HandlerRef instance
     */
    public static HandlerRef skill(SkillId skillId) {
        return new HandlerRef(HandlerKind.SKILL, skillId.value(), "skill://" + skillId.value());
    }

    /**
     * Creates a builtin handler reference.
     *
     * @param capability the builtin capability name
     * @return a new HandlerRef instance
     */
    public static HandlerRef builtin(String capability) {
        if (capability == null || capability.isBlank()) {
            throw new IllegalArgumentException("Capability cannot be null or blank");
        }
        return new HandlerRef(HandlerKind.BUILTIN, capability, "builtin://" + capability);
    }

    /**
     * Creates a script handler reference.
     *
     * @param path the script path
     * @return a new HandlerRef instance
     */
    public static HandlerRef script(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path cannot be null or blank");
        }
        return new HandlerRef(HandlerKind.SCRIPT, path, "script://" + path);
    }

    public HandlerKind kind() {
        return kind;
    }

    public String reference() {
        return reference;
    }

    /**
     * Returns the skill ID if this is a skill reference.
     *
     * @return the skill ID, or empty if not a skill reference
     */
    public SkillId asSkillId() {
        if (kind == HandlerKind.SKILL) {
            return SkillId.of(reference);
        }
        return null;
    }

    @JsonValue
    @Override
    public String toString() {
        return formatted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HandlerRef that = (HandlerRef) o;
        return kind == that.kind && reference.equals(that.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, reference);
    }
}

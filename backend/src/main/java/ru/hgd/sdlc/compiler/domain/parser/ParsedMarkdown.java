package ru.hgd.sdlc.compiler.domain.parser;

import ru.hgd.sdlc.compiler.domain.model.authored.MarkdownBody;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a parsed Markdown file with extracted frontmatter and body.
 * The body is preserved as raw bytes for byte-identical round-trip serialization.
 */
public final class ParsedMarkdown {

    private final Map<String, Object> frontmatter;
    private final MarkdownBody body;
    private final int frontmatterLineCount;

    private ParsedMarkdown(Map<String, Object> frontmatter, MarkdownBody body, int frontmatterLineCount) {
        this.frontmatter = Collections.unmodifiableMap(frontmatter);
        this.body = body;
        this.frontmatterLineCount = frontmatterLineCount;
    }

    /**
     * Creates a ParsedMarkdown instance.
     *
     * @param frontmatter the extracted YAML frontmatter as a map
     * @param body the markdown body (after frontmatter)
     * @param frontmatterLineCount the number of lines consumed by frontmatter (including delimiters)
     * @return a new ParsedMarkdown instance
     */
    public static ParsedMarkdown of(Map<String, Object> frontmatter, MarkdownBody body, int frontmatterLineCount) {
        Objects.requireNonNull(frontmatter, "frontmatter cannot be null");
        Objects.requireNonNull(body, "body cannot be null");
        return new ParsedMarkdown(frontmatter, body, frontmatterLineCount);
    }

    /**
     * Creates a ParsedMarkdown with empty frontmatter.
     */
    public static ParsedMarkdown bodyOnly(MarkdownBody body) {
        return new ParsedMarkdown(Map.of(), body, 0);
    }

    /**
     * Returns the frontmatter map.
     * The map is immutable.
     */
    public Map<String, Object> frontmatter() {
        return frontmatter;
    }

    /**
     * Returns the markdown body.
     */
    public MarkdownBody body() {
        return body;
    }

    /**
     * Returns the number of lines consumed by frontmatter (including --- delimiters).
     * Useful for error reporting to map back to source line numbers.
     */
    public int frontmatterLineCount() {
        return frontmatterLineCount;
    }

    /**
     * Checks if frontmatter is present.
     */
    public boolean hasFrontmatter() {
        return !frontmatter.isEmpty();
    }

    /**
     * Gets a field from frontmatter.
     *
     * @param key the field key
     * @return the field value, or null if not present
     */
    public Object get(String key) {
        return frontmatter.get(key);
    }

    /**
     * Gets a field from frontmatter with a default value.
     *
     * @param key the field key
     * @param defaultValue the default value if not present
     * @return the field value, or defaultValue if not present
     */
    public Object get(String key, Object defaultValue) {
        return frontmatter.getOrDefault(key, defaultValue);
    }

    /**
     * Gets a string field from frontmatter.
     *
     * @param key the field key
     * @return the string value, or null if not present or not a string
     */
    public String getString(String key) {
        Object value = frontmatter.get(key);
        return value instanceof String ? (String) value : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParsedMarkdown that = (ParsedMarkdown) o;
        return frontmatterLineCount == that.frontmatterLineCount
            && frontmatter.equals(that.frontmatter)
            && Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(frontmatter, body, frontmatterLineCount);
    }

    @Override
    public String toString() {
        return "ParsedMarkdown{frontmatter=" + frontmatter.size() + " keys, body=" + body.length() + " bytes}";
    }
}

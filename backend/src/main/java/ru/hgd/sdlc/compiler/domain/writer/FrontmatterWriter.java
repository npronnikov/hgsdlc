package ru.hgd.sdlc.compiler.domain.writer;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes YAML frontmatter from a map of values.
 *
 * <p>This class produces deterministic YAML output suitable for
 * canonical Markdown serialization. The output format is designed
 * to be both human-readable and machine-parseable.
 *
 * <p>Key features:
 * <ul>
 *   <li>Deterministic field ordering (alphabetical)</li>
 *   <li>Proper string escaping</li>
 *   <li>Support for nested objects and lists</li>
 *   <li>Compact list notation for simple lists</li>
 * </ul>
 */
public final class FrontmatterWriter {

    private static final String INDENT = "  ";
    private static final String LIST_PREFIX = "- ";

    /**
     * Writes a map as YAML frontmatter.
     * Fields are sorted alphabetically for deterministic output.
     *
     * @param frontmatter the map to write
     * @return the YAML representation
     */
    public String write(Map<String, Object> frontmatter) {
        if (frontmatter == null || frontmatter.isEmpty()) {
            return "";
        }

        // Sort fields alphabetically for deterministic output
        Map<String, Object> sorted = new LinkedHashMap<>();
        frontmatter.keySet().stream()
            .sorted()
            .forEach(k -> sorted.put(k, frontmatter.get(k)));

        StringBuilder sb = new StringBuilder();
        writeMap(sb, sorted, 0);
        return sb.toString();
    }

    /**
     * Writes a map as YAML frontmatter with custom field ordering.
     *
     * @param frontmatter the map to write
     * @param fieldOrder the preferred order of fields (fields not in this list go last, alphabetically)
     * @return the YAML representation
     */
    public String write(Map<String, Object> frontmatter, List<String> fieldOrder) {
        if (frontmatter == null || frontmatter.isEmpty()) {
            return "";
        }

        // Create a sorted map with custom ordering
        Map<String, Object> ordered = new LinkedHashMap<>();

        // First add fields in the specified order
        if (fieldOrder != null) {
            for (String field : fieldOrder) {
                if (frontmatter.containsKey(field)) {
                    ordered.put(field, frontmatter.get(field));
                }
            }
        }

        // Then add remaining fields in alphabetical order
        frontmatter.keySet().stream()
            .sorted()
            .filter(k -> !ordered.containsKey(k))
            .forEach(k -> ordered.put(k, frontmatter.get(k)));

        StringBuilder sb = new StringBuilder();
        writeMap(sb, ordered, 0);
        return sb.toString();
    }

    private void writeMap(StringBuilder sb, Map<String, Object> map, int indentLevel) {
        String indent = INDENT.repeat(indentLevel);

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            sb.append(indent).append(key).append(":");
            writeValue(sb, value, indentLevel);
        }
    }

    private void writeValue(StringBuilder sb, Object value, int indentLevel) {
        if (value == null) {
            sb.append(" null\n");
        } else if (value instanceof String s) {
            writeStringValue(sb, s, indentLevel);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(" ").append(value).append("\n");
        } else if (value instanceof Instant instant) {
            sb.append(" ").append(DateTimeFormatter.ISO_INSTANT.format(instant)).append("\n");
        } else if (value instanceof Collection<?> collection) {
            writeCollection(sb, collection, indentLevel);
        } else if (value instanceof Map<?, ?> map) {
            writeNestedMap(sb, map, indentLevel);
        } else {
            // Fallback: toString with string escaping
            writeStringValue(sb, value.toString(), indentLevel);
        }
    }

    private void writeStringValue(StringBuilder sb, String value, int indentLevel) {
        if (value.isEmpty()) {
            sb.append(" \"\"\n");
            return;
        }

        // Check if we need quoting
        boolean needsQuoting = needsQuoting(value);

        if (needsQuoting) {
            sb.append(" \"");
            sb.append(escapeString(value));
            sb.append("\"\n");
        } else if (value.contains("\n")) {
            // Multi-line string - use block scalar
            sb.append(" |\n");
            String[] lines = value.split("\n", -1);
            String blockIndent = INDENT.repeat(indentLevel + 1);
            for (String line : lines) {
                sb.append(blockIndent).append(line).append("\n");
            }
        } else {
            sb.append(" ").append(value).append("\n");
        }
    }

    private boolean needsQuoting(String value) {
        // Empty strings need quoting
        if (value.isEmpty()) {
            return true;
        }

        // Check for special characters that require quoting
        char first = value.charAt(0);
        if (first == ':' || first == '{' || first == '}' || first == '[' || first == ']' ||
            first == ',' || first == '&' || first == '*' || first == '#' || first == '?' ||
            first == '|' || first == '-' || first == '<' || first == '>' ||
            first == '=' || first == '!' || first == '%' || first == '@' || first == '`') {
            return true;
        }

        // Check for characters anywhere in the string that require quoting
        if (value.contains("\"") || value.contains("\n") || value.contains("\r") || value.contains("\t")) {
            return true;
        }

        // Check for special YAML values
        String lower = value.toLowerCase();
        if (lower.equals("true") || lower.equals("false") || lower.equals("null") || lower.equals("~")) {
            return true;
        }

        // Check for numbers
        if (value.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?")) {
            return true;
        }

        // Check for colons followed by space or end of string (YAML key-value indicator)
        if (value.contains(": ") || value.endsWith(":")) {
            return true;
        }

        return false;
    }

    private String escapeString(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private void writeCollection(StringBuilder sb, Collection<?> collection, int indentLevel) {
        if (collection.isEmpty()) {
            sb.append(" []\n");
            return;
        }

        // Check if we can use compact flow notation for simple scalar lists
        if (canUseCompactNotation(collection)) {
            sb.append(" [");
            String elementIndent = INDENT.repeat(indentLevel + 1);
            List<String> elements = new ArrayList<>();
            for (Object item : collection) {
                elements.add(formatScalar(item));
            }
            sb.append(String.join(", ", elements));
            sb.append("]\n");
        } else {
            // Use block notation
            sb.append("\n");
            String itemIndent = INDENT.repeat(indentLevel + 1);
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    sb.append(itemIndent).append("- ");
                    // First entry on same line as dash
                    boolean first = true;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (first) {
                            sb.append(entry.getKey()).append(":");
                            writeValue(sb, entry.getValue(), indentLevel + 1);
                            first = false;
                        } else {
                            sb.append(itemIndent).append(INDENT).append(entry.getKey()).append(":");
                            writeValue(sb, entry.getValue(), indentLevel + 2);
                        }
                    }
                } else {
                    sb.append(itemIndent).append("- ");
                    writeValue(sb, item, indentLevel + 1);
                }
            }
        }
    }

    private boolean canUseCompactNotation(Collection<?> collection) {
        // Use compact notation for small collections of simple scalars
        if (collection.size() > 5) {
            return false;
        }
        for (Object item : collection) {
            if (!(item instanceof String) && !(item instanceof Number) && !(item instanceof Boolean)) {
                return false;
            }
            if (item instanceof String s && (s.contains(",") || s.contains("\n") || needsQuoting(s))) {
                return false;
            }
        }
        return true;
    }

    private String formatScalar(Object value) {
        if (value instanceof String s) {
            return s;
        }
        return String.valueOf(value);
    }

    private void writeNestedMap(StringBuilder sb, Map<?, ?> map, int indentLevel) {
        if (map.isEmpty()) {
            sb.append(" {}\n");
            return;
        }

        sb.append("\n");
        @SuppressWarnings("unchecked")
        Map<String, Object> typedMap = (Map<String, Object>) map;
        writeMap(sb, typedMap, indentLevel + 1);
    }
}

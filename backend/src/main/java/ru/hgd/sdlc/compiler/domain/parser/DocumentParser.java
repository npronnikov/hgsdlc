package ru.hgd.sdlc.compiler.domain.parser;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Base class for document parsers.
 * Provides common parsing utilities and error collection.
 */
public abstract class DocumentParser {

    /**
     * Error collector for parsing operations.
     */
    protected static final class ErrorCollector {
        private final List<ParseError> errors = new ArrayList<>();

        /**
         * Adds a missing field error.
         */
        public void missingField(String fieldName) {
            errors.add(ParseError.missingField(fieldName, null));
        }

        /**
         * Adds an invalid field error.
         */
        public void invalidField(String fieldName, String reason) {
            errors.add(ParseError.invalidField(fieldName, reason, null));
        }

        /**
         * Adds a custom error.
         */
        public void add(ParseError error) {
            errors.add(error);
        }

        /**
         * Returns true if there are any errors.
         */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        /**
         * Returns all collected errors.
         */
        public List<ParseError> getErrors() {
            return Collections.unmodifiableList(errors);
        }
    }

    /**
     * Parses a required string field.
     * Adds error to collector if missing or empty.
     *
     * @param fm the frontmatter map
     * @param field the field name
     * @param errors the error collector
     * @return Optional containing the string value, or empty if missing/invalid
     */
    protected Optional<String> parseRequiredString(Map<String, Object> fm, String field, ErrorCollector errors) {
        Object value = fm.get(field);
        if (value == null) {
            errors.missingField(field);
            return Optional.empty();
        }
        if (!(value instanceof String)) {
            errors.invalidField(field, "expected string, got " + value.getClass().getSimpleName());
            return Optional.empty();
        }
        String str = ((String) value).trim();
        if (str.isEmpty()) {
            errors.missingField(field);
            return Optional.empty();
        }
        return Optional.of(str);
    }

    /**
     * Parses an optional string field.
     *
     * @param fm the frontmatter map
     * @param field the field name
     * @return Optional containing the string value, or empty if not present
     */
    protected Optional<String> parseOptionalString(Map<String, Object> fm, String field) {
        Object value = fm.get(field);
        if (value instanceof String s && !s.trim().isEmpty()) {
            return Optional.of(s.trim());
        }
        return Optional.empty();
    }

    /**
     * Parses a list of strings field.
     * Adds error to collector if not a list or contains non-strings.
     *
     * @param fm the frontmatter map
     * @param field the field name
     * @param errors the error collector
     * @return List of strings, empty if not present or invalid
     */
    protected List<String> parseStringList(Map<String, Object> fm, String field, ErrorCollector errors) {
        Object value = fm.get(field);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s) {
                    result.add(s);
                } else {
                    errors.invalidField(field,
                        "list item must be string, got " + item.getClass().getSimpleName());
                }
            }
            return result;
        }
        errors.invalidField(field, "expected list, got " + value.getClass().getSimpleName());
        return List.of();
    }

    /**
     * Parses a map field (for schemas, etc).
     *
     * @param fm the frontmatter map
     * @param field the field name
     * @param errors the error collector
     * @return Map, empty if not present or invalid
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> parseMap(Map<String, Object> fm, String field, ErrorCollector errors) {
        Object value = fm.get(field);
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map) {
            return new HashMap<>((Map<String, Object>) value);
        }
        errors.invalidField(field, "expected map, got " + value.getClass().getSimpleName());
        return Map.of();
    }

    /**
     * Parses an ISO-8601 timestamp field.
     * Adds error to collector if format is invalid.
     *
     * @param fm the frontmatter map
     * @param field the field name
     * @param errors the error collector
     * @return Instant, or null if not present or invalid
     */
    protected Instant parseInstant(Map<String, Object> fm, String field, ErrorCollector errors) {
        Object value = fm.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String str)) {
            errors.invalidField(field, "expected string, got " + value.getClass().getSimpleName());
            return null;
        }
        try {
            return Instant.parse(str);
        } catch (DateTimeParseException e) {
            errors.invalidField(field, "invalid ISO-8601 timestamp: " + str);
            return null;
        }
    }
}

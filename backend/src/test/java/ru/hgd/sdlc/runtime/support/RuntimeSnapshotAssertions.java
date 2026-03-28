package ru.hgd.sdlc.runtime.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;

public final class RuntimeSnapshotAssertions {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
    );
    private static final Pattern INSTANT_PATTERN = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z"
    );
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)\\b[0-9a-f]{7,40}\\b");

    private RuntimeSnapshotAssertions() {
    }

    public static void assertJsonSnapshot(
            ObjectMapper objectMapper,
            String actualJson,
            String snapshotRelativePath,
            List<Path> dynamicRoots
    ) throws Exception {
        JsonNode actualNode = objectMapper.readTree(actualJson);
        JsonNode sanitizedNode = sanitizeNode(objectMapper, actualNode, null, dynamicRoots == null ? List.of() : dynamicRoots);
        String normalizedActual = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sanitizedNode) + "\n";

        Path snapshotPath = Path.of("src/test/resources").resolve(snapshotRelativePath);
        boolean updateSnapshots = Boolean.getBoolean("runtime.snapshot.update");
        if (updateSnapshots || !Files.exists(snapshotPath)) {
            Files.createDirectories(snapshotPath.getParent());
            Files.writeString(snapshotPath, normalizedActual, StandardCharsets.UTF_8);
        }

        if (!Files.exists(snapshotPath)) {
            Assertions.fail("Snapshot file does not exist: " + snapshotPath
                    + " (run with -Druntime.snapshot.update=true to generate)");
        }

        String expected = Files.readString(snapshotPath, StandardCharsets.UTF_8);
        Assertions.assertEquals(expected, normalizedActual, "Snapshot mismatch for " + snapshotRelativePath);
    }

    private static JsonNode sanitizeNode(
            ObjectMapper objectMapper,
            JsonNode source,
            String fieldName,
            List<Path> dynamicRoots
    ) {
        if (source == null || source.isNull()) {
            return source;
        }
        if (source.isObject()) {
            ObjectNode objectNode = objectMapper.createObjectNode();
            TreeSet<String> fieldNames = new TreeSet<>();
            Iterator<String> namesIterator = source.fieldNames();
            while (namesIterator.hasNext()) {
                fieldNames.add(namesIterator.next());
            }
            for (String name : fieldNames) {
                objectNode.set(name, sanitizeNode(objectMapper, source.get(name), name, dynamicRoots));
            }
            return objectNode;
        }
        if (source.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (JsonNode item : source) {
                arrayNode.add(sanitizeNode(objectMapper, item, fieldName, dynamicRoots));
            }
            return arrayNode;
        }
        if (source.isTextual()) {
            String sanitized = sanitizeString(source.asText(), fieldName, dynamicRoots);
            return objectMapper.getNodeFactory().textNode(sanitized);
        }
        return source;
    }

    private static String sanitizeString(String value, String fieldName, List<Path> dynamicRoots) {
        if (value == null) {
            return null;
        }
        String result = value;

        for (Path root : dynamicRoots) {
            if (root == null) {
                continue;
            }
            String prefix = root.toAbsolutePath().normalize().toString();
            if (!prefix.isBlank()) {
                result = result.replace(prefix, "<path>");
                result = result.replace(prefix.replace("\\", "/"), "<path>");
            }
        }

        result = UUID_PATTERN.matcher(result).replaceAll("<uuid>");
        result = INSTANT_PATTERN.matcher(result).replaceAll("<instant>");

        if (fieldName != null && fieldName.toLowerCase().contains("checksum")) {
            return "<checksum>";
        }
        if (HEX_PATTERN.matcher(result).matches()) {
            return "<hex>";
        }

        if ("patch".equals(fieldName)) {
            result = HEX_PATTERN.matcher(result).replaceAll("<hex>");
            return result;
        }

        if (isPathField(fieldName) && looksLikeAbsolutePath(result)) {
            return "<path>";
        }

        return result;
    }

    private static boolean isPathField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String normalized = fieldName.toLowerCase();
        return normalized.contains("path")
                || normalized.contains("root")
                || normalized.contains("workspace");
    }

    private static boolean looksLikeAbsolutePath(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.startsWith("/")) {
            return true;
        }
        return value.matches("^[A-Za-z]:\\\\.*");
    }
}

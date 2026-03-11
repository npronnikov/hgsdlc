package ru.hgd.sdlc.compiler.domain.writer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FrontmatterWriter")
class FrontmatterWriterTest {

    private FrontmatterWriter writer;

    @BeforeEach
    void setUp() {
        writer = new FrontmatterWriter();
    }

    @Nested
    @DisplayName("simple values")
    class SimpleValuesTest {

        @Test
        @DisplayName("writes string values")
        void writesStringValues() {
            Map<String, Object> fm = Map.of("name", "Test Flow");
            String result = writer.write(fm);

            assertTrue(result.contains("name: Test Flow"));
        }

        @Test
        @DisplayName("writes numeric values")
        void writesNumericValues() {
            Map<String, Object> fm = Map.of("count", 42, "price", 19.99);
            String result = writer.write(fm);

            assertTrue(result.contains("count: 42"));
            assertTrue(result.contains("price: 19.99"));
        }

        @Test
        @DisplayName("writes boolean values")
        void writesBooleanValues() {
            Map<String, Object> fm = Map.of("enabled", true, "disabled", false);
            String result = writer.write(fm);

            assertTrue(result.contains("enabled: true"));
            assertTrue(result.contains("disabled: false"));
        }

        @Test
        @DisplayName("writes null values")
        void writesNullValues() {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("optional", null);
            String result = writer.write(fm);

            assertTrue(result.contains("optional: null"));
        }

        @Test
        @DisplayName("writes empty string as quoted empty")
        void writesEmptyStringAsQuotedEmpty() {
            Map<String, Object> fm = Map.of("value", "");
            String result = writer.write(fm);

            assertTrue(result.contains("value: \"\""));
        }
    }

    @Nested
    @DisplayName("string escaping")
    class StringEscapingTest {

        @Test
        @DisplayName("quotes strings with colons")
        void quotesStringsWithColons() {
            Map<String, Object> fm = Map.of("value", "key: value");
            String result = writer.write(fm);

            assertTrue(result.contains("value: \"key: value\""));
        }

        @Test
        @DisplayName("quotes strings that look like booleans")
        void quotesStringsThatLookLikeBooleans() {
            Map<String, Object> fm = Map.of("value", "true");
            String result = writer.write(fm);

            assertTrue(result.contains("value: \"true\""));
        }

        @Test
        @DisplayName("escapes double quotes in strings")
        void escapesDoubleQuotesInStrings() {
            Map<String, Object> fm = Map.of("value", "say \"hello\"");
            String result = writer.write(fm);

            assertTrue(result.contains("\\\"hello\\\""));
        }

        @Test
        @DisplayName("escapes newlines in strings")
        void escapesNewlinesInStrings() {
            Map<String, Object> fm = Map.of("value", "line1\nline2");
            String result = writer.write(fm);

            assertTrue(result.contains("line1\\nline2"));
        }

        @Test
        @DisplayName("handles special YAML characters")
        void handlesSpecialYamlCharacters() {
            Map<String, Object> fm = Map.of("value", "[test]");
            String result = writer.write(fm);

            assertTrue(result.contains("value: \"[test]\""));
        }
    }

    @Nested
    @DisplayName("collections")
    class CollectionsTest {

        @Test
        @DisplayName("writes empty list")
        void writesEmptyList() {
            Map<String, Object> fm = Map.of("items", List.of());
            String result = writer.write(fm);

            assertTrue(result.contains("items: []"));
        }

        @Test
        @DisplayName("writes simple list with compact notation")
        void writesSimpleListWithCompactNotation() {
            Map<String, Object> fm = Map.of("items", List.of("a", "b", "c"));
            String result = writer.write(fm);

            assertTrue(result.contains("items: [a, b, c]"));
        }

        @Test
        @DisplayName("writes list of roles")
        void writesListOfRoles() {
            Map<String, Object> fm = Map.of("roles", List.of("developer", "architect"));
            String result = writer.write(fm);

            assertTrue(result.contains("roles: [developer, architect]"));
        }

        @Test
        @DisplayName("writes list with block notation for complex items")
        void writesListWithBlockNotationForComplexItems() {
            Map<String, Object> fm = Map.of("items", List.of(
                Map.of("name", "item1", "value", 1),
                Map.of("name", "item2", "value", 2)
            ));
            String result = writer.write(fm);

            assertTrue(result.contains("items:"));
            // Check that we have list items with map content
            assertTrue(result.contains("-"));
            assertTrue(result.contains("name:"));
            assertTrue(result.contains("item1"));
            assertTrue(result.contains("item2"));
        }
    }

    @Nested
    @DisplayName("nested maps")
    class NestedMapsTest {

        @Test
        @DisplayName("writes empty nested map")
        void writesEmptyNestedMap() {
            Map<String, Object> fm = Map.of("config", Map.of());
            String result = writer.write(fm);

            assertTrue(result.contains("config: {}"));
        }

        @Test
        @DisplayName("writes nested map")
        void writesNestedMap() {
            Map<String, Object> fm = Map.of(
                "metadata", Map.of("author", "John", "version", 1)
            );
            String result = writer.write(fm);

            assertTrue(result.contains("metadata:"));
            assertTrue(result.contains("  author: John"));
            assertTrue(result.contains("  version: 1"));
        }

        @Test
        @DisplayName("writes deeply nested map")
        void writesDeeplyNestedMap() {
            Map<String, Object> fm = Map.of(
                "level1", Map.of(
                    "level2", Map.of(
                        "level3", "value"
                    )
                )
            );
            String result = writer.write(fm);

            assertTrue(result.contains("level1:"));
            assertTrue(result.contains("  level2:"));
            assertTrue(result.contains("    level3: value"));
        }
    }

    @Nested
    @DisplayName("field ordering")
    class FieldOrderingTest {

        @Test
        @DisplayName("orders fields alphabetically by default")
        void ordersFieldsAlphabeticallyByDefault() {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("zebra", "last");
            fm.put("alpha", "first");
            fm.put("middle", "middle");

            String result = writer.write(fm);
            String[] lines = result.split("\n");

            assertTrue(lines[0].startsWith("alpha:"));
            assertTrue(lines[1].startsWith("middle:"));
            assertTrue(lines[2].startsWith("zebra:"));
        }

        @Test
        @DisplayName("respects custom field order")
        void respectsCustomFieldOrder() {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("c", "3");
            fm.put("a", "1");
            fm.put("b", "2");

            List<String> order = List.of("b", "a", "c");
            String result = writer.write(fm, order);

            String[] lines = result.split("\n");
            assertTrue(lines[0].startsWith("b:"));
            assertTrue(lines[1].startsWith("a:"));
            assertTrue(lines[2].startsWith("c:"));
        }

        @Test
        @DisplayName("appends unordered fields at end alphabetically")
        void appendsUnorderedFieldsAtEndAlphabetically() {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("extra1", "e1");
            fm.put("priority", "p");
            fm.put("extra2", "e2");

            List<String> order = List.of("priority");
            String result = writer.write(fm, order);

            String[] lines = result.split("\n");
            assertTrue(lines[0].startsWith("priority:"));
            assertTrue(lines[1].startsWith("extra1:"));
            assertTrue(lines[2].startsWith("extra2:"));
        }
    }

    @Nested
    @DisplayName("timestamps")
    class TimestampsTest {

        @Test
        @DisplayName("writes Instant in ISO-8601 format")
        void writesInstantInIso8601Format() {
            Instant now = Instant.parse("2024-01-15T10:30:00Z");
            Map<String, Object> fm = Map.of("created_at", now);
            String result = writer.write(fm);

            assertTrue(result.contains("created_at: 2024-01-15T10:30:00Z"));
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("returns empty string for null map")
        void returnsEmptyStringForNullMap() {
            String result = writer.write(null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("returns empty string for empty map")
        void returnsEmptyStringForEmptyMap() {
            String result = writer.write(Map.of());
            assertEquals("", result);
        }

        @Test
        @DisplayName("handles unicode characters")
        void handlesUnicodeCharacters() {
            Map<String, Object> fm = Map.of("value", "Hello \u65e5\u672c\u8a9e");
            String result = writer.write(fm);

            assertTrue(result.contains("value: Hello \u65e5\u672c\u8a9e"));
        }
    }
}

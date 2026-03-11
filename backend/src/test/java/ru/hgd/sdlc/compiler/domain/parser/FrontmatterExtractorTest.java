package ru.hgd.sdlc.compiler.domain.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.shared.kernel.Result;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FrontmatterExtractor")
class FrontmatterExtractorTest {

    private FrontmatterExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new FrontmatterExtractor();
    }

    @Nested
    @DisplayName("extraction")
    class ExtractionTest {

        @Test
        @DisplayName("extracts simple frontmatter")
        void extractsSimpleFrontmatter() {
            String markdown = """
                ---
                id: my-flow
                version: 1.0.0
                ---

                # My Flow

                This is the body.
                """;

            Result<ParsedMarkdown, ParseError> result = extractor.extract(markdown);

            assertTrue(result.isSuccess());
            ParsedMarkdown parsed = result.getValue();

            assertTrue(parsed.hasFrontmatter());
            assertEquals("my-flow", parsed.getString("id"));
            assertEquals("1.0.0", parsed.getString("version"));
            assertTrue(parsed.body().content().contains("# My Flow"));
        }

        @Test
        @DisplayName("extracts frontmatter with list values")
        void extractsFrontmatterWithListValues() {
            String markdown = """
                ---
                id: test-flow
                phase_order:
                  - setup
                  - develop
                  - review
                start_roles: [developer, architect]
                ---

                Body content.
                """;

            Result<ParsedMarkdown, ParseError> result = extractor.extract(markdown);

            assertTrue(result.isSuccess());
            ParsedMarkdown parsed = result.getValue();

            assertEquals("test-flow", parsed.getString("id"));
            assertNotNull(parsed.get("phase_order"));
            assertNotNull(parsed.get("start_roles"));
        }

        @Test
        @DisplayName("extracts frontmatter with nested objects")
        void extractsFrontmatterWithNestedObjects() {
            String markdown = """
                ---
                id: complex-flow
                metadata:
                  author: John Doe
                  created_at: 2024-01-15
                config:
                  timeout: 30
                  retries: 3
                ---

                Body.
                """;

            Result<ParsedMarkdown, ParseError> result = extractor.extract(markdown);

            assertTrue(result.isSuccess());
            ParsedMarkdown parsed = result.getValue();

            assertEquals("complex-flow", parsed.getString("id"));
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
            assertNotNull(metadata);
            assertEquals("John Doe", metadata.get("author"));
        }

        @Test
        @DisplayName("handles empty frontmatter")
        void handlesEmptyFrontmatter() {
            String markdown = """
                ---
                ---

                # Body only
                """;

            Result<ParsedMarkdown, ParseError> result = extractor.extract(markdown);

            assertTrue(result.isSuccess());
            ParsedMarkdown parsed = result.getValue();
            assertTrue(parsed.frontmatter().isEmpty());
        }

        @Test
        @DisplayName("handles markdown without frontmatter")
        void handlesMarkdownWithoutFrontmatter() {
            String markdown = """
                # Just a Title

                No frontmatter here.
                """;

            Result<ParsedMarkdown, ParseError> result = extractor.extract(markdown);

            assertTrue(result.isSuccess());
            ParsedMarkdown parsed = result.getValue();
            assertFalse(parsed.hasFrontmatter());
            assertTrue(markdown.startsWith(parsed.body().content().substring(0, 10)));
        }

        @Test
        @DisplayName("preserves body bytes exactly")
        void preservesBodyBytesExactly() {
            String markdown = """
                ---
                id: test
                ---

                # Header

                Body with special characters: äöü 日本語 🎉
                """;

            byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
            Result<ParsedMarkdown, ParseError> result = extractor.extract(bytes);

            assertTrue(result.isSuccess());
            ParsedMarkdown parsed = result.getValue();

            // Verify body contains the special characters correctly
            String bodyContent = parsed.body().content();
            assertTrue(bodyContent.contains("äöü"));
            assertTrue(bodyContent.contains("日本語"));
            assertTrue(bodyContent.contains("🎉"));
        }
    }

    @Nested
    @DisplayName("error handling")
    class ErrorHandlingTest {

        @Test
        @DisplayName("returns error for unclosed frontmatter")
        void returnsErrorForUnclosedFrontmatter() {
            String markdown = """
                ---
                id: my-flow
                version: 1.0.0

                # Missing closing delimiter
                """;

            Result<ParsedMarkdown, ParseError> result = extractor.extract(markdown);

            assertTrue(result.isFailure());
            ParseError error = result.getError();
            assertEquals("E1003", error.code());
            assertTrue(error.message().contains("Unclosed"));
        }

        @Test
        @DisplayName("returns error for malformed YAML")
        void returnsErrorForMalformedYaml() {
            String markdown = """
                ---
                id: my-flow
                invalid: [unclosed
                ---

                Body.
                """;

            Result<ParsedMarkdown, ParseError> result = extractor.extract(markdown);

            assertTrue(result.isFailure());
            ParseError error = result.getError();
            assertEquals("E1002", error.code());
            assertTrue(error.message().contains("Malformed"));
        }

        @Test
        @DisplayName("handles empty input")
        void handlesEmptyInput() {
            Result<ParsedMarkdown, ParseError> result = extractor.extract("");

            assertTrue(result.isSuccess());
            ParsedMarkdown parsed = result.getValue();
            assertFalse(parsed.hasFrontmatter());
            assertTrue(parsed.body().isEmpty());
        }

        @Test
        @DisplayName("handles null input")
        void handlesNullInput() {
            Result<ParsedMarkdown, ParseError> result = extractor.extract((byte[]) null);

            assertTrue(result.isSuccess());
            ParsedMarkdown parsed = result.getValue();
            assertTrue(parsed.body().isEmpty());
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("handles frontmatter with dashes in content")
        void handlesFrontmatterWithDashesInContent() {
            String markdown = """
                ---
                id: test-flow
                description: This has --- dashes in it
                ---

                Body.
                """;

            Result<ParsedMarkdown, ParseError> result = extractor.extract(markdown);

            assertTrue(result.isSuccess());
            ParsedMarkdown parsed = result.getValue();
            assertEquals("This has --- dashes in it", parsed.getString("description"));
        }

        @Test
        @DisplayName("handles Windows line endings")
        void handlesWindowsLineEndings() {
            String markdown = "---\r\nid: test\r\nversion: 1.0.0\r\n---\r\n\r\nBody.\r\n";

            Result<ParsedMarkdown, ParseError> result = extractor.extract(markdown);

            assertTrue(result.isSuccess());
            ParsedMarkdown parsed = result.getValue();
            assertEquals("test", parsed.getString("id"));
        }

        @Test
        @DisplayName("does not treat --- in body as frontmatter")
        void doesNotTreatBodyDashesAsFrontmatter() {
            String markdown = """
                # Title

                Some text with --- in the middle.

                More text.
                """;

            Result<ParsedMarkdown, ParseError> result = extractor.extract(markdown);

            assertTrue(result.isSuccess());
            ParsedMarkdown parsed = result.getValue();
            assertFalse(parsed.hasFrontmatter());
            assertTrue(parsed.body().content().contains("---"));
        }

        @Test
        @DisplayName("tracks frontmatter line count")
        void tracksFrontmatterLineCount() {
            String markdown = """
                ---
                id: test
                version: 1.0.0
                author: John
                ---

                Body starts here.
                """;

            Result<ParsedMarkdown, ParseError> result = extractor.extract(markdown);

            assertTrue(result.isSuccess());
            ParsedMarkdown parsed = result.getValue();
            // 5 lines: ---, id, version, author, ---
            assertEquals(5, parsed.frontmatterLineCount());
        }
    }

    @Nested
    @DisplayName("byte preservation")
    class BytePreservationTest {

        @Test
        @DisplayName("preserves exact body bytes")
        void preservesExactBodyBytes() {
            String markdown = "---\nid: test\n---\n\nBody content here.";
            byte[] originalBytes = markdown.getBytes(StandardCharsets.UTF_8);

            Result<ParsedMarkdown, ParseError> result = extractor.extract(originalBytes);

            assertTrue(result.isSuccess());
            ParsedMarkdown parsed = result.getValue();

            // Get the body bytes and compare
            byte[] bodyBytes = parsed.body().rawBytes();
            String bodyContent = new String(bodyBytes, StandardCharsets.UTF_8);

            assertTrue(bodyContent.contains("Body content here."));
        }

        @Test
        @DisplayName("handles different encodings in body")
        void handlesDifferentEncodingsInBody() {
            // Create markdown with UTF-8 content
            String body = "日本語のコンテンツ";
            String markdown = "---\nid: test\n---\n\n" + body;

            Result<ParsedMarkdown, ParseError> result = extractor.extract(markdown);

            assertTrue(result.isSuccess());
            ParsedMarkdown parsed = result.getValue();

            assertEquals(body, parsed.body().content().trim());
        }
    }
}

package ru.hgd.sdlc.compiler.domain.model.authored;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MarkdownBody")
class MarkdownBodyTest {

    @Nested
    @DisplayName("of(byte[])")
    class OfBytes {

        @Test
        @DisplayName("creates from bytes")
        void createsFromBytes() {
            byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
            MarkdownBody body = MarkdownBody.of(data);

            assertEquals(13, body.length());
            assertEquals("Hello, World!", body.content());
        }

        @Test
        @DisplayName("returns empty for null")
        void returnsEmptyForNull() {
            MarkdownBody body = MarkdownBody.of((byte[]) null);

            assertTrue(body.isEmpty());
        }

        @Test
        @DisplayName("returns empty for empty array")
        void returnsEmptyForEmptyArray() {
            MarkdownBody body = MarkdownBody.of(new byte[0]);

            assertTrue(body.isEmpty());
        }

        @Test
        @DisplayName("defensive copy on creation")
        void defensiveCopyOnCreation() {
            byte[] data = "original".getBytes(StandardCharsets.UTF_8);
            MarkdownBody body = MarkdownBody.of(data);

            data[0] = 'X'; // Modify original

            assertEquals("original", body.content());
        }

        @Test
        @DisplayName("defensive copy on retrieval")
        void defensiveCopyOnRetrieval() {
            byte[] data = "original".getBytes(StandardCharsets.UTF_8);
            MarkdownBody body = MarkdownBody.of(data);

            byte[] retrieved = body.rawBytes();
            retrieved[0] = 'X'; // Modify retrieved

            assertEquals("original", body.content());
        }
    }

    @Nested
    @DisplayName("of(String)")
    class OfString {

        @Test
        @DisplayName("creates from string")
        void createsFromString() {
            MarkdownBody body = MarkdownBody.of("Hello, World!");

            assertEquals("Hello, World!", body.content());
        }

        @Test
        @DisplayName("returns empty for null")
        void returnsEmptyForNull() {
            MarkdownBody body = MarkdownBody.of((String) null);

            assertTrue(body.isEmpty());
        }

        @Test
        @DisplayName("returns empty for empty string")
        void returnsEmptyForEmptyString() {
            MarkdownBody body = MarkdownBody.of("");

            assertTrue(body.isEmpty());
        }

        @Test
        @DisplayName("preserves special characters")
        void preservesSpecialCharacters() {
            String content = "# Header\n\n- item 1\n- item 2\n\n```java\ncode\n```\n";
            MarkdownBody body = MarkdownBody.of(content);

            assertEquals(content, body.content());
        }

        @Test
        @DisplayName("preserves different line endings")
        void preservesDifferentLineEndings() {
            String content = "line1\r\nline2\nline3\r";
            MarkdownBody body = MarkdownBody.of(content);

            assertEquals(content, body.content());
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("equal for same content")
        void equalForSameContent() {
            MarkdownBody body1 = MarkdownBody.of("content");
            MarkdownBody body2 = MarkdownBody.of("content");

            assertEquals(body1, body2);
            assertEquals(body1.hashCode(), body2.hashCode());
        }

        @Test
        @DisplayName("equal for same bytes")
        void equalForSameBytes() {
            MarkdownBody body1 = MarkdownBody.of("content".getBytes(StandardCharsets.UTF_8));
            MarkdownBody body2 = MarkdownBody.of("content".getBytes(StandardCharsets.UTF_8));

            assertEquals(body1, body2);
        }

        @Test
        @DisplayName("not equal for different content")
        void notEqualForDifferentContent() {
            MarkdownBody body1 = MarkdownBody.of("content1");
            MarkdownBody body2 = MarkdownBody.of("content2");

            assertNotEquals(body1, body2);
        }
    }

    @Nested
    @DisplayName("round-trip")
    class RoundTrip {

        @Test
        @DisplayName("byte round-trip preserves data")
        void byteRoundTripPreservesData() {
            byte[] original = "Hello\nWorld\u00e9".getBytes(StandardCharsets.UTF_8);

            MarkdownBody body = MarkdownBody.of(original);
            byte[] retrieved = body.rawBytes();

            assertArrayEquals(original, retrieved);
        }

        @Test
        @DisplayName("string round-trip preserves data")
        void stringRoundTripPreservesData() {
            String original = "Hello\nWorld\u00e9";

            MarkdownBody body = MarkdownBody.of(original);
            String retrieved = body.content();

            assertEquals(original, retrieved);
        }
    }

    @Test
    @DisplayName("toString shows preview")
    void toStringShowsPreview() {
        MarkdownBody body = MarkdownBody.of("Hello, World!");

        assertTrue(body.toString().contains("Hello"));
        assertTrue(body.toString().contains("bytes"));
    }

    @Test
    @DisplayName("toString handles empty")
    void toStringHandlesEmpty() {
        MarkdownBody body = MarkdownBody.of("");

        assertTrue(body.toString().contains("empty"));
    }
}

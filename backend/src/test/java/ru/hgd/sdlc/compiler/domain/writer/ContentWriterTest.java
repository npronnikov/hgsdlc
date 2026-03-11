package ru.hgd.sdlc.compiler.domain.writer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.MarkdownBody;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContentWriter")
class ContentWriterTest {

    private ContentWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ContentWriter();
    }

    @Nested
    @DisplayName("write MarkdownBody")
    class WriteMarkdownBodyTest {

        @Test
        @DisplayName("returns empty string for null body")
        void returnsEmptyStringForNullBody() {
            String result = writer.write((MarkdownBody) null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("returns empty string for empty body")
        void returnsEmptyStringForEmptyBody() {
            String result = writer.write(MarkdownBody.of(""));
            assertEquals("", result);
        }

        @Test
        @DisplayName("returns body content as-is")
        void returnsBodyContentAsIs() {
            MarkdownBody body = MarkdownBody.of("# Header\n\nParagraph");
            String result = writer.write(body);

            assertEquals("# Header\n\nParagraph", result);
        }

        @Test
        @DisplayName("preserves unicode characters")
        void preservesUnicodeCharacters() {
            MarkdownBody body = MarkdownBody.of("Japanese: \u65e5\u672c\u8a9e Emoji: \ud83c\udf89");
            String result = writer.write(body);

            assertTrue(result.contains("\u65e5\u672c\u8a9e"));
            assertTrue(result.contains("\ud83c\udf89"));
        }
    }

    @Nested
    @DisplayName("write string")
    class WriteStringTest {

        @Test
        @DisplayName("returns empty string for null")
        void returnsEmptyStringForNull() {
            String result = writer.write((String) null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("returns empty string for empty")
        void returnsEmptyStringForEmpty() {
            String result = writer.write("");
            assertEquals("", result);
        }

        @Test
        @DisplayName("returns content as-is")
        void returnsContentAsIs() {
            String result = writer.write("Some content");
            assertEquals("Some content", result);
        }
    }

    @Nested
    @DisplayName("writeWithSeparator")
    class WriteWithSeparatorTest {

        @Test
        @DisplayName("returns empty for null body")
        void returnsEmptyForNullBody() {
            String result = writer.writeWithSeparator(null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("adds newline if body does not start with one")
        void addsNewlineIfBodyDoesNotStartWithOne() {
            MarkdownBody body = MarkdownBody.of("Content");
            String result = writer.writeWithSeparator(body);

            assertTrue(result.startsWith("\n"));
            assertTrue(result.contains("Content"));
        }

        @Test
        @DisplayName("does not add extra newline if body already starts with one")
        void doesNotAddExtraNewlineIfBodyAlreadyStartsWithOne() {
            MarkdownBody body = MarkdownBody.of("\nContent");
            String result = writer.writeWithSeparator(body);

            assertEquals("\nContent", result);
        }
    }

    @Nested
    @DisplayName("writeFormatted")
    class WriteFormattedTest {

        @Test
        @DisplayName("returns empty for null body")
        void returnsEmptyForNullBody() {
            String result = writer.writeFormatted(null);
            assertEquals("", result);
        }

        @Test
        @DisplayName("returns empty for blank body")
        void returnsEmptyForBlankBody() {
            String result = writer.writeFormatted(MarkdownBody.of("   \n   "));
            assertEquals("", result);
        }

        @Test
        @DisplayName("adds leading and trailing newlines")
        void addsLeadingAndTrailingNewlines() {
            MarkdownBody body = MarkdownBody.of("Content");
            String result = writer.writeFormatted(body);

            assertEquals("\nContent\n", result);
        }

        @Test
        @DisplayName("does not add leading newline if already present")
        void doesNotAddLeadingNewlineIfAlreadyPresent() {
            MarkdownBody body = MarkdownBody.of("\nContent");
            String result = writer.writeFormatted(body);

            assertTrue(result.startsWith("\nContent"));
        }

        @Test
        @DisplayName("does not add trailing newline if already present")
        void doesNotAddTrailingNewlineIfAlreadyPresent() {
            MarkdownBody body = MarkdownBody.of("Content\n");
            String result = writer.writeFormatted(body);

            assertTrue(result.endsWith("Content\n"));
        }
    }

    @Nested
    @DisplayName("hasCodeBlocks")
    class HasCodeBlocksTest {

        @Test
        @DisplayName("returns false for null body")
        void returnsFalseForNullBody() {
            assertFalse(writer.hasCodeBlocks(null));
        }

        @Test
        @DisplayName("returns false for body without code blocks")
        void returnsFalseForBodyWithoutCodeBlocks() {
            MarkdownBody body = MarkdownBody.of("# Header\n\nNo code here");
            assertFalse(writer.hasCodeBlocks(body));
        }

        @Test
        @DisplayName("returns true for body with triple backticks")
        void returnsTrueForBodyWithTripleBackticks() {
            MarkdownBody body = MarkdownBody.of("```java\ncode\n```");
            assertTrue(writer.hasCodeBlocks(body));
        }

        @Test
        @DisplayName("returns true for body with tilde fences")
        void returnsTrueForBodyWithTildeFences() {
            MarkdownBody body = MarkdownBody.of("~~~\ncode\n~~~");
            assertTrue(writer.hasCodeBlocks(body));
        }
    }

    @Nested
    @DisplayName("escapeMarkdown")
    class EscapeMarkdownTest {

        @Test
        @DisplayName("returns empty for null")
        void returnsEmptyForNull() {
            assertEquals("", writer.escapeMarkdown(null));
        }

        @Test
        @DisplayName("returns empty for empty string")
        void returnsEmptyForEmptyString() {
            assertEquals("", writer.escapeMarkdown(""));
        }

        @Test
        @DisplayName("escapes asterisks")
        void escapesAsterisks() {
            assertEquals("\\*bold\\*", writer.escapeMarkdown("*bold*"));
        }

        @Test
        @DisplayName("escapes underscores")
        void escapesUnderscores() {
            assertEquals("\\_italic\\_", writer.escapeMarkdown("_italic_"));
        }

        @Test
        @DisplayName("escapes backticks")
        void escapesBackticks() {
            assertEquals("\\`code\\`", writer.escapeMarkdown("`code`"));
        }

        @Test
        @DisplayName("escapes hash symbols")
        void escapesHashSymbols() {
            assertEquals("\\# Header", writer.escapeMarkdown("# Header"));
        }

        @Test
        @DisplayName("escapes brackets")
        void escapesBrackets() {
            assertEquals("\\[link\\]\\(url\\)", writer.escapeMarkdown("[link](url)"));
        }
    }

    @Nested
    @DisplayName("codeBlock")
    class CodeBlockTest {

        @Test
        @DisplayName("returns empty for null code")
        void returnsEmptyForNullCode() {
            assertEquals("", writer.codeBlock(null, "java"));
        }

        @Test
        @DisplayName("creates code block without language")
        void createsCodeBlockWithoutLanguage() {
            String result = writer.codeBlock("code", null);

            assertTrue(result.startsWith("```\n"));
            assertTrue(result.endsWith("```\n"));
            assertTrue(result.contains("code"));
        }

        @Test
        @DisplayName("creates code block with language")
        void createsCodeBlockWithLanguage() {
            String result = writer.codeBlock("code", "java");

            assertTrue(result.startsWith("```java\n"));
            assertTrue(result.endsWith("```\n"));
        }

        @Test
        @DisplayName("ensures code ends with newline")
        void ensuresCodeEndsWithNewline() {
            String result = writer.codeBlock("code", "java");

            assertTrue(result.contains("code\n"));
        }

        @Test
        @DisplayName("does not add extra newline if code already ends with one")
        void doesNotAddExtraNewlineIfCodeAlreadyEndsWithOne() {
            String result = writer.codeBlock("code\n", "java");

            assertEquals(1, countOccurrences(result, "code\n"));
        }
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}

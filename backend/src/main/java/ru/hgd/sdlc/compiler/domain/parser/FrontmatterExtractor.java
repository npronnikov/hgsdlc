package ru.hgd.sdlc.compiler.domain.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import ru.hgd.sdlc.compiler.domain.model.authored.MarkdownBody;
import ru.hgd.sdlc.shared.kernel.Result;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Extracts YAML frontmatter from Markdown files.
 *
 * <p>Frontmatter is delimited by '---' at the start and end of a YAML block.
 * The body content after frontmatter is preserved as raw bytes for byte-identical
 * round-trip serialization.
 *
 * <p>Example Markdown with frontmatter:
 * <pre>
 * ---
 * id: my-flow
 * version: 1.0.0
 * phase_order: [setup, develop]
 * ---
 *
 * # Flow Title
 *
 * Body content here...
 * </pre>
 */
public final class FrontmatterExtractor {

    private static final String FRONTMATTER_DELIMITER = "---";
    private static final int DELIMITER_LENGTH = FRONTMATTER_DELIMITER.length();

    private final ObjectMapper yamlMapper;

    public FrontmatterExtractor() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Extracts frontmatter and body from raw Markdown bytes.
     *
     * @param markdownBytes the raw markdown content as bytes
     * @return a Result containing either ParsedMarkdown or ParseError
     */
    public Result<ParsedMarkdown, ParseError> extract(byte[] markdownBytes) {
        if (markdownBytes == null || markdownBytes.length == 0) {
            return Result.success(ParsedMarkdown.bodyOnly(MarkdownBody.of(new byte[0])));
        }

        // Convert to string for line-by-line parsing (UTF-8)
        String content = new String(markdownBytes, StandardCharsets.UTF_8);

        // Check if file starts with ---
        if (!startsWithFrontmatter(content)) {
            // No frontmatter - entire content is body
            return Result.success(ParsedMarkdown.bodyOnly(MarkdownBody.of(markdownBytes)));
        }

        // Find the closing ---
        int closingIndex = findClosingDelimiter(content);
        if (closingIndex == -1) {
            return Result.failure(ParseError.unclosedFrontmatter());
        }

        // Extract frontmatter content (between the two ---)
        int frontmatterStart = DELIMITER_LENGTH;
        // Skip newline after opening ---
        while (frontmatterStart < content.length() && isNewline(content.charAt(frontmatterStart))) {
            frontmatterStart++;
        }

        String frontmatterText = content.substring(frontmatterStart, closingIndex).trim();

        // Parse YAML
        Map<String, Object> frontmatter;
        if (frontmatterText.isEmpty()) {
            frontmatter = Map.of();
        } else {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = yamlMapper.readValue(frontmatterText, Map.class);
                frontmatter = parsed != null ? parsed : Map.of();
            } catch (IOException e) {
                return Result.failure(ParseError.malformedFrontmatter(e.getMessage(), e));
            }
        }

        // Calculate line count for frontmatter (for error reporting)
        int frontmatterLineCount = countLines(content.substring(0, closingIndex + DELIMITER_LENGTH));

        // Extract body (everything after closing ---)
        int bodyStart = closingIndex + DELIMITER_LENGTH;
        // Skip newlines after closing ---
        while (bodyStart < content.length() && isNewline(content.charAt(bodyStart))) {
            bodyStart++;
        }

        byte[] bodyBytes;
        if (bodyStart >= content.length()) {
            bodyBytes = new byte[0];
        } else {
            // Get the raw bytes of the body portion
            // Need to calculate byte offset
            int byteOffset = getByteOffset(markdownBytes, content, bodyStart);
            bodyBytes = new byte[markdownBytes.length - byteOffset];
            System.arraycopy(markdownBytes, byteOffset, bodyBytes, 0, bodyBytes.length);
        }

        return Result.success(ParsedMarkdown.of(frontmatter, MarkdownBody.of(bodyBytes), frontmatterLineCount));
    }

    /**
     * Extracts frontmatter from a Markdown string.
     *
     * @param markdown the markdown content as a string
     * @return a Result containing either ParsedMarkdown or ParseError
     */
    public Result<ParsedMarkdown, ParseError> extract(String markdown) {
        if (markdown == null) {
            return extract((byte[]) null);
        }
        return extract(markdown.getBytes(StandardCharsets.UTF_8));
    }

    private boolean startsWithFrontmatter(String content) {
        if (content.length() < DELIMITER_LENGTH) {
            return false;
        }
        // Check for --- at the very start
        if (content.startsWith(FRONTMATTER_DELIMITER)) {
            // Make sure it's followed by a newline (not part of a longer string)
            int after = DELIMITER_LENGTH;
            return after >= content.length() || isNewline(content.charAt(after));
        }
        return false;
    }

    private int findClosingDelimiter(String content) {
        // Start after the opening ---
        int searchStart = DELIMITER_LENGTH;

        // Skip newline after opening delimiter
        while (searchStart < content.length() && isNewline(content.charAt(searchStart))) {
            searchStart++;
        }

        // Look for --- on its own line
        int index = searchStart;
        while (index < content.length()) {
            int found = content.indexOf(FRONTMATTER_DELIMITER, index);
            if (found == -1) {
                return -1;
            }

            // Check if it's on its own line
            boolean atLineStart = (found == 0) || isAtLineStart(content, found);
            boolean atLineEnd = (found + DELIMITER_LENGTH >= content.length())
                || isNewline(content.charAt(found + DELIMITER_LENGTH));

            if (atLineStart && atLineEnd) {
                return found;
            }

            index = found + 1;
        }

        return -1;
    }

    private boolean isAtLineStart(String content, int index) {
        // Check if the character before is a newline
        if (index <= 0) return true;
        char prev = content.charAt(index - 1);
        return prev == '\n' || prev == '\r';
    }

    private boolean isNewline(char c) {
        return c == '\n' || c == '\r';
    }

    private int countLines(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private int getByteOffset(byte[] bytes, String content, int charIndex) {
        // UTF-8: most characters are 1 byte, but we need to account for multi-byte chars
        String prefix = content.substring(0, charIndex);
        return prefix.getBytes(StandardCharsets.UTF_8).length;
    }
}

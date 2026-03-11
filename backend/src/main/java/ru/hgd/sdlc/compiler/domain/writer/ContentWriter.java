package ru.hgd.sdlc.compiler.domain.writer;

import ru.hgd.sdlc.compiler.domain.model.authored.MarkdownBody;

/**
 * Writes Markdown body content.
 *
 * <p>This class handles the serialization of markdown body content,
 * preserving formatting and handling special cases like code blocks.
 *
 * <p>The writer ensures proper handling of:
 * <ul>
 *   <li>Empty bodies</li>
 *   <li>Code blocks with language hints</li>
 *   <li>Preservation of original formatting</li>
 *   <li>Line ending normalization</li>
 * </ul>
 */
public final class ContentWriter {

    private static final String LINE_SEPARATOR = "\n";

    /**
     * Writes a MarkdownBody to string representation.
     *
     * <p>If the body is null or empty, returns an empty string.
     * Otherwise, returns the body content as-is to preserve exact formatting.
     *
     * @param body the markdown body to write
     * @return the string representation
     */
    public String write(MarkdownBody body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        return body.content();
    }

    /**
     * Writes a string as markdown body content.
     *
     * @param content the content to write
     * @return the content, or empty string if null
     */
    public String write(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return content;
    }

    /**
     * Writes a body with a leading newline separator.
     * Used to separate frontmatter from body content.
     *
     * @param body the markdown body to write
     * @return the content with leading newline, or empty string if body is empty
     */
    public String writeWithSeparator(MarkdownBody body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String content = body.content();
        if (content.isEmpty()) {
            return "";
        }
        // Ensure body starts on a new line after frontmatter
        if (!content.startsWith("\n")) {
            return LINE_SEPARATOR + content;
        }
        return content;
    }

    /**
     * Writes a body with proper formatting for a document.
     * Adds leading newline if needed and ensures proper ending.
     *
     * @param body the markdown body to write
     * @return the formatted content
     */
    public String writeFormatted(MarkdownBody body) {
        if (body == null || body.isEmpty()) {
            return "";
        }

        String content = body.content();
        if (content.isBlank()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // Ensure body starts on a new line
        if (!content.startsWith("\n")) {
            sb.append(LINE_SEPARATOR);
        }
        sb.append(content);

        // Ensure content ends with newline
        if (!content.endsWith("\n")) {
            sb.append(LINE_SEPARATOR);
        }

        return sb.toString();
    }

    /**
     * Checks if the content contains code blocks.
     *
     * @param body the markdown body to check
     * @return true if the body contains fenced code blocks
     */
    public boolean hasCodeBlocks(MarkdownBody body) {
        if (body == null || body.isEmpty()) {
            return false;
        }
        String content = body.content();
        return content.contains("```") || content.contains("~~~");
    }

    /**
     * Escapes special markdown characters in plain text.
     * Use this when embedding user-provided text that should not be interpreted as markdown.
     *
     * @param text the text to escape
     * @return the escaped text
     */
    public String escapeMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return text
            .replace("\\", "\\\\")
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("`", "\\`")
            .replace("#", "\\#")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .replace("<", "\\<")
            .replace(">", "\\>");
    }

    /**
     * Creates a fenced code block with optional language hint.
     *
     * @param code the code content
     * @param language the language hint (can be null or empty)
     * @return the fenced code block
     */
    public String codeBlock(String code, String language) {
        if (code == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("```");
        if (language != null && !language.isBlank()) {
            sb.append(language.trim());
        }
        sb.append(LINE_SEPARATOR);
        sb.append(code);
        if (!code.endsWith("\n")) {
            sb.append(LINE_SEPARATOR);
        }
        sb.append("```").append(LINE_SEPARATOR);

        return sb.toString();
    }
}

package ru.hgd.sdlc.compiler.domain.model.authored;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable container for Markdown body content.
 * Preserves raw bytes for byte-identical round-trip serialization.
 *
 * <p>This class is critical for maintaining document integrity during
 * parse → write → parse cycles. The body content is stored as bytes
 * to ensure exact preservation of formatting, line endings, and encoding.
 */
public final class MarkdownBody {

    private static final MarkdownBody EMPTY = new MarkdownBody(new byte[0]);

    private final byte[] rawBytes;
    private volatile String cachedContent;

    private MarkdownBody(byte[] rawBytes) {
        this.rawBytes = rawBytes.clone(); // Defensive copy
    }

    /**
     * Creates a MarkdownBody from raw bytes.
     * The bytes are copied to ensure immutability.
     *
     * @param rawBytes the raw bytes of the markdown content
     * @return a new MarkdownBody instance
     */
    public static MarkdownBody of(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) {
            return EMPTY;
        }
        return new MarkdownBody(rawBytes);
    }

    /**
     * Creates a MarkdownBody from a string using UTF-8 encoding.
     *
     * @param content the markdown content as a string
     * @return a new MarkdownBody instance
     */
    public static MarkdownBody of(String content) {
        if (content == null || content.isEmpty()) {
            return EMPTY;
        }
        return new MarkdownBody(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns a copy of the raw bytes.
     * A copy is returned to maintain immutability.
     *
     * @return a copy of the raw bytes
     */
    public byte[] rawBytes() {
        return rawBytes.clone();
    }

    /**
     * Returns the number of bytes in the body.
     *
     * @return the byte length
     */
    public int length() {
        return rawBytes.length;
    }

    /**
     * Checks if the body is empty.
     *
     * @return true if the body has no content
     */
    public boolean isEmpty() {
        return rawBytes.length == 0;
    }

    /**
     * Returns the content as a string using UTF-8 encoding.
     * The result is cached for performance.
     *
     * @return the markdown content as a string
     */
    public String content() {
        if (cachedContent == null) {
            synchronized (this) {
                if (cachedContent == null) {
                    cachedContent = new String(rawBytes, StandardCharsets.UTF_8);
                }
            }
        }
        return cachedContent;
    }

    /**
     * Returns the content as a string using the specified charset.
     *
     * @param charset the charset to use for decoding
     * @return the markdown content as a string
     */
    public String content(Charset charset) {
        return new String(rawBytes, charset);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MarkdownBody that = (MarkdownBody) o;
        return Arrays.equals(rawBytes, that.rawBytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(rawBytes);
    }

    @Override
    public String toString() {
        if (rawBytes.length == 0) {
            return "MarkdownBody{empty}";
        }
        int previewLength = Math.min(50, rawBytes.length);
        String preview = new String(rawBytes, 0, previewLength, StandardCharsets.UTF_8)
            .replace("\n", "\\n")
            .replace("\r", "\\r");
        String suffix = rawBytes.length > previewLength ? "..." : "";
        return "MarkdownBody{" + preview + suffix + " (" + rawBytes.length + " bytes)}";
    }
}

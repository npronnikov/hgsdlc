package ru.hgd.sdlc.compiler.domain.writer;

import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;

/**
 * Writes domain documents to canonical Markdown format.
 *
 * <p>The canonical format preserves:
 * <ul>
 *   <li>YAML frontmatter between --- delimiters</li>
 *   <li>Markdown body content</li>
 *   <li>Deterministic ordering for reproducible output</li>
 * </ul>
 *
 * <p>Implementations must ensure round-trip compatibility:
 * parse → write → parse should produce equivalent documents.
 */
public interface MarkdownWriter {

    /**
     * Writes a FlowDocument to canonical Markdown format.
     *
     * @param document the flow document to write
     * @return the canonical Markdown representation
     * @throws IllegalArgumentException if document is null
     */
    String write(FlowDocument document);

    /**
     * Writes a SkillDocument to canonical Markdown format.
     *
     * @param document the skill document to write
     * @return the canonical Markdown representation
     * @throws IllegalArgumentException if document is null
     */
    String write(SkillDocument document);
}

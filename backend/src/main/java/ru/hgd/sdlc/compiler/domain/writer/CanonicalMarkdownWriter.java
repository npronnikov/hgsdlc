package ru.hgd.sdlc.compiler.domain.writer;

import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.MarkdownBody;
import ru.hgd.sdlc.compiler.domain.model.authored.ResumePolicy;
import ru.hgd.sdlc.compiler.domain.model.authored.Role;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writes domain documents to canonical Markdown format.
 *
 * <p>The canonical format ensures:
 * <ul>
 *   <li>Deterministic output for reproducible serialization</li>
 *   <li>YAML frontmatter between --- delimiters</li>
 *   <li>Preserved markdown body content</li>
 *   <li>Round-trip compatibility with parsers</li>
 * </ul>
 *
 * <p>Field ordering in frontmatter:
 * <ol>
 *   <li>id (required)</li>
 *   <li>name (if present)</li>
 *   <li>version (required)</li>
 *   <li>Document-specific required fields (handler for skills, etc.)</li>
 *   <li>Optional fields in alphabetical order</li>
 * </ol>
 */
public final class CanonicalMarkdownWriter implements MarkdownWriter {

    private static final String FRONTMATTER_DELIMITER = "---\n";
    private static final DateTimeFormatter INSTANT_FORMATTER =
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    private static final List<String> FLOW_FIELD_ORDER = List.of(
        "id", "name", "version", "phase_order", "start_roles", "resume_policy",
        "authored_at", "author"
    );

    private static final List<String> SKILL_FIELD_ORDER = List.of(
        "id", "name", "version", "handler", "input_schema", "output_schema",
        "tags", "authored_at", "author"
    );

    private final FrontmatterWriter frontmatterWriter;
    private final ContentWriter contentWriter;

    /**
     * Creates a new CanonicalMarkdownWriter with default components.
     */
    public CanonicalMarkdownWriter() {
        this.frontmatterWriter = new FrontmatterWriter();
        this.contentWriter = new ContentWriter();
    }

    /**
     * Creates a new CanonicalMarkdownWriter with custom components.
     *
     * @param frontmatterWriter the frontmatter writer to use
     * @param contentWriter the content writer to use
     */
    public CanonicalMarkdownWriter(FrontmatterWriter frontmatterWriter, ContentWriter contentWriter) {
        this.frontmatterWriter = Objects.requireNonNull(frontmatterWriter, "frontmatterWriter cannot be null");
        this.contentWriter = Objects.requireNonNull(contentWriter, "contentWriter cannot be null");
    }

    @Override
    public String write(FlowDocument document) {
        Objects.requireNonNull(document, "document cannot be null");

        Map<String, Object> frontmatter = buildFlowFrontmatter(document);
        String frontmatterYaml = frontmatterWriter.write(frontmatter, FLOW_FIELD_ORDER);
        String body = contentWriter.writeFormatted(document.description());

        return buildDocument(frontmatterYaml, body);
    }

    @Override
    public String write(SkillDocument document) {
        Objects.requireNonNull(document, "document cannot be null");

        Map<String, Object> frontmatter = buildSkillFrontmatter(document);
        String frontmatterYaml = frontmatterWriter.write(frontmatter, SKILL_FIELD_ORDER);
        String body = contentWriter.writeFormatted(document.description());

        return buildDocument(frontmatterYaml, body);
    }

    private Map<String, Object> buildFlowFrontmatter(FlowDocument document) {
        Map<String, Object> fm = new LinkedHashMap<>();

        // Required fields
        fm.put("id", document.id().value());
        fm.put("version", document.version().toString());

        // Optional fields (only if present/non-default)
        if (document.name() != null) {
            fm.put("name", document.name());
        }

        if (document.phaseOrder() != null && !document.phaseOrder().isEmpty()) {
            fm.put("phase_order", document.phaseOrder().stream()
                .map(id -> id.value())
                .sorted()
                .collect(Collectors.toList()));
        }

        if (document.startRoles() != null && !document.startRoles().isEmpty()) {
            fm.put("start_roles", document.startRoles().stream()
                .map(Role::value)
                .sorted()
                .collect(Collectors.toList()));
        }

        if (document.resumePolicy() != ResumePolicy.FROM_CHECKPOINT) {
            fm.put("resume_policy", formatResumePolicy(document.resumePolicy()));
        }

        if (document.authoredAt() != null) {
            fm.put("authored_at", INSTANT_FORMATTER.format(document.authoredAt()));
        }

        if (document.author() != null && !document.author().isBlank()) {
            fm.put("author", document.author());
        }

        return fm;
    }

    private Map<String, Object> buildSkillFrontmatter(SkillDocument document) {
        Map<String, Object> fm = new LinkedHashMap<>();

        // Required fields
        fm.put("id", document.id().value());
        fm.put("name", document.name());
        fm.put("version", document.version().toString());
        fm.put("handler", document.handler().toString());

        // Optional fields (only if present/non-default)
        if (document.inputSchema() != null && !document.inputSchema().isEmpty()) {
            fm.put("input_schema", document.inputSchema());
        }

        if (document.outputSchema() != null && !document.outputSchema().isEmpty()) {
            fm.put("output_schema", document.outputSchema());
        }

        if (document.tags() != null && !document.tags().isEmpty()) {
            fm.put("tags", document.tags().stream()
                .sorted()
                .collect(Collectors.toList()));
        }

        if (document.authoredAt() != null) {
            fm.put("authored_at", INSTANT_FORMATTER.format(document.authoredAt()));
        }

        if (document.author() != null && !document.author().isBlank()) {
            fm.put("author", document.author());
        }

        return fm;
    }

    private String formatResumePolicy(ResumePolicy policy) {
        // Convert enum name to kebab-case (FROM_CHECKPOINT -> from-checkpoint)
        return policy.name().toLowerCase().replace("_", "-");
    }

    private String buildDocument(String frontmatterYaml, String body) {
        StringBuilder sb = new StringBuilder();

        sb.append(FRONTMATTER_DELIMITER);
        if (!frontmatterYaml.isEmpty()) {
            sb.append(frontmatterYaml);
        }
        sb.append(FRONTMATTER_DELIMITER);
        sb.append(body);

        return sb.toString();
    }
}

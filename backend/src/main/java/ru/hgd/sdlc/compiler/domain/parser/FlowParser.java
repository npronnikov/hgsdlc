package ru.hgd.sdlc.compiler.domain.parser;

import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.shared.kernel.Result;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses FlowDocument from ParsedMarkdown.
 * Maps YAML frontmatter fields to typed domain model.
 */
public final class FlowParser extends DocumentParser {

    private static final String FIELD_ID = "id";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_PHASE_ORDER = "phase_order";
    private static final String FIELD_START_ROLES = "start_roles";
    private static final String FIELD_RESUME_POLICY = "resume_policy";
    private static final String FIELD_AUTHORED_AT = "authored_at";
    private static final String FIELD_AUTHOR = "author";

    /**
     * Parses a FlowDocument from ParsedMarkdown.
     * Returns all validation errors instead of failing on the first one.
     *
     * @param parsed the parsed markdown with frontmatter and body
     * @return a ParseResult containing FlowDocument or list of ParseErrors
     */
    public ParseResult<FlowDocument> parse(ParsedMarkdown parsed) {
        Objects.requireNonNull(parsed, "parsed cannot be null");

        Map<String, Object> fm = parsed.frontmatter();
        ErrorCollector errors = new ErrorCollector();

        // Required fields
        FlowId id = parseRequiredString(fm, FIELD_ID, errors)
            .map(FlowId::of)
            .orElse(null);

        SemanticVersion version = parseRequiredString(fm, FIELD_VERSION, errors)
            .map(v -> {
                try {
                    return SemanticVersion.of(v);
                } catch (IllegalArgumentException e) {
                    errors.invalidField(FIELD_VERSION, e.getMessage());
                    return null;
                }
            })
            .orElse(null);

        // Optional fields
        String name = parseOptionalString(fm, FIELD_NAME).orElse(null);

        List<PhaseId> phaseOrder = parseStringList(fm, FIELD_PHASE_ORDER, errors)
            .stream()
            .map(PhaseId::of)
            .toList();

        Set<Role> startRoles = parseStringList(fm, FIELD_START_ROLES, errors)
            .stream()
            .map(Role::of)
            .collect(Collectors.toSet());

        ResumePolicy resumePolicy = parseResumePolicy(fm, errors);

        Instant authoredAt = parseInstant(fm, FIELD_AUTHORED_AT, errors);
        String author = parseOptionalString(fm, FIELD_AUTHOR).orElse(null);

        // Parse body as description
        MarkdownBody description = parsed.body();

        // If there are errors, return failure with all errors
        if (errors.hasErrors()) {
            return ParseResult.failure(errors.getErrors());
        }

        // Build FlowDocument
        FlowDocument document = FlowDocument.builder()
            .id(id)
            .name(name)
            .version(version)
            .description(description)
            .phaseOrder(phaseOrder)
            .startRoles(startRoles)
            .resumePolicy(resumePolicy)
            .authoredAt(authoredAt)
            .author(author)
            .build();

        return ParseResult.success(document);
    }

    /**
     * Legacy method for backward compatibility.
     * Returns Result with first error only.
     *
     * @deprecated Use {@link #parse(ParsedMarkdown)} instead
     */
    @Deprecated
    public Result<FlowDocument, ParseError> parseLegacy(ParsedMarkdown parsed) {
        ParseResult<FlowDocument> result = parse(parsed);
        if (result.isSuccess()) {
            return Result.success(result.getDocument());
        }
        return Result.failure(result.getFirstError());
    }

    private ResumePolicy parseResumePolicy(Map<String, Object> fm, ErrorCollector errors) {
        return parseOptionalString(fm, FIELD_RESUME_POLICY)
            .map(value -> {
                try {
                    return ResumePolicy.valueOf(value.toUpperCase().replace("-", "_"));
                } catch (IllegalArgumentException e) {
                    errors.invalidField(FIELD_RESUME_POLICY,
                        "must be one of: from-checkpoint, from-start, not-allowed");
                    return ResumePolicy.FROM_CHECKPOINT;
                }
            })
            .orElse(ResumePolicy.FROM_CHECKPOINT);
    }
}

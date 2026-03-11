package ru.hgd.sdlc.compiler.domain.parser;

import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.shared.kernel.Result;

import java.time.Instant;
import java.util.*;

/**
 * Parses SkillDocument from ParsedMarkdown.
 * Maps YAML frontmatter fields to typed domain model.
 */
public final class SkillParser extends DocumentParser {

    private static final String FIELD_ID = "id";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_HANDLER = "handler";
    private static final String FIELD_INPUT_SCHEMA = "input_schema";
    private static final String FIELD_OUTPUT_SCHEMA = "output_schema";
    private static final String FIELD_TAGS = "tags";
    private static final String FIELD_AUTHORED_AT = "authored_at";
    private static final String FIELD_AUTHOR = "author";

    /**
     * Parses a SkillDocument from ParsedMarkdown.
     * Returns all validation errors instead of failing on the first one.
     *
     * @param parsed the parsed markdown with frontmatter and body
     * @return a ParseResult containing SkillDocument or list of ParseErrors
     */
    public ParseResult<SkillDocument> parse(ParsedMarkdown parsed) {
        Objects.requireNonNull(parsed, "parsed cannot be null");

        Map<String, Object> fm = parsed.frontmatter();
        ErrorCollector errors = new ErrorCollector();

        // Required fields
        SkillId id = parseRequiredString(fm, FIELD_ID, errors)
            .map(SkillId::of)
            .orElse(null);

        String name = parseRequiredString(fm, FIELD_NAME, errors).orElse(null);

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

        HandlerRef handler = parseRequiredString(fm, FIELD_HANDLER, errors)
            .map(h -> {
                try {
                    return HandlerRef.of(h);
                } catch (IllegalArgumentException e) {
                    errors.invalidField(FIELD_HANDLER, e.getMessage());
                    return null;
                }
            })
            .orElse(null);

        // Optional fields
        Map<String, Object> inputSchema = parseMap(fm, FIELD_INPUT_SCHEMA, errors);
        Map<String, Object> outputSchema = parseMap(fm, FIELD_OUTPUT_SCHEMA, errors);
        List<String> tags = parseStringList(fm, FIELD_TAGS, errors);

        Instant authoredAt = parseInstant(fm, FIELD_AUTHORED_AT, errors);
        String author = parseOptionalString(fm, FIELD_AUTHOR).orElse(null);

        // Parse body as description
        MarkdownBody description = parsed.body();

        // If there are errors, return failure with all errors
        if (errors.hasErrors()) {
            return ParseResult.failure(errors.getErrors());
        }

        // Build SkillDocument
        SkillDocument document = SkillDocument.builder()
            .id(id)
            .name(name)
            .version(version)
            .description(description)
            .handler(handler)
            .inputSchema(inputSchema)
            .outputSchema(outputSchema)
            .tags(tags)
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
    public Result<SkillDocument, ParseError> parseLegacy(ParsedMarkdown parsed) {
        ParseResult<SkillDocument> result = parse(parsed);
        if (result.isSuccess()) {
            return Result.success(result.getDocument());
        }
        return Result.failure(result.getFirstError());
    }
}

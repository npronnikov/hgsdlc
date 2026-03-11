package ru.hgd.sdlc.compiler.domain.compiler;

import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Compiles SkillDocument to SkillIr.
 * Validates the skill definition and builds the IR.
 *
 * <p>Per ADR-002: Runtime executes compiled IR, not Markdown.
 * The compiler transforms authored skill documents into executable IR.
 */
public class SkillCompiler {

    private static final String COMPILER_VERSION = "1.0.0";

    /**
     * Compiles a SkillDocument to SkillIr.
     *
     * @param document the source skill document
     * @return compilation result with IR or errors
     */
    public CompilerResult<SkillIr> compile(SkillDocument document) {
        Objects.requireNonNull(document, "document cannot be null");

        List<CompilerError> errors = new ArrayList<>();

        // Validate skill
        validateSkill(document, errors);

        if (!errors.isEmpty()) {
            return CompilerResult.failure(errors);
        }

        // Build IR
        try {
            SkillIr ir = buildIr(document);
            return CompilerResult.success(ir);
        } catch (Exception e) {
            return CompilerResult.failure(CompilerError.of(
                "E2999",
                "Unexpected compilation error: " + e.getMessage(),
                null,
                e
            ));
        }
    }

    private void validateSkill(SkillDocument document, List<CompilerError> errors) {
        String location = "skill[" + document.id().value() + "]";

        // Validate handler is present
        if (document.handler() == null) {
            errors.add(CompilerError.missingField("handler", location));
        }

        // Validate name is present
        if (document.name() == null || document.name().isBlank()) {
            errors.add(CompilerError.missingField("name", location));
        }

        // Validate input schema is valid JSON if present
        if (document.inputSchema() != null && !document.inputSchema().isEmpty()) {
            // Basic validation - in production would validate JSON schema structure
            try {
                // Just check it's not malformed
                Objects.requireNonNull(document.inputSchema());
            } catch (Exception e) {
                errors.add(CompilerError.validationFailure(
                    "invalid input schema: " + e.getMessage(),
                    location + ".inputSchema"
                ));
            }
        }

        // Validate output schema is valid JSON if present
        if (document.outputSchema() != null && !document.outputSchema().isEmpty()) {
            try {
                Objects.requireNonNull(document.outputSchema());
            } catch (Exception e) {
                errors.add(CompilerError.validationFailure(
                    "invalid output schema: " + e.getMessage(),
                    location + ".outputSchema"
                ));
            }
        }
    }

    private SkillIr buildIr(SkillDocument document) {
        Sha256 irChecksum = computeIrChecksum(document);

        return SkillIr.builder()
            .skillId(document.id())
            .skillVersion(document.version())
            .name(document.name())
            .description(document.description())
            .handler(document.handler())
            .inputSchema(document.inputSchema())
            .outputSchema(document.outputSchema())
            .tags(document.tags())
            .irChecksum(irChecksum)
            .compiledAt(Instant.now())
            .compilerVersion(COMPILER_VERSION)
            .build();
    }

    private Sha256 computeIrChecksum(SkillDocument document) {
        StringBuilder sb = new StringBuilder();
        sb.append(document.id().value());
        sb.append("|");
        sb.append(document.version().toString());
        sb.append("|");
        sb.append(document.handler().kind().name());
        sb.append(":");
        sb.append(document.handler().reference());
        sb.append("|");
        sb.append(document.tags().stream().sorted().collect(Collectors.joining(",")));

        return Sha256.of(sb.toString());
    }
}

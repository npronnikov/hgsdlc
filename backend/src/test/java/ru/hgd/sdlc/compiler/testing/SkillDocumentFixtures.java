package ru.hgd.sdlc.compiler.testing;

import ru.hgd.sdlc.compiler.domain.model.authored.HandlerRef;
import ru.hgd.sdlc.compiler.domain.model.authored.MarkdownBody;
import ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating test SkillDocument instances.
 * Provides pre-configured skill documents for various testing scenarios.
 */
public final class SkillDocumentFixtures implements TestFixture {

    private SkillDocumentFixtures() {
        // Utility class - no instantiation
    }

    /**
     * Creates a minimal valid skill.
     */
    public static SkillDocument simpleSkill() {
        return SkillDocument.builder()
            .id(SkillId.of("simple-skill"))
            .name("Simple Skill")
            .version(SemanticVersion.of("1.0.0"))
            .description(MarkdownBody.of("A simple skill for testing"))
            .handler(HandlerRef.builtin("execute"))
            .build();
    }

    /**
     * Creates a skill with input and output parameters.
     * Alias for skillWithParameters for backward compatibility.
     */
    public static SkillDocument skillWithSchemas() {
        return skillWithParameters();
    }

    /**
     * Creates a skill with input and output parameters.
     */
    public static SkillDocument skillWithParameters() {
        return SkillDocument.builder()
            .id(SkillId.of("parameterized-skill"))
            .name("Parameterized Skill")
            .version(SemanticVersion.of("2.0.0"))
            .description(MarkdownBody.of("A skill with input and output schemas"))
            .handler(HandlerRef.builtin("process-with-params"))
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "inputPath", Map.of(
                        "type", "string",
                        "description", "Path to input file"
                    ),
                    "options", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "verbose", Map.of("type", "boolean"),
                            "timeout", Map.of("type", "integer")
                        )
                    )
                ),
                "required", List.of("inputPath")
            ))
            .outputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "status", Map.of("type", "string"),
                    "outputPath", Map.of("type", "string"),
                    "metrics", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "duration", Map.of("type", "number"),
                            "itemsProcessed", Map.of("type", "integer")
                        )
                    )
                )
            ))
            .tags(List.of("processing", "batch"))
            .authoredAt(Instant.parse("2024-02-01T12:00:00Z"))
            .author("skill-author")
            .build();
    }

    /**
     * Creates a skill with template variables in the handler reference.
     */
    public static SkillDocument templatedSkill() {
        return SkillDocument.builder()
            .id(SkillId.of("templated-skill"))
            .name("Templated Skill")
            .version(SemanticVersion.of("1.5.0"))
            .description(MarkdownBody.of("""
                # Templated Skill

                This skill uses template variables for dynamic configuration.

                ## Configuration

                The skill accepts the following configuration:
                - `model`: The AI model to use
                - `temperature`: Sampling temperature
                """))
            .handler(HandlerRef.of("skill://ai-processor"))
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "model", Map.of(
                        "type", "string",
                        "default", "gpt-4"
                    ),
                    "temperature", Map.of(
                        "type", "number",
                        "minimum", 0,
                        "maximum", 2,
                        "default", 0.7
                    ),
                    "promptTemplate", Map.of(
                        "type", "string",
                        "description", "Template string with {{variable}} placeholders"
                    )
                )
            ))
            .outputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "result", Map.of("type", "string"),
                    "tokensUsed", Map.of("type", "integer")
                )
            ))
            .tags(List.of("ai", "templated", "dynamic"))
            .extensions(Map.of(
                "runtime", "python",
                "version", "3.11"
            ))
            .build();
    }

    /**
     * Creates a skill that references a script handler.
     */
    public static SkillDocument scriptSkill() {
        return SkillDocument.builder()
            .id(SkillId.of("script-skill"))
            .name("Script Skill")
            .version(SemanticVersion.of("1.0.0"))
            .description(MarkdownBody.of("A skill that executes a shell script"))
            .handler(HandlerRef.script("skills/automation.sh"))
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "command", Map.of("type", "string"),
                    "args", Map.of(
                        "type", "array",
                        "items", Map.of("type", "string")
                    )
                )
            ))
            .tags(List.of("script", "automation"))
            .build();
    }

    /**
     * Creates a skill with complex nested schemas.
     */
    public static SkillDocument complexSchemaSkill() {
        return SkillDocument.builder()
            .id(SkillId.of("complex-skill"))
            .name("Complex Schema Skill")
            .version(SemanticVersion.of("3.0.0"))
            .description(MarkdownBody.of("A skill with complex nested input/output schemas"))
            .handler(HandlerRef.of("skill://complex-processor"))
            .inputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "config", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "environment", Map.of(
                                "type", "string",
                                "enum", List.of("development", "staging", "production")
                            ),
                            "resources", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                    "cpu", Map.of("type", "string"),
                                    "memory", Map.of("type", "string")
                                )
                            )
                        )
                    ),
                    "items", Map.of(
                        "type", "array",
                        "items", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                "id", Map.of("type", "string"),
                                "data", Map.of("type", "object")
                            )
                        )
                    )
                ),
                "required", List.of("config", "items")
            ))
            .outputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                    "results", Map.of(
                        "type", "array",
                        "items", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                "id", Map.of("type", "string"),
                                "status", Map.of("type", "string"),
                                "output", Map.of("type", "object")
                            )
                        )
                    ),
                    "summary", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "total", Map.of("type", "integer"),
                            "successful", Map.of("type", "integer"),
                            "failed", Map.of("type", "integer")
                        )
                    )
                )
            ))
            .tags(List.of("batch", "complex"))
            .build();
    }

    /**
     * Creates a basic skill builder for custom skill construction.
     */
    public static SkillDocument.SkillDocumentBuilder builder() {
        return SkillDocument.builder()
            .id(SkillId.of("custom-skill"))
            .name("Custom Skill")
            .version(SemanticVersion.of("1.0.0"))
            .handler(HandlerRef.builtin("execute"));
    }

    /**
     * Creates an invalid skill without required fields.
     */
    public static SkillDocument invalidSkill() {
        return SkillDocument.builder()
            .id(SkillId.of("invalid-skill"))
            // Missing name - will fail validation
            .version(SemanticVersion.of("1.0.0"))
            // Missing handler - will fail validation
            .build();
    }
}

package ru.hgd.sdlc.compiler.domain.compiler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillCompiler")
class SkillCompilerTest {

    private SkillCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new SkillCompiler();
    }

    @Nested
    @DisplayName("valid input")
    class ValidInputTest {

        @Test
        @DisplayName("compiles minimal valid skill")
        void compilesMinimalValidSkill() {
            SkillDocument document = SkillDocument.builder()
                .id(SkillId.of("test-skill"))
                .name("Test Skill")
                .version(SemanticVersion.of("1.0.0"))
                .handler(HandlerRef.builtin("execute"))
                .build();

            CompilerResult<SkillIr> result = compiler.compile(document);

            assertTrue(result.isSuccess());
            assertNotNull(result.getIr());
            assertEquals("test-skill", result.getIr().skillId().value());
            assertEquals("Test Skill", result.getIr().name());
            assertEquals("1.0.0", result.getIr().skillVersion().toString());
        }

        @Test
        @DisplayName("compiles skill with all fields")
        void compilesSkillWithAllFields() {
            SkillDocument document = SkillDocument.builder()
                .id(SkillId.of("full-skill"))
                .name("Full Skill")
                .version(SemanticVersion.of("2.1.0"))
                .description(MarkdownBody.of("A complete skill definition"))
                .handler(HandlerRef.skill(SkillId.of("implementation")))
                .inputSchema(Map.of("type", "object", "properties", Map.of("input", Map.of("type", "string"))))
                .outputSchema(Map.of("type", "object", "properties", Map.of("output", Map.of("type", "string"))))
                .tags(List.of("utility", "testing"))
                .build();

            CompilerResult<SkillIr> result = compiler.compile(document);

            assertTrue(result.isSuccess());
            SkillIr ir = result.getIr();

            assertEquals("full-skill", ir.skillId().value());
            assertEquals("Full Skill", ir.name());
            assertEquals("2.1.0", ir.skillVersion().toString());
            assertTrue(ir.description().isPresent());
            assertEquals("utility", ir.tags().get(0));
            assertEquals("testing", ir.tags().get(1));
            assertFalse(ir.inputSchema().isEmpty());
            assertFalse(ir.outputSchema().isEmpty());
        }

        @Test
        @DisplayName("compiles skill with skill handler")
        void compilesSkillWithSkillHandler() {
            SkillDocument document = SkillDocument.builder()
                .id(SkillId.of("nested-skill"))
                .name("Nested Skill")
                .version(SemanticVersion.of("1.0.0"))
                .handler(HandlerRef.skill(SkillId.of("base-skill")))
                .build();

            CompilerResult<SkillIr> result = compiler.compile(document);

            assertTrue(result.isSuccess());
            assertNotNull(result.getIr().handler());
            assertEquals(HandlerKind.SKILL, result.getIr().handler().kind());
        }

        @Test
        @DisplayName("compiles skill with script handler")
        void compilesSkillWithScriptHandler() {
            SkillDocument document = SkillDocument.builder()
                .id(SkillId.of("script-skill"))
                .name("Script Skill")
                .version(SemanticVersion.of("1.0.0"))
                .handler(HandlerRef.script("scripts/execute.sh"))
                .build();

            CompilerResult<SkillIr> result = compiler.compile(document);

            assertTrue(result.isSuccess());
            assertEquals(HandlerKind.SCRIPT, result.getIr().handler().kind());
            assertEquals("scripts/execute.sh", result.getIr().handler().reference());
        }
    }

    @Nested
    @DisplayName("validation errors")
    class ValidationErrorTest {

        @Test
        @DisplayName("fails when name is blank")
        void failsWhenNameIsBlank() {
            SkillDocument document = SkillDocument.builder()
                .id(SkillId.of("test-skill"))
                .name("")
                .version(SemanticVersion.of("1.0.0"))
                .handler(HandlerRef.builtin("execute"))
                .build();

            CompilerResult<SkillIr> result = compiler.compile(document);

            assertTrue(result.isFailure());
            assertEquals("E2001", result.getFirstError().code());
            assertTrue(result.getFirstError().message().contains("name"));
        }
    }

    @Nested
    @DisplayName("IR structure")
    class IrStructureTest {

        @Test
        @DisplayName("IR contains correct metadata")
        void irContainsCorrectMetadata() {
            SkillDocument document = SkillDocument.builder()
                .id(SkillId.of("test-skill"))
                .name("Test Skill")
                .version(SemanticVersion.of("1.0.0"))
                .handler(HandlerRef.builtin("execute"))
                .build();

            CompilerResult<SkillIr> result = compiler.compile(document);

            assertTrue(result.isSuccess());
            SkillIr ir = result.getIr();

            assertNotNull(ir.compiledAt());
            assertNotNull(ir.compilerVersion());
            assertNotNull(ir.irChecksum());
        }

        @Test
        @DisplayName("IR implements CompiledIR interface")
        void irImplementsCompiledIRInterface() {
            SkillDocument document = SkillDocument.builder()
                .id(SkillId.of("test-skill"))
                .name("Test Skill")
                .version(SemanticVersion.of("1.0.0"))
                .handler(HandlerRef.builtin("execute"))
                .build();

            CompilerResult<SkillIr> result = compiler.compile(document);

            assertTrue(result.isSuccess());
            SkillIr ir = result.getIr();

            assertTrue(ir instanceof CompiledIR);
            assertEquals("test-skill", ir.irId());
            assertEquals("1.0.0", ir.sourceVersion());
            assertNotNull(ir.checksum());
        }

        @Test
        @DisplayName("IR has correct schema version")
        void irHasCorrectSchemaVersion() {
            SkillDocument document = SkillDocument.builder()
                .id(SkillId.of("test-skill"))
                .name("Test Skill")
                .version(SemanticVersion.of("1.0.0"))
                .handler(HandlerRef.builtin("execute"))
                .build();

            CompilerResult<SkillIr> result = compiler.compile(document);

            assertTrue(result.isSuccess());
            assertEquals(SkillIr.CURRENT_SCHEMA_VERSION, result.getIr().irSchemaVersion());
        }
    }

    @Nested
    @DisplayName("tags")
    class TagsTest {

        @Test
        @DisplayName("skill can check for tag presence")
        void skillCanCheckForTagPresence() {
            SkillDocument document = SkillDocument.builder()
                .id(SkillId.of("tagged-skill"))
                .name("Tagged Skill")
                .version(SemanticVersion.of("1.0.0"))
                .handler(HandlerRef.builtin("execute"))
                .tags(List.of("utility", "testing", "core"))
                .build();

            CompilerResult<SkillIr> result = compiler.compile(document);

            assertTrue(result.isSuccess());
            SkillIr ir = result.getIr();

            assertTrue(ir.hasTag("utility"));
            assertTrue(ir.hasTag("testing"));
            assertTrue(ir.hasTag("core"));
            assertFalse(ir.hasTag("nonexistent"));
        }
    }
}

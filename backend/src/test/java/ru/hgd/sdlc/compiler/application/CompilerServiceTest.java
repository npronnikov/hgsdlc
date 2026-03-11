package ru.hgd.sdlc.compiler.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.compiler.FlowCompiler;
import ru.hgd.sdlc.compiler.domain.compiler.SkillCompiler;
import ru.hgd.sdlc.compiler.domain.parser.FlowParser;
import ru.hgd.sdlc.compiler.domain.parser.FrontmatterExtractor;
import ru.hgd.sdlc.compiler.domain.parser.SkillParser;
import ru.hgd.sdlc.compiler.testing.MarkdownFixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CompilerServiceTest {

    private CompilerService compilerService;

    @BeforeEach
    void setUp() {
        FrontmatterExtractor extractor = new FrontmatterExtractor();
        FlowParser flowParser = new FlowParser();
        SkillParser skillParser = new SkillParser();

        ParseService parseService = new ParseService(extractor, flowParser, skillParser);
        ValidationService validationService = new ValidationService();
        SerializationService serializationService = new SerializationService();

        FlowCompiler flowCompiler = new FlowCompiler();
        SkillCompiler skillCompiler = new SkillCompiler();

        FlowCompilerService flowCompilerService = new FlowCompilerService(
            parseService, validationService, flowCompiler, serializationService
        );
        SkillCompilerService skillCompilerService = new SkillCompilerService(
            parseService, validationService, skillCompiler, serializationService
        );

        compilerService = new CompilerService(
            parseService, validationService, serializationService,
            flowCompilerService, skillCompilerService
        );
    }


    @Nested
    @DisplayName("Flow compilation")
    class FlowCompilation {

        @Test
        @DisplayName("should compile valid flow markdown")
        void shouldCompileValidFlowMarkdown() {
            String markdown = MarkdownFixtures.validFlowMarkdown();

            CompilerService.CompileFlowResult result = compilerService.compileFlow(markdown);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getIr()).isNotNull();
            assertThat(result.getIr().flowId().value()).isEqualTo("simple-flow");
        }

        @Test
        @DisplayName("should compile multi-phase flow")
        void shouldCompileMultiPhaseFlow() {
            String markdown = MarkdownFixtures.multiPhaseFlowMarkdown();

            CompilerService.CompileFlowResult result = compilerService.compileFlow(markdown);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getIr().flowId().value()).isEqualTo("multi-phase-flow");
        }

        @Test
        @DisplayName("should fail for flow with missing required fields")
        void shouldFailForFlowWithMissingRequiredFields() {
            String markdown = MarkdownFixtures.missingRequiredFieldsMarkdown();

            CompilerService.CompileFlowResult result = compilerService.compileFlow(markdown);

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getFailureStage()).isEqualTo("PARSE");
            assertThat(result.getParseErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("should compile flow from file")
        void shouldCompileFlowFromFile() throws IOException {
            Path tempFile = Files.createTempFile("test-flow", ".md");
            Files.writeString(tempFile, MarkdownFixtures.validFlowMarkdown());

            try {
                CompilerService.CompileFlowResult result = compilerService.compileFlow(tempFile);

                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getIr()).isNotNull();
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    @Nested
    @DisplayName("Skill compilation")
    class SkillCompilation {

        @Test
        @DisplayName("should compile valid skill markdown")
        void shouldCompileValidSkillMarkdown() {
            String markdown = MarkdownFixtures.validSkillMarkdown();

            CompilerService.CompileSkillResult result = compilerService.compileSkill(markdown);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getIr()).isNotNull();
            assertThat(result.getIr().skillId().value()).isEqualTo("code-generator");
        }

        @Test
        @DisplayName("should compile skill with parameters")
        void shouldCompileSkillWithParameters() {
            String markdown = MarkdownFixtures.skillWithParametersMarkdown();

            CompilerService.CompileSkillResult result = compilerService.compileSkill(markdown);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getIr().skillId().value()).isEqualTo("parameterized-skill");
        }

        @Test
        @DisplayName("should compile skill from file")
        void shouldCompileSkillFromFile() throws IOException {
            Path tempFile = Files.createTempFile("test-skill", ".md");
            Files.writeString(tempFile, MarkdownFixtures.validSkillMarkdown());

            try {
                CompilerService.CompileSkillResult result = compilerService.compileSkill(tempFile);

                assertThat(result.isSuccess()).isTrue();
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    @Nested
    @DisplayName("Auto-detect compilation")
    class AutoDetectCompilation {

        @Test
        @DisplayName("should auto-detect and compile flow")
        void shouldAutoDetectAndCompileFlow() {
            // Use multiPhaseFlowMarkdown as it has 'phases' field for type detection
            String markdown = MarkdownFixtures.multiPhaseFlowMarkdown();

            CompilerService.CompileResult result = compilerService.compile(markdown);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getType()).isEqualTo(CompilerService.DocumentType.FLOW);
            assertThat(result.getFlowIr()).isNotNull();
        }

        @Test
        @DisplayName("should auto-detect and compile skill")
        void shouldAutoDetectAndCompileSkill() {
            String markdown = MarkdownFixtures.validSkillMarkdown();

            CompilerService.CompileResult result = compilerService.compile(markdown);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getType()).isEqualTo(CompilerService.DocumentType.SKILL);
            assertThat(result.getSkillIr()).isNotNull();
        }

        @Test
        @DisplayName("should return error for unknown document type")
        void shouldReturnErrorForUnknownDocumentType() {
            String markdown = "# Plain Markdown\n\nNo frontmatter or type indicators.";

            CompilerService.CompileResult result = compilerService.compile(markdown);

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getFailureStage()).isEqualTo("UNKNOWN_TYPE");
            assertThat(result.getErrorMessage()).contains("Cannot determine document type");
        }
    }

    @Nested
    @DisplayName("Serialization")
    class Serialization {

        @Test
        @DisplayName("should serialize IR to JSON")
        void shouldSerializeIrToJson() throws Exception {
            String markdown = MarkdownFixtures.validFlowMarkdown();
            CompilerService.CompileFlowResult result = compilerService.compileFlow(markdown);

            assertThat(result.isSuccess()).isTrue();

            String json = compilerService.serialize(result.getIr());

            assertThat(json).isNotBlank();
            assertThat(json).contains("simple-flow");
        }
    }

    @Nested
    @DisplayName("Utility methods")
    class UtilityMethods {

        @Test
        @DisplayName("should detect flow document type with phases")
        void shouldDetectFlowDocumentTypeWithPhases() {
            String markdown = MarkdownFixtures.multiPhaseFlowMarkdown();

            var type = compilerService.detectDocumentType(markdown);

            assertThat(type).isPresent();
            assertThat(type.get()).isEqualTo(CompilerService.DocumentType.FLOW);
        }

        @Test
        @DisplayName("should detect skill document type")
        void shouldDetectSkillDocumentType() {
            String markdown = MarkdownFixtures.validSkillMarkdown();

            var type = compilerService.detectDocumentType(markdown);

            assertThat(type).isPresent();
            assertThat(type.get()).isEqualTo(CompilerService.DocumentType.SKILL);
        }

        @Test
        @DisplayName("should return empty for unknown document type")
        void shouldReturnEmptyForUnknownDocumentType() {
            String markdown = "# Plain Markdown\n\nNo frontmatter.";

            var type = compilerService.detectDocumentType(markdown);

            assertThat(type).isEmpty();
        }

        @Test
        @DisplayName("should check if content can be compiled")
        void shouldCheckIfContentCanBeCompiled() {
            assertThat(compilerService.canCompile(MarkdownFixtures.multiPhaseFlowMarkdown())).isTrue();
            assertThat(compilerService.canCompile(MarkdownFixtures.validSkillMarkdown())).isTrue();
            assertThat(compilerService.canCompile("# Plain text")).isFalse();
        }

        @Test
        @DisplayName("should get all error messages")
        void shouldGetAllErrorMessages() {
            String markdown = MarkdownFixtures.missingRequiredFieldsMarkdown();

            var errors = compilerService.getErrors(markdown);

            assertThat(errors).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Result types")
    class ResultTypes {

        @Test
        @DisplayName("should provide parse errors from flow result")
        void shouldProvideParseErrorsFromFlowResult() {
            String markdown = MarkdownFixtures.unclosedFrontmatterMarkdown();

            CompilerService.CompileFlowResult result = compilerService.compileFlow(markdown);

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getParseErrors()).isNotEmpty();
            assertThat(result.getAllErrorMessages()).isNotEmpty();
        }

        @Test
        @DisplayName("should provide validation errors from skill result")
        void shouldProvideValidationErrorsFromSkillResult() {
            // Create a skill with invalid handler (will fail validation)
            String invalidSkill = """
                ---
                id: test-skill
                name: Test Skill
                version: 1.0.0
                ---
                Test skill without handler.
                """;

            CompilerService.CompileSkillResult result = compilerService.compileSkill(invalidSkill);

            assertThat(result.isFailure()).isTrue();
        }
    }
}

package ru.hgd.sdlc.compiler.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;
import ru.hgd.sdlc.compiler.domain.parser.FlowParser;
import ru.hgd.sdlc.compiler.domain.parser.FrontmatterExtractor;
import ru.hgd.sdlc.compiler.domain.parser.ParseResult;
import ru.hgd.sdlc.compiler.domain.parser.SkillParser;
import ru.hgd.sdlc.compiler.testing.MarkdownFixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ParseServiceTest {

    private ParseService parseService;

    @BeforeEach
    void setUp() {
        FrontmatterExtractor extractor = new FrontmatterExtractor();
        FlowParser flowParser = new FlowParser();
        SkillParser skillParser = new SkillParser();
        parseService = new ParseService(extractor, flowParser, skillParser);
    }

    @Nested
    @DisplayName("Flow parsing")
    class FlowParsing {

        @Test
        @DisplayName("should parse valid flow markdown")
        void shouldParseValidFlowMarkdown() {
            String markdown = MarkdownFixtures.validFlowMarkdown();

            ParseResult<FlowDocument> result = parseService.parseFlowMarkdown(markdown);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDocument()).isNotNull();
            assertThat(result.getDocument().id().value()).isEqualTo("simple-flow");
            assertThat(result.getDocument().name()).isEqualTo("Simple Flow");
        }

        @Test
        @DisplayName("should parse flow with missing required fields")
        void shouldParseFlowWithMissingRequiredFields() {
            String markdown = MarkdownFixtures.missingRequiredFieldsMarkdown();

            ParseResult<FlowDocument> result = parseService.parseFlowMarkdown(markdown);

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("should parse flow with unclosed frontmatter")
        void shouldParseFlowWithUnclosedFrontmatter() {
            String markdown = MarkdownFixtures.unclosedFrontmatterMarkdown();

            ParseResult<FlowDocument> result = parseService.parseFlowMarkdown(markdown);

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getFirstError().code()).isEqualTo("E1003");
        }

        @Test
        @DisplayName("should parse flow with malformed YAML")
        void shouldParseFlowWithMalformedYaml() {
            String markdown = MarkdownFixtures.invalidFrontmatterMarkdown();

            ParseResult<FlowDocument> result = parseService.parseFlowMarkdown(markdown);

            assertThat(result.isFailure()).isTrue();
            assertThat(result.getFirstError().code()).isEqualTo("E1002");
        }

        @Test
        @DisplayName("should parse multi-phase flow")
        void shouldParseMultiPhaseFlow() {
            String markdown = MarkdownFixtures.multiPhaseFlowMarkdown();

            ParseResult<FlowDocument> result = parseService.parseFlowMarkdown(markdown);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDocument().id().value()).isEqualTo("multi-phase-flow");
        }
    }

    @Nested
    @DisplayName("Skill parsing")
    class SkillParsing {

        @Test
        @DisplayName("should parse valid skill markdown")
        void shouldParseValidSkillMarkdown() {
            String markdown = MarkdownFixtures.validSkillMarkdown();

            ParseResult<SkillDocument> result = parseService.parseSkillMarkdown(markdown);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDocument()).isNotNull();
            assertThat(result.getDocument().id().value()).isEqualTo("code-generator");
            assertThat(result.getDocument().name()).isEqualTo("Code Generator");
        }

        @Test
        @DisplayName("should parse skill with parameters")
        void shouldParseSkillWithParameters() {
            String markdown = MarkdownFixtures.skillWithParametersMarkdown();

            ParseResult<SkillDocument> result = parseService.parseSkillMarkdown(markdown);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDocument().id().value()).isEqualTo("parameterized-skill");
            assertThat(result.getDocument().name()).isEqualTo("Parameterized Skill");
        }
    }

    @Nested
    @DisplayName("File parsing")
    class FileParsing {

        @Test
        @DisplayName("should parse flow from file")
        void shouldParseFlowFromFile() throws IOException {
            Path tempFile = Files.createTempFile("test-flow", ".md");
            Files.writeString(tempFile, MarkdownFixtures.validFlowMarkdown());

            try {
                ParseResult<FlowDocument> result = parseService.parseFlowMarkdownFile(tempFile);

                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getDocument().id().value()).isEqualTo("simple-flow");
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        @Test
        @DisplayName("should parse skill from file")
        void shouldParseSkillFromFile() throws IOException {
            Path tempFile = Files.createTempFile("test-skill", ".md");
            Files.writeString(tempFile, MarkdownFixtures.validSkillMarkdown());

            try {
                ParseResult<SkillDocument> result = parseService.parseSkillMarkdownFile(tempFile);

                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getDocument().id().value()).isEqualTo("code-generator");
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    @Nested
    @DisplayName("Document type detection")
    class DocumentTypeDetection {

        @Test
        @DisplayName("should detect flow document with phases")
        void shouldDetectFlowDocumentWithPhases() {
            var extractResult = parseService.extractFrontmatter(MarkdownFixtures.multiPhaseFlowMarkdown());

            assertThat(extractResult.isSuccess()).isTrue();
            assertThat(parseService.isFlowDocument(extractResult.getValue())).isTrue();
        }

        @Test
        @DisplayName("should detect skill document")
        void shouldDetectSkillDocument() {
            var extractResult = parseService.extractFrontmatter(MarkdownFixtures.validSkillMarkdown());

            assertThat(extractResult.isSuccess()).isTrue();
            assertThat(parseService.isSkillDocument(extractResult.getValue())).isTrue();
        }
    }

    @Nested
    @DisplayName("Error formatting")
    class ErrorFormatting {

        @Test
        @DisplayName("should format errors with code and location")
        void shouldFormatErrorsWithCodeAndLocation() {
            ParseResult<FlowDocument> result = parseService.parseFlowMarkdown(
                MarkdownFixtures.missingRequiredFieldsMarkdown()
            );

            assertThat(result.isFailure()).isTrue();
            var formattedErrors = parseService.formatErrors(result.getErrors());

            assertThat(formattedErrors).isNotEmpty();
            assertThat(formattedErrors.get(0)).contains("E1004");
        }
    }
}

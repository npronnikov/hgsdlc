package ru.hgd.sdlc.compiler.domain.writer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.compiler.domain.parser.FrontmatterExtractor;
import ru.hgd.sdlc.compiler.domain.parser.ParseError;
import ru.hgd.sdlc.compiler.domain.parser.ParseResult;
import ru.hgd.sdlc.compiler.domain.parser.ParsedMarkdown;
import ru.hgd.sdlc.compiler.domain.parser.FlowParser;
import ru.hgd.sdlc.compiler.domain.parser.SkillParser;
import ru.hgd.sdlc.shared.kernel.Result;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CanonicalMarkdownWriter")
class CanonicalMarkdownWriterTest {

    private CanonicalMarkdownWriter writer;
    private FrontmatterExtractor extractor;
    private FlowParser flowParser;
    private SkillParser skillParser;

    @BeforeEach
    void setUp() {
        writer = new CanonicalMarkdownWriter();
        extractor = new FrontmatterExtractor();
        flowParser = new FlowParser();
        skillParser = new SkillParser();
    }

    @Nested
    @DisplayName("write FlowDocument")
    class WriteFlowDocumentTest {

        @Test
        @DisplayName("writes minimal flow")
        void writesMinimalFlow() {
            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("minimal-flow"))
                .version(SemanticVersion.of(1, 0, 0))
                .build();

            String result = writer.write(flow);

            assertTrue(result.startsWith("---\n"));
            assertTrue(result.contains("id: minimal-flow"));
            assertTrue(result.contains("version: 1.0.0"));
            assertTrue(result.indexOf("---\n", 4) > 0); // Closing delimiter
        }

        @Test
        @DisplayName("writes complete flow")
        void writesCompleteFlow() {
            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("full-flow"))
                .name("Full Flow")
                .version(SemanticVersion.of(2, 1, 0))
                .description(MarkdownBody.of("# Description\n\nThis is a test flow."))
                .phaseOrder(List.of(PhaseId.of("setup"), PhaseId.of("develop")))
                .startRoles(Set.of(Role.of("developer"), Role.of("architect")))
                .resumePolicy(ResumePolicy.FROM_CHECKPOINT)
                .author("John Doe")
                .build();

            String result = writer.write(flow);

            assertTrue(result.contains("id: full-flow"));
            assertTrue(result.contains("name: Full Flow"));
            assertTrue(result.contains("version: 2.1.0"));
            assertTrue(result.contains("phase_order: [develop, setup]")); // Sorted alphabetically
            assertTrue(result.contains("start_roles: [architect, developer]")); // Sorted
            assertTrue(result.contains("# Description"));
            assertTrue(result.contains("author: John Doe"));
        }

        @Test
        @DisplayName("writes flow with custom resume policy")
        void writesFlowWithCustomResumePolicy() {
            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test"))
                .version(SemanticVersion.of(1, 0, 0))
                .resumePolicy(ResumePolicy.RESTART_PHASE)
                .build();

            String result = writer.write(flow);

            assertTrue(result.contains("resume_policy: restart-phase"));
        }

        @Test
        @DisplayName("writes flow with authored timestamp")
        void writesFlowWithAuthoredTimestamp() {
            Instant timestamp = Instant.parse("2024-01-15T10:30:00Z");
            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test"))
                .version(SemanticVersion.of(1, 0, 0))
                .authoredAt(timestamp)
                .build();

            String result = writer.write(flow);

            assertTrue(result.contains("authored_at: 2024-01-15T10:30:00Z"));
        }

        @Test
        @DisplayName("throws NullPointerException for null document")
        void throwsNullPointerExceptionForNullDocument() {
            assertThrows(NullPointerException.class, () -> writer.write((FlowDocument) null));
        }
    }

    @Nested
    @DisplayName("write SkillDocument")
    class WriteSkillDocumentTest {

        @Test
        @DisplayName("writes minimal skill")
        void writesMinimalSkill() {
            SkillDocument skill = SkillDocument.builder()
                .id(SkillId.of("generate-code"))
                .name("Code Generator")
                .version(SemanticVersion.of(1, 0, 0))
                .handler(HandlerRef.skill(SkillId.of("qwen-coder")))
                .build();

            String result = writer.write(skill);

            assertTrue(result.startsWith("---\n"));
            assertTrue(result.contains("id: generate-code"));
            assertTrue(result.contains("name: Code Generator"));
            assertTrue(result.contains("version: 1.0.0"));
            assertTrue(result.contains("handler: skill://qwen-coder"));
        }

        @Test
        @DisplayName("writes skill with all fields")
        void writesSkillWithAllFields() {
            SkillDocument skill = SkillDocument.builder()
                .id(SkillId.of("review-code"))
                .name("Code Reviewer")
                .version(SemanticVersion.of(2, 0, 0))
                .description(MarkdownBody.of("# Code Review\n\nReviews code quality."))
                .handler(HandlerRef.builtin("review"))
                .inputSchema(java.util.Map.of("type", "object"))
                .outputSchema(java.util.Map.of("type", "object"))
                .tags(List.of("review", "quality"))
                .author("Jane Doe")
                .build();

            String result = writer.write(skill);

            assertTrue(result.contains("id: review-code"));
            assertTrue(result.contains("name: Code Reviewer"));
            assertTrue(result.contains("handler: builtin://review"));
            assertTrue(result.contains("input_schema:"));
            assertTrue(result.contains("output_schema:"));
            assertTrue(result.contains("tags: [quality, review]")); // Sorted
            assertTrue(result.contains("# Code Review"));
        }

        @Test
        @DisplayName("writes skill with script handler")
        void writesSkillWithScriptHandler() {
            SkillDocument skill = SkillDocument.builder()
                .id(SkillId.of("custom"))
                .name("Custom Script")
                .version(SemanticVersion.of(1, 0, 0))
                .handler(HandlerRef.script("scripts/custom.sh"))
                .build();

            String result = writer.write(skill);

            assertTrue(result.contains("handler: script://scripts/custom.sh"));
        }

        @Test
        @DisplayName("throws NullPointerException for null document")
        void throwsNullPointerExceptionForNullDocument() {
            assertThrows(NullPointerException.class, () -> writer.write((SkillDocument) null));
        }
    }

    @Nested
    @DisplayName("round-trip")
    class RoundTripTest {

        @Test
        @DisplayName("round-trips minimal flow")
        void roundTripsMinimalFlow() {
            FlowDocument original = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of(1, 0, 0))
                .build();

            String markdown = writer.write(original);

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<FlowDocument> parsed = flowParser.parse(extracted.getValue());
            assertTrue(parsed.isSuccess());

            FlowDocument result = parsed.getDocument();
            assertEquals(original.id(), result.id());
            assertEquals(original.version(), result.version());
        }

        @Test
        @DisplayName("round-trips flow with description")
        void roundTripsFlowWithDescription() {
            FlowDocument original = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of(1, 0, 0))
                .description(MarkdownBody.of("# Title\n\nBody content."))
                .build();

            String markdown = writer.write(original);

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<FlowDocument> parsed = flowParser.parse(extracted.getValue());
            assertTrue(parsed.isSuccess());

            FlowDocument result = parsed.getDocument();
            assertEquals(original.id(), result.id());
            assertEquals(original.version(), result.version());
            assertTrue(result.description().content().contains("# Title"));
        }

        @Test
        @DisplayName("round-trips flow with roles and phases")
        void roundTripsFlowWithRolesAndPhases() {
            FlowDocument original = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of(1, 0, 0))
                .phaseOrder(List.of(PhaseId.of("setup"), PhaseId.of("develop")))
                .startRoles(Set.of(Role.of("developer"), Role.of("architect")))
                .build();

            String markdown = writer.write(original);

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<FlowDocument> parsed = flowParser.parse(extracted.getValue());
            assertTrue(parsed.isSuccess());

            FlowDocument result = parsed.getDocument();
            assertEquals(2, result.phaseOrder().size());
            assertTrue(result.phaseOrder().contains(PhaseId.of("setup")));
            assertTrue(result.phaseOrder().contains(PhaseId.of("develop")));
            assertEquals(2, result.startRoles().size());
            assertTrue(result.startRoles().contains(Role.of("developer")));
            assertTrue(result.startRoles().contains(Role.of("architect")));
        }

        @Test
        @DisplayName("round-trips minimal skill")
        void roundTripsMinimalSkill() {
            SkillDocument original = SkillDocument.builder()
                .id(SkillId.of("test-skill"))
                .name("Test Skill")
                .version(SemanticVersion.of(1, 0, 0))
                .handler(HandlerRef.skill(SkillId.of("handler-id")))
                .build();

            String markdown = writer.write(original);

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<SkillDocument> parsed = skillParser.parse(extracted.getValue());
            assertTrue(parsed.isSuccess());

            SkillDocument result = parsed.getDocument();
            assertEquals(original.id(), result.id());
            assertEquals(original.name(), result.name());
            assertEquals(original.version(), result.version());
            assertEquals(original.handler().toString(), result.handler().toString());
        }

        @Test
        @DisplayName("round-trips skill with all fields")
        void roundTripsSkillWithAllFields() {
            SkillDocument original = SkillDocument.builder()
                .id(SkillId.of("test-skill"))
                .name("Test Skill")
                .version(SemanticVersion.of(2, 0, 0))
                .handler(HandlerRef.builtin("test"))
                .tags(List.of("tag1", "tag2"))
                .author("Test Author")
                .build();

            String markdown = writer.write(original);

            Result<ParsedMarkdown, ParseError> extracted = extractor.extract(markdown);
            assertTrue(extracted.isSuccess());

            ParseResult<SkillDocument> parsed = skillParser.parse(extracted.getValue());
            assertTrue(parsed.isSuccess());

            SkillDocument result = parsed.getDocument();
            assertEquals(original.id(), result.id());
            assertEquals(original.name(), result.name());
            assertEquals(original.version(), result.version());
            assertEquals(original.handler().toString(), result.handler().toString());
            assertEquals(2, result.tags().size());
            assertEquals("Test Author", result.author());
        }
    }

    @Nested
    @DisplayName("deterministic output")
    class DeterministicOutputTest {

        @Test
        @DisplayName("produces identical output for identical flow documents")
        void producesIdenticalOutputForIdenticalFlowDocuments() {
            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test"))
                .version(SemanticVersion.of(1, 0, 0))
                .startRoles(Set.of(Role.of("a"), Role.of("b"), Role.of("c")))
                .build();

            String result1 = writer.write(flow);
            String result2 = writer.write(flow);

            assertEquals(result1, result2);
        }

        @Test
        @DisplayName("produces identical output for identical skill documents")
        void producesIdenticalOutputForIdenticalSkillDocuments() {
            SkillDocument skill = SkillDocument.builder()
                .id(SkillId.of("test"))
                .name("Test")
                .version(SemanticVersion.of(1, 0, 0))
                .handler(HandlerRef.builtin("test"))
                .tags(List.of("a", "b", "c"))
                .build();

            String result1 = writer.write(skill);
            String result2 = writer.write(skill);

            assertEquals(result1, result2);
        }

        @Test
        @DisplayName("orders roles alphabetically")
        void ordersRolesAlphabetically() {
            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test"))
                .version(SemanticVersion.of(1, 0, 0))
                .startRoles(Set.of(Role.of("zebra"), Role.of("alpha"), Role.of("middle")))
                .build();

            String result = writer.write(flow);

            // Roles should be sorted
            assertTrue(result.contains("start_roles: [alpha, middle, zebra]"));
        }

        @Test
        @DisplayName("orders tags alphabetically")
        void ordersTagsAlphabetically() {
            SkillDocument skill = SkillDocument.builder()
                .id(SkillId.of("test"))
                .name("Test")
                .version(SemanticVersion.of(1, 0, 0))
                .handler(HandlerRef.builtin("test"))
                .tags(List.of("zebra", "alpha", "middle"))
                .build();

            String result = writer.write(skill);

            assertTrue(result.contains("tags: [alpha, middle, zebra]"));
        }
    }
}

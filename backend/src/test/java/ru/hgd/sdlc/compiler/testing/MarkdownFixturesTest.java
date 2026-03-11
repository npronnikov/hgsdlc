package ru.hgd.sdlc.compiler.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MarkdownFixtures.
 */
class MarkdownFixturesTest {

    @Nested
    @DisplayName("Valid markdown fixtures")
    class ValidMarkdownTests {

        @Test
        @DisplayName("validFlowMarkdown should have frontmatter")
        void validFlowMarkdownShouldHaveFrontmatter() {
            String markdown = MarkdownFixtures.validFlowMarkdown();

            assertTrue(markdown.startsWith("---"));
            assertTrue(markdown.contains("id: simple-flow"));
            assertTrue(markdown.contains("name: Simple Flow"));
            assertTrue(markdown.contains("version: 1.0.0"));
        }

        @Test
        @DisplayName("multiPhaseFlowMarkdown should have multiple phases")
        void multiPhaseFlowMarkdownShouldHaveMultiplePhases() {
            String markdown = MarkdownFixtures.multiPhaseFlowMarkdown();

            assertTrue(markdown.contains("development"));
            assertTrue(markdown.contains("review"));
            assertTrue(markdown.contains("deployment"));
        }

        @Test
        @DisplayName("validSkillMarkdown should have skill-specific fields")
        void validSkillMarkdownShouldHaveSkillSpecificFields() {
            String markdown = MarkdownFixtures.validSkillMarkdown();

            assertTrue(markdown.contains("id: code-generator"));
            assertTrue(markdown.contains("handler: skill://code-generator"));
            assertTrue(markdown.contains("inputSchema:"));
        }

        @Test
        @DisplayName("skillWithParametersMarkdown should have detailed schemas")
        void skillWithParametersMarkdownShouldHaveDetailedSchemas() {
            String markdown = MarkdownFixtures.skillWithParametersMarkdown();

            assertTrue(markdown.contains("inputSchema:"));
            assertTrue(markdown.contains("outputSchema:"));
            assertTrue(markdown.contains("required:"));
        }
    }

    @Nested
    @DisplayName("Invalid markdown fixtures")
    class InvalidMarkdownTests {

        @Test
        @DisplayName("invalidFrontmatterMarkdown should have malformed YAML")
        void invalidFrontmatterMarkdownShouldHaveMalformedYaml() {
            String markdown = MarkdownFixtures.invalidFrontmatterMarkdown();

            assertTrue(markdown.contains("[invalid version format]"));
            assertTrue(markdown.contains("[unclosed array"));
        }

        @Test
        @DisplayName("missingRequiredFieldsMarkdown should not have id field")
        void missingRequiredFieldsMarkdownShouldNotHaveIdField() {
            String markdown = MarkdownFixtures.missingRequiredFieldsMarkdown();

            assertFalse(markdown.contains("id:"));
            assertTrue(markdown.contains("name: Flow Without ID"));
        }

        @Test
        @DisplayName("unclosedFrontmatterMarkdown should not have closing delimiter")
        void unclosedFrontmatterMarkdownShouldNotHaveClosingDelimiter() {
            String markdown = MarkdownFixtures.unclosedFrontmatterMarkdown();

            // Should have opening but not proper closing
            assertTrue(markdown.startsWith("---"));
            // Count occurrences of "---"
            long delimiterCount = markdown.lines()
                .filter(line -> line.trim().equals("---"))
                .count();
            assertEquals(1, delimiterCount, "Should have only one frontmatter delimiter");
        }

        @Test
        @DisplayName("noFrontmatterMarkdown should be plain markdown")
        void noFrontmatterMarkdownShouldBePlainMarkdown() {
            String markdown = MarkdownFixtures.noFrontmatterMarkdown();

            assertFalse(markdown.startsWith("---"));
            assertTrue(markdown.contains("# Plain Markdown"));
        }
    }

    @Nested
    @DisplayName("Edge case fixtures")
    class EdgeCaseTests {

        @Test
        @DisplayName("emptyMarkdown should be empty")
        void emptyMarkdownShouldBeEmpty() {
            String markdown = MarkdownFixtures.emptyMarkdown();

            assertTrue(markdown.isEmpty());
        }

        @Test
        @DisplayName("whitespaceOnlyMarkdown should contain only whitespace")
        void whitespaceOnlyMarkdownShouldContainOnlyWhitespace() {
            String markdown = MarkdownFixtures.whitespaceOnlyMarkdown();

            assertTrue(markdown.isBlank());
        }

        @Test
        @DisplayName("unknownFieldsMarkdown should have non-standard fields")
        void unknownFieldsMarkdownShouldHaveNonStandardFields() {
            String markdown = MarkdownFixtures.unknownFieldsMarkdown();

            assertTrue(markdown.contains("unknownField:"));
            assertTrue(markdown.contains("anotherUnknown:"));
        }
    }

    @Nested
    @DisplayName("Complex fixtures")
    class ComplexFixtureTests {

        @Test
        @DisplayName("flowWithGatesMarkdown should have gate configuration")
        void flowWithGatesMarkdownShouldHaveGateConfiguration() {
            String markdown = MarkdownFixtures.flowWithGatesMarkdown();

            assertTrue(markdown.contains("gates:"));
            assertTrue(markdown.contains("type: APPROVAL"));
            assertTrue(markdown.contains("requiredApprovers:"));
        }

        @Test
        @DisplayName("complexFlowMarkdown should have all features")
        void complexFlowMarkdownShouldHaveAllFeatures() {
            String markdown = MarkdownFixtures.complexFlowMarkdown();

            assertTrue(markdown.contains("resumePolicy: FROM_CHECKPOINT"));
            assertTrue(markdown.contains("artifacts:"));
            assertTrue(markdown.contains("phases:"));
            assertTrue(markdown.contains("Deployment"));
        }
    }
}

package ru.hgd.sdlc.compiler.domain.validation.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.compiler.domain.validation.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FrontmatterValidator")
class FrontmatterValidatorTest {

    private FrontmatterValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FrontmatterValidator();
    }

    @Nested
    @DisplayName("flow document validation")
    class FlowDocumentTest {

        @Test
        @DisplayName("validates valid flow document")
        void validatesValidFlowDocument() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("valid-flow"))
                .name("Valid Flow")
                .version(SemanticVersion.of("1.0.0"))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("reports error for non-kebab-case flow ID")
        void reportsErrorForNonKebabCaseFlowId() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("InvalidFlow"))
                .name("Test")
                .version(SemanticVersion.of("1.0.0"))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.INVALID_ID_FORMAT)));
        }

        @Test
        @DisplayName("reports warning for missing name")
        void reportsWarningForMissingName() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("test-flow"))
                .version(SemanticVersion.of("1.0.0"))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.isValid()); // Warning doesn't make it invalid
            assertTrue(result.warnings().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.SUGGESTED_FIELD_MISSING)));
        }

        @Test
        @DisplayName("accepts valid kebab-case IDs")
        void acceptsValidKebabCaseIds() {
            ValidationContext context = ValidationContext.forFile("test-flow.md");

            FlowDocument flow = FlowDocument.builder()
                .id(FlowId.of("my-valid-flow-123"))
                .name("Test")
                .version(SemanticVersion.of("1.0.0"))
                .build();

            ValidationResult result = validator.validate(flow, context);

            assertTrue(result.isValid(), "Expected valid result but got: " + result.allIssues());
        }
    }

    @Nested
    @DisplayName("skill document validation")
    class SkillDocumentTest {

        @Test
        @DisplayName("validates valid skill document")
        void validatesValidSkillDocument() {
            ValidationContext context = ValidationContext.forFile("test-skill.md");

            SkillDocument skill = SkillDocument.builder()
                .id(SkillId.of("valid-skill"))
                .name("Valid Skill")
                .version(SemanticVersion.of("1.0.0"))
                .handler(HandlerRef.builtin("test"))
                .build();

            ValidationResult result = validator.validate(skill, context);

            assertTrue(result.isValid(), "Expected valid result but got: " + result.allIssues());
        }

        @Test
        @DisplayName("reports error for non-kebab-case skill ID")
        void reportsErrorForNonKebabCaseSkillId() {
            ValidationContext context = ValidationContext.forFile("test-skill.md");

            SkillDocument skill = SkillDocument.builder()
                .id(SkillId.of("InvalidSkill"))
                .name("Test Skill")
                .version(SemanticVersion.of("1.0.0"))
                .handler(HandlerRef.builtin("test"))
                .build();

            ValidationResult result = validator.validate(skill, context);

            assertFalse(result.isValid());
            assertTrue(result.errors().stream()
                .anyMatch(e -> e.code().equals(ValidationCodes.INVALID_ID_FORMAT)));
        }
    }

    @Nested
    @DisplayName("ID format validation")
    class IdFormatTest {

        @Test
        @DisplayName("accepts lowercase kebab-case")
        void acceptsLowercaseKebabCase() {
            ValidationContext context = ValidationContext.forFile("test.md");
            validator.validateIdFormat("my-valid-id", "test", context);

            assertFalse(context.hasErrors());
        }

        @Test
        @DisplayName("rejects uppercase letters")
        void rejectsUppercaseLetters() {
            ValidationContext context = ValidationContext.forFile("test.md");
            validator.validateIdFormat("My-Invalid-Id", "test", context);

            assertTrue(context.hasErrors());
        }

        @Test
        @DisplayName("rejects starting with number")
        void rejectsStartingWithNumber() {
            ValidationContext context = ValidationContext.forFile("test.md");
            validator.validateIdFormat("123-invalid", "test", context);

            assertTrue(context.hasErrors());
        }

        @Test
        @DisplayName("rejects starting with hyphen")
        void rejectsStartingWithHyphen() {
            ValidationContext context = ValidationContext.forFile("test.md");
            validator.validateIdFormat("-invalid", "test", context);

            assertTrue(context.hasErrors());
        }

        @Test
        @DisplayName("rejects consecutive hyphens")
        void rejectsConsecutiveHyphens() {
            ValidationContext context = ValidationContext.forFile("test.md");
            validator.validateIdFormat("my--invalid", "test", context);

            assertTrue(context.hasErrors());
        }
    }

    @Nested
    @DisplayName("type validation")
    class TypeValidationTest {

        @Test
        @DisplayName("accepts valid types")
        void acceptsValidTypes() {
            assertTrue(FrontmatterValidator.isValidType("flow"));
            assertTrue(FrontmatterValidator.isValidType("skill"));
            assertTrue(FrontmatterValidator.isValidType("FLOW"));
            assertTrue(FrontmatterValidator.isValidType("SKILL"));
        }

        @Test
        @DisplayName("rejects invalid types")
        void rejectsInvalidTypes() {
            assertFalse(FrontmatterValidator.isValidType("workflow"));
            assertFalse(FrontmatterValidator.isValidType(null));
            assertFalse(FrontmatterValidator.isValidType(""));
        }
    }
}

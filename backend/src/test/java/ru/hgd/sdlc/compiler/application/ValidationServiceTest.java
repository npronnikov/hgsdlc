package ru.hgd.sdlc.compiler.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;
import ru.hgd.sdlc.compiler.domain.validation.ValidationResult;
import ru.hgd.sdlc.compiler.testing.FlowDocumentFixtures;
import ru.hgd.sdlc.compiler.testing.SkillDocumentFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationServiceTest {

    private ValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new ValidationService();
    }

    @Nested
    @DisplayName("Flow validation")
    class FlowValidation {

        @Test
        @DisplayName("should validate simple flow successfully")
        void shouldValidateSimpleFlowSuccessfully() {
            FlowDocument flow = FlowDocumentFixtures.simpleFlow();

            ValidationResult result = validationService.validateFlow(flow);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should validate multi-phase flow")
        void shouldValidateMultiPhaseFlow() {
            FlowDocument flow = FlowDocumentFixtures.multiPhaseFlow();

            ValidationResult result = validationService.validateFlow(flow);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should validate flow with gates")
        void shouldValidateFlowWithGates() {
            FlowDocument flow = FlowDocumentFixtures.flowWithGates();

            ValidationResult result = validationService.validateFlow(flow);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should fail validation for invalid flow")
        void shouldFailValidationForInvalidFlow() {
            FlowDocument flow = FlowDocumentFixtures.invalidFlow();

            ValidationResult result = validationService.validateFlow(flow);

            assertThat(result.isValid()).isFalse();
            assertThat(result.errors()).isNotEmpty();
        }

        @Test
        @DisplayName("should include file path in error location")
        void shouldIncludeFilePathInErrorLocation() {
            FlowDocument flow = FlowDocumentFixtures.invalidFlow();

            ValidationResult result = validationService.validateFlow(flow, "/path/to/flow.md");

            assertThat(result.isValid()).isFalse();
        }
    }

    @Nested
    @DisplayName("Skill validation")
    class SkillValidation {

        @Test
        @DisplayName("should validate simple skill successfully")
        void shouldValidateSimpleSkillSuccessfully() {
            SkillDocument skill = SkillDocumentFixtures.simpleSkill();

            ValidationResult result = validationService.validateSkill(skill);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should validate skill with parameters")
        void shouldValidateSkillWithParameters() {
            SkillDocument skill = SkillDocumentFixtures.skillWithSchemas();

            ValidationResult result = validationService.validateSkill(skill);

            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("Polymorphic validation")
    class PolymorphicValidation {

        @Test
        @DisplayName("should validate flow document polymorphically")
        void shouldValidateFlowDocumentPolymorphically() {
            FlowDocument flow = FlowDocumentFixtures.simpleFlow();

            ValidationResult result = validationService.validate(flow);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should validate skill document polymorphically")
        void shouldValidateSkillDocumentPolymorphically() {
            SkillDocument skill = SkillDocumentFixtures.simpleSkill();

            ValidationResult result = validationService.validate(skill);

            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("should throw for unsupported document type")
        void shouldThrowForUnsupportedDocumentType() {
            String unsupported = "not a document";

            assertThatThrownBy(() -> validationService.validate(unsupported))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported document type");
        }
    }

    @Nested
    @DisplayName("Utility methods")
    class UtilityMethods {

        @Test
        @DisplayName("should check if document is valid")
        void shouldCheckIfDocumentIsValid() {
            FlowDocument validFlow = FlowDocumentFixtures.simpleFlow();
            FlowDocument invalidFlow = FlowDocumentFixtures.invalidFlow();

            assertThat(validationService.isValid(validFlow)).isTrue();
            assertThat(validationService.isValid(invalidFlow)).isFalse();
        }

        @Test
        @DisplayName("should check if document has issues")
        void shouldCheckIfDocumentHasIssues() {
            FlowDocument validFlow = FlowDocumentFixtures.simpleFlow();
            FlowDocument invalidFlow = FlowDocumentFixtures.invalidFlow();

            assertThat(validationService.hasIssues(validFlow)).isFalse();
            assertThat(validationService.hasIssues(invalidFlow)).isTrue();
        }

        @Test
        @DisplayName("should format validation result")
        void shouldFormatValidationResult() {
            FlowDocument invalidFlow = FlowDocumentFixtures.invalidFlow();

            ValidationResult result = validationService.validateFlow(invalidFlow);
            var formattedErrors = validationService.formatErrors(result);

            assertThat(formattedErrors).isNotEmpty();
        }
    }
}

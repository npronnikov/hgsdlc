package ru.hgd.sdlc.compiler.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.validation.CompositeValidator;
import ru.hgd.sdlc.compiler.domain.validation.rules.*;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatorConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ValidatorConfiguration.class);

    @Nested
    @DisplayName("Individual validator beans")
    class IndividualValidatorBeans {

        @Test
        @DisplayName("should create FrontmatterValidator bean")
        void shouldCreateFrontmatterValidatorBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(FrontmatterValidator.class);
                assertThat(context.getBean(FrontmatterValidator.class))
                        .isNotNull();
            });
        }

        @Test
        @DisplayName("should create PhaseValidator bean")
        void shouldCreatePhaseValidatorBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(PhaseValidator.class);
                assertThat(context.getBean(PhaseValidator.class))
                        .isNotNull();
            });
        }

        @Test
        @DisplayName("should create StepValidator bean")
        void shouldCreateStepValidatorBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(StepValidator.class);
                assertThat(context.getBean(StepValidator.class))
                        .isNotNull();
            });
        }

        @Test
        @DisplayName("should create CrossReferenceValidator bean")
        void shouldCreateCrossReferenceValidatorBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(CrossReferenceValidator.class);
                assertThat(context.getBean(CrossReferenceValidator.class))
                        .isNotNull();
            });
        }

        @Test
        @DisplayName("should create SemanticValidator bean")
        void shouldCreateSemanticValidatorBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(SemanticValidator.class);
                assertThat(context.getBean(SemanticValidator.class))
                        .isNotNull();
            });
        }
    }

    @Nested
    @DisplayName("Composite validator")
    class CompositeValidatorBean {

        @Test
        @DisplayName("should create CompositeValidator for FlowDocument")
        void shouldCreateFlowValidatorBean() {
            contextRunner.run(context -> {
                assertThat(context).hasSingleBean(CompositeValidator.class);
                CompositeValidator<?> validator = context.getBean(CompositeValidator.class);
                assertThat(validator).isNotNull();
            });
        }

        @Test
        @DisplayName("should have all validators in composite")
        void shouldHaveAllValidatorsInComposite() {
            contextRunner.run(context -> {
                @SuppressWarnings("unchecked")
                CompositeValidator<FlowDocument> validator =
                        (CompositeValidator<FlowDocument>) context.getBean(CompositeValidator.class);

                // Composite should contain 5 validators
                assertThat(validator.size()).isEqualTo(5);
                assertThat(validator.isEmpty()).isFalse();
            });
        }
    }

    @Nested
    @DisplayName("Validator bean names")
    class ValidatorBeanNames {

        @Test
        @DisplayName("should register flowValidator bean with expected name")
        void shouldRegisterFlowValidatorWithName() {
            contextRunner.run(context -> {
                assertThat(context.containsBean("flowValidator")).isTrue();
            });
        }
    }
}

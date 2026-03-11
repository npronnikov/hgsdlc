package ru.hgd.sdlc.compiler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.validation.CompositeValidator;
import ru.hgd.sdlc.compiler.domain.validation.Validator;
import ru.hgd.sdlc.compiler.domain.validation.rules.*;

import java.util.List;

/**
 * Configuration for validator beans in the compiler module.
 * Creates and wires all validators into a composite for comprehensive validation.
 *
 * <p>Validators are ordered by priority:
 * <ol>
 *   <li>FrontmatterValidator - validates required fields and ID formats</li>
 *   <li>PhaseValidator - validates phase structure</li>
 *   <li>StepValidator - validates node/step definitions</li>
 *   <li>CrossReferenceValidator - validates references between entities</li>
 *   <li>SemanticValidator - validates flow semantics (cycles, reachability)</li>
 * </ol>
 */
@Configuration
public class ValidatorConfiguration {

    /**
     * Creates the FrontmatterValidator bean.
     * Validates required frontmatter fields and ID formats.
     *
     * @return a new FrontmatterValidator instance
     */
    @Bean
    @Order(1)
    public FrontmatterValidator frontmatterValidator() {
        return new FrontmatterValidator();
    }

    /**
     * Creates the PhaseValidator bean.
     * Validates phase structure, names, and gate references.
     *
     * @return a new PhaseValidator instance
     */
    @Bean
    @Order(2)
    public PhaseValidator phaseValidator() {
        return new PhaseValidator();
    }

    /**
     * Creates the StepValidator bean.
     * Validates node definitions, types, and transitions.
     *
     * @return a new StepValidator instance
     */
    @Bean
    @Order(3)
    public StepValidator stepValidator() {
        return new StepValidator();
    }

    /**
     * Creates the CrossReferenceValidator bean.
     * Validates references between phases, nodes, skills, and artifacts.
     *
     * @return a new CrossReferenceValidator instance
     */
    @Bean
    @Order(4)
    public CrossReferenceValidator crossReferenceValidator() {
        return new CrossReferenceValidator();
    }

    /**
     * Creates the SemanticValidator bean.
     * Validates semantic correctness: entry points, cycles, reachability.
     *
     * @return a new SemanticValidator instance
     */
    @Bean
    @Order(5)
    public SemanticValidator semanticValidator() {
        return new SemanticValidator();
    }

    /**
     * Creates a CompositeValidator for FlowDocument that combines all flow validators.
     * Validators are applied in order defined by their @Order annotation.
     *
     * @param frontmatterValidator validates frontmatter fields
     * @param phaseValidator validates phase structure
     * @param stepValidator validates node/step definitions
     * @param crossReferenceValidator validates cross-references
     * @param semanticValidator validates semantic correctness
     * @return a composite validator for FlowDocument
     */
    @Bean
    @SuppressWarnings("unchecked")
    public CompositeValidator<FlowDocument> flowValidator(
            FrontmatterValidator frontmatterValidator,
            PhaseValidator phaseValidator,
            StepValidator stepValidator,
            CrossReferenceValidator crossReferenceValidator,
            SemanticValidator semanticValidator) {

        // Note: FrontmatterValidator is a Validator<Object>, we adapt it for FlowDocument
        List<Validator<FlowDocument>> validators = List.of(
            adaptToObjectValidator(frontmatterValidator),
            phaseValidator,
            stepValidator,
            crossReferenceValidator,
            semanticValidator
        );

        return CompositeValidator.of(validators);
    }

    /**
     * Adapts a FrontmatterValidator (Validator&lt;Object&gt;) to work as a Validator&lt;FlowDocument&gt;.
     *
     * @param validator the frontmatter validator
     * @return an adapted validator for FlowDocument
     */
    private Validator<FlowDocument> adaptToObjectValidator(FrontmatterValidator validator) {
        return (flow, context) -> validator.validate(flow, context);
    }
}

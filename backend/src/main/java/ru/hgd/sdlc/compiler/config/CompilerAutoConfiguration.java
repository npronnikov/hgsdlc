package ru.hgd.sdlc.compiler.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import ru.hgd.sdlc.compiler.domain.parser.FlowParser;
import ru.hgd.sdlc.compiler.domain.parser.FrontmatterExtractor;
import ru.hgd.sdlc.compiler.domain.parser.SkillParser;
import ru.hgd.sdlc.compiler.domain.validation.CompositeValidator;
import ru.hgd.sdlc.compiler.domain.validation.rules.*;

/**
 * Auto-configuration for the compiler module.
 * Enables using the compiler as a standalone library without Spring Boot application context.
 *
 * <p>This auto-configuration is activated when:
 * <ul>
 *   <li>The compiler classes are on the classpath</li>
 *   <li>The property sdlc.compiler.enabled is true (default)</li>
 * </ul>
 *
 * <p>All beans are created only if not already present in the application context.
 */
@AutoConfiguration
@ConditionalOnClass(name = "ru.hgd.sdlc.compiler.domain.parser.FrontmatterExtractor")
@ConditionalOnProperty(prefix = "sdlc.compiler", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CompilerProperties.class)
public class CompilerAutoConfiguration {

    /**
     * Creates the CompilerProperties bean if not already present.
     *
     * @return the compiler configuration properties
     */
    @Bean
    @ConditionalOnMissingBean
    public CompilerProperties compilerProperties() {
        return new CompilerProperties();
    }

    // === Parsers ===

    /**
     * Creates FrontmatterExtractor bean if not already present.
     *
     * @return a new FrontmatterExtractor instance
     */
    @Bean
    @ConditionalOnMissingBean
    public FrontmatterExtractor frontmatterExtractor() {
        return new FrontmatterExtractor();
    }

    /**
     * Creates FlowParser bean if not already present.
     *
     * @return a new FlowParser instance
     */
    @Bean
    @ConditionalOnMissingBean
    public FlowParser flowParser() {
        return new FlowParser();
    }

    /**
     * Creates SkillParser bean if not already present.
     *
     * @return a new SkillParser instance
     */
    @Bean
    @ConditionalOnMissingBean
    public SkillParser skillParser() {
        return new SkillParser();
    }

    // === Validators ===

    /**
     * Creates FrontmatterValidator bean if not already present.
     *
     * @return a new FrontmatterValidator instance
     */
    @Bean
    @ConditionalOnMissingBean
    public FrontmatterValidator frontmatterValidator() {
        return new FrontmatterValidator();
    }

    /**
     * Creates PhaseValidator bean if not already present.
     *
     * @return a new PhaseValidator instance
     */
    @Bean
    @ConditionalOnMissingBean
    public PhaseValidator phaseValidator() {
        return new PhaseValidator();
    }

    /**
     * Creates StepValidator bean if not already present.
     *
     * @return a new StepValidator instance
     */
    @Bean
    @ConditionalOnMissingBean
    public StepValidator stepValidator() {
        return new StepValidator();
    }

    /**
     * Creates CrossReferenceValidator bean if not already present.
     *
     * @return a new CrossReferenceValidator instance
     */
    @Bean
    @ConditionalOnMissingBean
    public CrossReferenceValidator crossReferenceValidator() {
        return new CrossReferenceValidator();
    }

    /**
     * Creates SemanticValidator bean if not already present.
     *
     * @return a new SemanticValidator instance
     */
    @Bean
    @ConditionalOnMissingBean
    public SemanticValidator semanticValidator() {
        return new SemanticValidator();
    }

    /**
     * Creates CompositeValidator for FlowDocument if not already present.
     *
     * @param frontmatterValidator validates frontmatter fields
     * @param phaseValidator validates phase structure
     * @param stepValidator validates node/step definitions
     * @param crossReferenceValidator validates cross-references
     * @param semanticValidator validates semantic correctness
     * @return a composite validator for FlowDocument
     */
    @Bean
    @ConditionalOnMissingBean(name = "flowValidator")
    public CompositeValidator<ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument> flowValidator(
            FrontmatterValidator frontmatterValidator,
            PhaseValidator phaseValidator,
            StepValidator stepValidator,
            CrossReferenceValidator crossReferenceValidator,
            SemanticValidator semanticValidator) {

        return CompositeValidator.of(
            adaptToObjectValidator(frontmatterValidator),
            phaseValidator,
            stepValidator,
            crossReferenceValidator,
            semanticValidator
        );
    }

    private ru.hgd.sdlc.compiler.domain.validation.Validator<ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument>
            adaptToObjectValidator(FrontmatterValidator validator) {
        return (flow, context) -> validator.validate(flow, context);
    }
}

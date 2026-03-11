package ru.hgd.sdlc.compiler.application;

import org.springframework.stereotype.Service;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;
import ru.hgd.sdlc.compiler.domain.validation.CompositeValidator;
import ru.hgd.sdlc.compiler.domain.validation.ValidationContext;
import ru.hgd.sdlc.compiler.domain.validation.ValidationError;
import ru.hgd.sdlc.compiler.domain.validation.ValidationResult;
import ru.hgd.sdlc.compiler.domain.validation.Validator;
import ru.hgd.sdlc.compiler.domain.validation.rules.CrossReferenceValidator;
import ru.hgd.sdlc.compiler.domain.validation.rules.FrontmatterValidator;
import ru.hgd.sdlc.compiler.domain.validation.rules.PhaseValidator;
import ru.hgd.sdlc.compiler.domain.validation.rules.SemanticValidator;
import ru.hgd.sdlc.compiler.domain.validation.rules.StepValidator;

import java.util.List;
import java.util.Objects;

/**
 * Application service for validating documents.
 * Provides polymorphic validation for flow and skill documents.
 *
 * <p>This service handles the second stage of the compilation pipeline:
 * FlowDocument/SkillDocument -> ValidationResult
 *
 * <p>Uses CompositeValidator pattern to apply all validation rules.
 */
@Service
public class ValidationService {

    private final Validator<Object> flowValidator;
    private final Validator<Object> skillValidator;

    public ValidationService() {
        // Build flow validator with all flow-specific rules
        // Note: All validators need to be converted to Validator<Object> for composition
        this.flowValidator = CompositeValidator.of(
            adaptFrontmatterValidator(new FrontmatterValidator()),
            adaptFlowValidator(new PhaseValidator()),
            adaptFlowValidator(new StepValidator()),
            adaptFlowValidator(new CrossReferenceValidator()),
            adaptFlowValidator(new SemanticValidator())
        );

        // Build skill validator with skill-specific rules
        this.skillValidator = CompositeValidator.of(
            adaptFrontmatterValidator(new FrontmatterValidator())
        );
    }

    private Validator<Object> adaptFrontmatterValidator(FrontmatterValidator validator) {
        return (obj, context) -> validator.validate(obj, context);
    }

    private Validator<Object> adaptFlowValidator(Validator<FlowDocument> validator) {
        return (obj, context) -> {
            if (obj instanceof FlowDocument flow) {
                return validator.validate(flow, context);
            }
            return ru.hgd.sdlc.compiler.domain.validation.ValidationResult.valid();
        };
    }

    /**
     * Validates a flow document using all flow validation rules.
     *
     * @param flow the flow document to validate
     * @return the validation result
     */
    public ValidationResult validateFlow(FlowDocument flow) {
        Objects.requireNonNull(flow, "flow cannot be null");

        ValidationContext context = ValidationContext.forFile(null);
        context.setFlowDocument(flow);

        return flowValidator.validate(flow, context);
    }

    /**
     * Validates a flow document with a file path for error reporting.
     *
     * @param flow     the flow document to validate
     * @param filePath the file path for error location reporting
     * @return the validation result
     */
    public ValidationResult validateFlow(FlowDocument flow, String filePath) {
        Objects.requireNonNull(flow, "flow cannot be null");

        ValidationContext context = ValidationContext.forFile(filePath);
        context.setFlowDocument(flow);

        return flowValidator.validate(flow, context);
    }

    /**
     * Validates a skill document using all skill validation rules.
     *
     * @param skill the skill document to validate
     * @return the validation result
     */
    public ValidationResult validateSkill(SkillDocument skill) {
        Objects.requireNonNull(skill, "skill cannot be null");

        ValidationContext context = ValidationContext.forFile(null);
        context.setSkillDocument(skill);

        return skillValidator.validate(skill, context);
    }

    /**
     * Validates a skill document with a file path for error reporting.
     *
     * @param skill    the skill document to validate
     * @param filePath the file path for error location reporting
     * @return the validation result
     */
    public ValidationResult validateSkill(SkillDocument skill, String filePath) {
        Objects.requireNonNull(skill, "skill cannot be null");

        ValidationContext context = ValidationContext.forFile(filePath);
        context.setSkillDocument(skill);

        return skillValidator.validate(skill, context);
    }

    /**
     * Polymorphic validation method that validates any supported document type.
     *
     * @param document the document to validate (FlowDocument or SkillDocument)
     * @return the validation result
     * @throws IllegalArgumentException if the document type is not supported
     */
    public ValidationResult validate(Object document) {
        Objects.requireNonNull(document, "document cannot be null");

        if (document instanceof FlowDocument flow) {
            return validateFlow(flow);
        } else if (document instanceof SkillDocument skill) {
            return validateSkill(skill);
        }

        throw new IllegalArgumentException(
            "Unsupported document type: " + document.getClass().getName()
        );
    }

    /**
     * Polymorphic validation method with file path.
     *
     * @param document the document to validate
     * @param filePath the file path for error location reporting
     * @return the validation result
     * @throws IllegalArgumentException if the document type is not supported
     */
    public ValidationResult validate(Object document, String filePath) {
        Objects.requireNonNull(document, "document cannot be null");

        if (document instanceof FlowDocument flow) {
            return validateFlow(flow, filePath);
        } else if (document instanceof SkillDocument skill) {
            return validateSkill(skill, filePath);
        }

        throw new IllegalArgumentException(
            "Unsupported document type: " + document.getClass().getName()
        );
    }

    /**
     * Checks if a document is valid (no errors, warnings are allowed).
     *
     * @param document the document to validate
     * @return true if the document is valid
     */
    public boolean isValid(Object document) {
        return validate(document).isValid();
    }

    /**
     * Checks if a document has any issues (errors or warnings).
     *
     * @param document the document to validate
     * @return true if the document has issues
     */
    public boolean hasIssues(Object document) {
        return validate(document).hasIssues();
    }

    /**
     * Checks if a document has errors (not just warnings).
     *
     * @param document the document to validate
     * @return true if the document has errors
     */
    public boolean hasErrors(Object document) {
        ValidationResult result = validate(document);
        return !result.errors().isEmpty();
    }

    /**
     * Converts a ValidationResult to human-readable messages.
     *
     * @param result the validation result
     * @return a list of formatted messages
     */
    public List<String> formatResult(ValidationResult result) {
        return result.allIssues().stream()
            .map(this::formatIssue)
            .toList();
    }

    /**
     * Formats validation errors only.
     *
     * @param result the validation result
     * @return a list of formatted error messages
     */
    public List<String> formatErrors(ValidationResult result) {
        return result.errors().stream()
            .map(this::formatIssue)
            .toList();
    }

    /**
     * Formats validation warnings only.
     *
     * @param result the validation result
     * @return a list of formatted warning messages
     */
    public List<String> formatWarnings(ValidationResult result) {
        return result.warnings().stream()
            .map(this::formatIssue)
            .toList();
    }

    private String formatIssue(ValidationError issue) {
        String severity = issue.isError() ? "ERROR" : "WARN";
        String loc = issue.location().toString();
        return String.format("[%s] %s: %s%s",
            issue.code(),
            severity,
            issue.message(),
            loc.isEmpty() ? "" : " at " + loc);
    }
}

package ru.hgd.sdlc.compiler.domain.validation;

import ru.hgd.sdlc.compiler.domain.model.authored.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Context for validation operations.
 * Tracks accumulated errors/warnings and provides reference resolution.
 */
public final class ValidationContext {

    private final String filePath;
    private final List<ValidationError> errors;
    private final List<ValidationError> warnings;

    // Reference resolvers for cross-reference validation
    private final Map<String, Function<String, Boolean>> referenceResolvers;

    // Cached document references
    private FlowDocument flowDocument;
    private SkillDocument skillDocument;

    private ValidationContext(
            String filePath,
            List<ValidationError> errors,
            List<ValidationError> warnings,
            Map<String, Function<String, Boolean>> referenceResolvers,
            FlowDocument flowDocument,
            SkillDocument skillDocument) {
        this.filePath = filePath;
        this.errors = errors;
        this.warnings = warnings;
        this.referenceResolvers = referenceResolvers;
        this.flowDocument = flowDocument;
        this.skillDocument = skillDocument;
    }

    /**
     * Creates an empty validation context for a file.
     *
     * @param filePath the file being validated (may be null)
     * @return a new validation context
     */
    public static ValidationContext forFile(String filePath) {
        return new ValidationContext(
            filePath,
            new ArrayList<>(),
            new ArrayList<>(),
            new HashMap<>(),
            null,
            null
        );
    }

    /**
     * Creates an empty validation context without a file.
     *
     * @return a new validation context
     */
    public static ValidationContext empty() {
        return forFile(null);
    }

    /**
     * Returns the file path being validated.
     *
     * @return the file path, or empty if not set
     */
    public Optional<String> filePath() {
        return Optional.ofNullable(filePath);
    }

    /**
     * Creates a source location at the specified line.
     *
     * @param line the line number
     * @return a source location
     */
    public SourceLocation location(int line) {
        return SourceLocation.of(filePath, line);
    }

    /**
     * Creates a source location at the specified line and column.
     *
     * @param line   the line number
     * @param column the column number
     * @return a source location
     */
    public SourceLocation location(int line, int column) {
        return SourceLocation.of(filePath, line, column);
    }

    /**
     * Creates a source location for the file (line 1).
     *
     * @return a source location
     */
    public SourceLocation fileLocation() {
        return filePath != null ? SourceLocation.file(filePath) : SourceLocation.unknown();
    }

    // Error accumulation

    /**
     * Adds an error to the context.
     *
     * @param code     the error code
     * @param message  the error message
     * @param location the source location
     */
    public void addError(String code, String message, SourceLocation location) {
        errors.add(ValidationError.error(code, message, location));
    }

    /**
     * Adds an error at a specific line.
     *
     * @param code    the error code
     * @param message the error message
     * @param line    the line number
     */
    public void addError(String code, String message, int line) {
        addError(code, message, location(line));
    }

    /**
     * Adds a warning to the context.
     *
     * @param code     the warning code
     * @param message  the warning message
     * @param location the source location
     */
    public void addWarning(String code, String message, SourceLocation location) {
        warnings.add(ValidationError.warning(code, message, location));
    }

    /**
     * Adds a warning at a specific line.
     *
     * @param code    the warning code
     * @param message the warning message
     * @param line    the line number
     */
    public void addWarning(String code, String message, int line) {
        addWarning(code, message, location(line));
    }

    /**
     * Returns all accumulated errors.
     *
     * @return list of errors
     */
    public List<ValidationError> errors() {
        return List.copyOf(errors);
    }

    /**
     * Returns all accumulated warnings.
     *
     * @return list of warnings
     */
    public List<ValidationError> warnings() {
        return List.copyOf(warnings);
    }

    /**
     * Checks if there are any accumulated errors.
     *
     * @return true if there are errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Checks if there are any accumulated warnings.
     *
     * @return true if there are warnings
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Builds the final validation result from accumulated issues.
     *
     * @return the validation result
     */
    public ValidationResult toResult() {
        if (errors.isEmpty()) {
            return warnings.isEmpty()
                ? ValidationResult.valid()
                : ValidationResult.valid(warnings);
        }
        return ValidationResult.invalid(errors, warnings);
    }

    // Reference resolution

    /**
     * Registers a reference resolver for a reference type.
     *
     * @param referenceType the type of reference (e.g., "phase", "skill", "node")
     * @param resolver      the resolver function that returns true if reference exists
     */
    public void registerResolver(String referenceType, Function<String, Boolean> resolver) {
        referenceResolvers.put(referenceType, resolver);
    }

    /**
     * Checks if a reference exists.
     *
     * @param referenceType the type of reference
     * @param referenceId   the reference identifier
     * @return true if the reference can be resolved
     */
    public boolean canResolve(String referenceType, String referenceId) {
        Function<String, Boolean> resolver = referenceResolvers.get(referenceType);
        return resolver != null && resolver.apply(referenceId);
    }

    // Document access

    /**
     * Sets the flow document being validated.
     *
     * @param document the flow document
     */
    public void setFlowDocument(FlowDocument document) {
        this.flowDocument = document;
    }

    /**
     * Returns the flow document being validated.
     *
     * @return the flow document, or empty if not a flow
     */
    public Optional<FlowDocument> flowDocument() {
        return Optional.ofNullable(flowDocument);
    }

    /**
     * Sets the skill document being validated.
     *
     * @param document the skill document
     */
    public void setSkillDocument(SkillDocument document) {
        this.skillDocument = document;
    }

    /**
     * Returns the skill document being validated.
     *
     * @return the skill document, or empty if not a skill
     */
    public Optional<SkillDocument> skillDocument() {
        return Optional.ofNullable(skillDocument);
    }

    // Convenience methods for common lookups

    /**
     * Checks if a phase exists in the current flow.
     *
     * @param phaseId the phase ID
     * @return true if the phase exists
     */
    public boolean hasPhase(String phaseId) {
        return flowDocument != null
            && flowDocument.phases().containsKey(PhaseId.of(phaseId));
    }

    /**
     * Checks if a node exists in the current flow.
     *
     * @param nodeId the node ID
     * @return true if the node exists
     */
    public boolean hasNode(String nodeId) {
        return flowDocument != null
            && flowDocument.nodes().containsKey(NodeId.of(nodeId));
    }

    /**
     * Gets a phase from the current flow.
     *
     * @param phaseId the phase ID
     * @return the phase, or empty if not found
     */
    public Optional<PhaseDocument> getPhase(String phaseId) {
        if (flowDocument == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(flowDocument.getPhase(PhaseId.of(phaseId)));
    }

    /**
     * Gets a node from the current flow.
     *
     * @param nodeId the node ID
     * @return the node, or empty if not found
     */
    public Optional<NodeDocument> getNode(String nodeId) {
        if (flowDocument == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(flowDocument.getNode(NodeId.of(nodeId)));
    }

    /**
     * Creates a child context for nested validation.
     * The child shares the same error/warning lists but can have different document context.
     *
     * @return a child context
     */
    public ValidationContext child() {
        return new ValidationContext(
            filePath,
            errors,  // Shared reference to parent's lists
            warnings,
            new HashMap<>(referenceResolvers),
            flowDocument,
            skillDocument
        );
    }

    /**
     * Creates a new context with a different file path.
     *
     * @param newFilePath the new file path
     * @return a new context
     */
    public ValidationContext withFile(String newFilePath) {
        return new ValidationContext(
            newFilePath,
            errors,
            warnings,
            referenceResolvers,
            flowDocument,
            skillDocument
        );
    }
}

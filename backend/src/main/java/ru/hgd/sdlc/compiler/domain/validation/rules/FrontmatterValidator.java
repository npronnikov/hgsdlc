package ru.hgd.sdlc.compiler.domain.validation.rules;

import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;
import ru.hgd.sdlc.compiler.domain.validation.*;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static ru.hgd.sdlc.compiler.domain.validation.rules.ValidationCodes.*;

/**
 * Validates frontmatter fields in flow and skill documents.
 * Checks for required fields, valid type values, and valid ID formats.
 */
public class FrontmatterValidator implements Validator<Object> {

    // Valid document types
    private static final Set<String> VALID_TYPES = Set.of("flow", "skill");

    // Kebab-case pattern: lowercase letters, numbers, hyphens (not at start/end or consecutive)
    // Each segment after hyphen can start with letter or number, but not hyphen
    private static final Pattern KEBAB_CASE_PATTERN = Pattern.compile("^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$");

    // Allowed fields for flow documents
    private static final Set<String> FLOW_FIELDS = Set.of(
        "id", "name", "version", "type", "phase_order", "start_roles",
        "resume_policy", "author", "authored_at", "extensions"
    );

    // Allowed fields for skill documents
    private static final Set<String> SKILL_FIELDS = Set.of(
        "id", "name", "version", "type", "description", "handler",
        "input_schema", "output_schema", "tags", "extensions",
        "author", "authored_at"
    );

    @Override
    public ValidationResult validate(Object input, ValidationContext context) {
        if (input instanceof FlowDocument flow) {
            return validateFlowFrontmatter(flow, context);
        } else if (input instanceof SkillDocument skill) {
            return validateSkillFrontmatter(skill, context);
        }

        // Unknown type - should not happen
        context.addError(INVALID_TYPE_VALUE, "Unknown document type: " + input.getClass().getName(), context.fileLocation());
        return context.toResult();
    }

    private ValidationResult validateFlowFrontmatter(FlowDocument flow, ValidationContext context) {
        // Validate ID format
        validateIdFormat(flow.id().value(), "flow", context);

        // Validate version is present (already validated during parsing, but double-check)
        if (flow.version() == null) {
            context.addError(MISSING_REQUIRED_FIELD, "version is required", context.fileLocation());
        }

        // Validate name is present (warning only)
        if (flow.name() == null || flow.name().isBlank()) {
            context.addWarning(SUGGESTED_FIELD_MISSING, "name is recommended for better documentation", context.fileLocation());
        }

        // Validate start_roles exist
        if (flow.startRoles() != null && !flow.startRoles().isEmpty()) {
            for (var role : flow.startRoles()) {
                if (role.value() == null || role.value().isBlank()) {
                    context.addError(INVALID_ID_FORMAT, "start_roles contains invalid role", context.fileLocation());
                }
            }
        }

        return context.toResult();
    }

    private ValidationResult validateSkillFrontmatter(SkillDocument skill, ValidationContext context) {
        // Validate ID format
        validateIdFormat(skill.id().value(), "skill", context);

        // Validate version
        if (skill.version() == null) {
            context.addError(MISSING_REQUIRED_FIELD, "version is required", context.fileLocation());
        }

        // Validate name
        if (skill.name() == null || skill.name().isBlank()) {
            context.addError(MISSING_REQUIRED_FIELD, "name is required for skills", context.fileLocation());
        }

        // Validate handler
        if (skill.handler() == null) {
            context.addError(MISSING_REQUIRED_FIELD, "handler is required for skills", context.fileLocation());
        }

        return context.toResult();
    }

    /**
     * Validates that an ID follows kebab-case format.
     *
     * @param id      the ID to validate
     * @param type    the type of document (for error messages)
     * @param context the validation context
     */
    public void validateIdFormat(String id, String type, ValidationContext context) {
        if (id == null || id.isBlank()) {
            context.addError(MISSING_REQUIRED_FIELD, type + " id is required", context.fileLocation());
            return;
        }

        if (!KEBAB_CASE_PATTERN.matcher(id).matches()) {
            context.addError(
                INVALID_ID_FORMAT,
                String.format("%s id '%s' must be in kebab-case format (lowercase letters, numbers, hyphens)", type, id),
                context.fileLocation()
            );
        }
    }

    /**
     * Validates that a document type is valid.
     *
     * @param type the type to validate
     * @return true if valid
     */
    public static boolean isValidType(String type) {
        return type != null && VALID_TYPES.contains(type.toLowerCase());
    }

    /**
     * Validates that a field is known for the document type.
     *
     * @param fieldName     the field name
     * @param documentType  the document type ("flow" or "skill")
     * @return true if the field is known
     */
    public static boolean isKnownField(String fieldName, String documentType) {
        if ("flow".equalsIgnoreCase(documentType)) {
            return FLOW_FIELDS.contains(fieldName);
        } else if ("skill".equalsIgnoreCase(documentType)) {
            return SKILL_FIELDS.contains(fieldName);
        }
        return false;
    }
}

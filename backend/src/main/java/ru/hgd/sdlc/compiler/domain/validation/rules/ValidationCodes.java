package ru.hgd.sdlc.compiler.domain.validation.rules;

/**
 * Central definition of validation error codes.
 * Codes follow pattern: V[Category][Number] where category indicates the validation type.
 *
 * Categories:
 * - V1xxx: Frontmatter validation
 * - V2xxx: Phase validation
 * - V3xxx: Node/Step validation
 * - V4xxx: Cross-reference validation
 * - V5xxx: Semantic validation
 */
public final class ValidationCodes {

    private ValidationCodes() {
        // Utility class
    }

    // Frontmatter validation codes (V1xxx)
    public static final String MISSING_REQUIRED_FIELD = "V1001";
    public static final String INVALID_TYPE_VALUE = "V1002";
    public static final String INVALID_ID_FORMAT = "V1003";
    public static final String INVALID_VERSION_FORMAT = "V1004";
    public static final String UNKNOWN_FIELD = "V1005";

    // Phase validation codes (V2xxx)
    public static final String PHASE_ID_NOT_UNIQUE = "V2001";
    public static final String PHASE_MISSING_ID = "V2002";
    public static final String PHASE_MISSING_NAME = "V2003";
    public static final String PHASE_EMPTY = "V2004";
    public static final String PHASE_ORDER_MISMATCH = "V2005";

    // Node/Step validation codes (V3xxx)
    public static final String NODE_ID_NOT_UNIQUE = "V3001";
    public static final String NODE_MISSING_ID = "V3002";
    public static final String NODE_INVALID_TYPE = "V3003";
    public static final String NODE_MISSING_HANDLER = "V3004";
    public static final String NODE_MISSING_GATE_KIND = "V3005";
    public static final String NODE_MISSING_REQUIRED_FIELD = "V3006";

    // Cross-reference validation codes (V4xxx)
    public static final String UNRESOLVED_PHASE_REF = "V4001";
    public static final String UNRESOLVED_NODE_REF = "V4002";
    public static final String UNRESOLVED_SKILL_REF = "V4003";
    public static final String UNRESOLVED_ARTIFACT_REF = "V4004";
    public static final String UNRESOLVED_PHASE_ORDER_REF = "V4005";

    // Semantic validation codes (V5xxx)
    public static final String NO_ENTRY_PHASE = "V5001";
    public static final String CYCLE_IN_PHASE_ORDER = "V5002";
    public static final String NO_TERMINAL_PHASE = "V5003";
    public static final String ORPHAN_NODE = "V5004";
    public static final String UNREACHABLE_PHASE = "V5005";
    public static final String UNREACHABLE_NODE = "V5006";

    // Warning codes (W prefix)
    public static final String UNUSED_PHASE = "W5001";
    public static final String UNUSED_ARTIFACT = "W5002";
    public static final String DEPRECATED_FIELD = "W1001";
    public static final String SUGGESTED_FIELD_MISSING = "W1002";
}

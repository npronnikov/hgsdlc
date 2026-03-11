package ru.hgd.sdlc.compiler.domain.model.ir;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.compiler.domain.model.authored.NodeId;

import java.util.Map;

/**
 * Policy hook definition in compiled IR.
 * Represents a point where policy decisions are evaluated.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public class PolicyHook {

    /**
     * Type of policy hook.
     */
    public enum Type {
        /**
         * Pre-execution check before node runs.
         */
        PRE_EXECUTE,

        /**
         * Post-execution check after node completes.
         */
        POST_EXECUTE,

        /**
         * Gate entry check.
         */
        GATE_ENTRY,

        /**
         * Gate exit check after approval.
         */
        GATE_EXIT,

        /**
         * Phase transition check.
         */
        PHASE_TRANSITION,

        /**
         * Artifact promotion check.
         */
        ARTIFACT_PROMOTION
    }

    @NonNull private final String id;

    /**
     * Type of this hook.
     */
    @NonNull private final Type type;

    /**
     * Node this hook is attached to (if applicable).
     */
    private final NodeId nodeId;

    /**
     * Policy rule to evaluate.
     */
    @NonNull private final String policyRule;

    /**
     * Additional configuration for the hook.
     */
    @Builder.Default private final Map<String, Object> config = Map.of();

    /**
     * Whether this hook blocks execution on failure.
     */
    @Builder.Default private final boolean blocking = true;
}

package ru.hgd.sdlc.compiler.domain.model.ir;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.compiler.domain.model.authored.GateKind;
import ru.hgd.sdlc.compiler.domain.model.authored.Role;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Normalized configuration for a gate node.
 * This is the compiled, resolved form of gate settings.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public class GateConfig {

    @NonNull private final GateKind kind;

    /**
     * Required approver roles.
     */
    @Builder.Default private final Set<Role> requiredApprovers = Set.of();

    /**
     * Condition expression for CONDITIONAL gates.
     */
    private final String condition;

    /**
     * Configuration parameters.
     */
    @Builder.Default private final Map<String, Object> config = Map.of();

    /**
     * Returns the condition if present.
     */
    public Optional<String> condition() {
        return Optional.ofNullable(condition);
    }

    /**
     * Checks if this is an approval gate.
     */
    public boolean isApproval() {
        return kind == GateKind.APPROVAL;
    }

    /**
     * Checks if this is a questionnaire gate.
     */
    public boolean isQuestionnaire() {
        return kind == GateKind.QUESTIONNAIRE;
    }

    /**
     * Checks if this is a conditional gate.
     */
    public boolean isConditional() {
        return kind == GateKind.CONDITIONAL;
    }
}

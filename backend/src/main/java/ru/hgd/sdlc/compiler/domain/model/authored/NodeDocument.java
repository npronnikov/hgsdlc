package ru.hgd.sdlc.compiler.domain.model.authored;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a node within a phase.
 * A node is either an EXECUTOR (performs work) or a GATE (waits for input).
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class NodeDocument {

    @EqualsAndHashCode.Include
    @NonNull private final NodeId id;

    @NonNull private final NodeType type;

    private final String name;
    private final MarkdownBody description;
    private final MarkdownBody instructions;

    // Phase membership
    private final PhaseId phaseId;

    // For EXECUTOR nodes
    private final ExecutorKind executorKind;
    private final HandlerRef handler;

    // For GATE nodes
    private final GateKind gateKind;
    @Builder.Default private final Set<Role> requiredApprovers = Set.of();

    // Inputs/Outputs
    @Builder.Default private final List<ArtifactBinding> inputs = List.of();
    @Builder.Default private final List<ArtifactBinding> outputs = List.of();

    // Transitions
    @Builder.Default private final List<Transition> transitions = List.of();

    // Configuration
    @Builder.Default private final Map<String, Object> config = Map.of();
    @Builder.Default private final Map<String, Object> extensions = Map.of();

    // Optional wrappers for nullable fields
    public Optional<MarkdownBody> instructions() { return Optional.ofNullable(instructions); }
    public Optional<PhaseId> phaseId() { return Optional.ofNullable(phaseId); }
    public Optional<ExecutorKind> executorKind() { return Optional.ofNullable(executorKind); }
    public Optional<HandlerRef> handler() { return Optional.ofNullable(handler); }
    public Optional<GateKind> gateKind() { return Optional.ofNullable(gateKind); }

    /**
     * Checks if this node is an executor node.
     */
    public boolean isExecutor() {
        return type == NodeType.EXECUTOR;
    }

    /**
     * Checks if this node is a gate node.
     */
    public boolean isGate() {
        return type == NodeType.GATE;
    }
}

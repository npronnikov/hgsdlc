package ru.hgd.sdlc.compiler.domain.model.ir;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.compiler.domain.model.authored.MarkdownBody;
import ru.hgd.sdlc.compiler.domain.model.authored.NodeId;
import ru.hgd.sdlc.compiler.domain.model.authored.NodeType;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.util.List;
import java.util.Optional;

/**
 * Compiled node in IR.
 * Represents a normalized node with resolved references and deterministic hash.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public class NodeIr {

    @NonNull private final NodeId id;

    /**
     * Type of node (EXECUTOR or GATE).
     */
    @NonNull private final NodeType type;

    /**
     * Phase this node belongs to.
     */
    @NonNull private final PhaseId phaseId;

    /**
     * Human-readable name.
     */
    private final String name;

    /**
     * Normalized executor configuration (for EXECUTOR nodes).
     */
    private final ExecutorConfig executorConfig;

    /**
     * Normalized gate configuration (for GATE nodes).
     */
    private final GateConfig gateConfig;

    /**
     * Resolved input artifact bindings.
     */
    @Builder.Default private final List<ResolvedArtifactBinding> inputs = List.of();

    /**
     * Resolved output artifact bindings.
     */
    @Builder.Default private final List<ResolvedArtifactBinding> outputs = List.of();

    /**
     * Indices into FlowIr.transitions list.
     */
    @Builder.Default private final List<Integer> transitionIndices = List.of();

    /**
     * Compiled instructions from markdown.
     */
    private final MarkdownBody instructions;

    /**
     * Deterministic hash of this node's content.
     */
    @NonNull private final Sha256 nodeHash;

    /**
     * Returns executor config if this is an executor node.
     */
    public Optional<ExecutorConfig> executorConfig() {
        return Optional.ofNullable(executorConfig);
    }

    /**
     * Returns gate config if this is a gate node.
     */
    public Optional<GateConfig> gateConfig() {
        return Optional.ofNullable(gateConfig);
    }

    /**
     * Returns instructions if present.
     */
    public Optional<MarkdownBody> instructions() {
        return Optional.ofNullable(instructions);
    }

    /**
     * Checks if this is an executor node.
     */
    public boolean isExecutor() {
        return type == NodeType.EXECUTOR;
    }

    /**
     * Checks if this is a gate node.
     */
    public boolean isGate() {
        return type == NodeType.GATE;
    }
}

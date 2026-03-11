package ru.hgd.sdlc.compiler.domain.model.ir;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.compiler.domain.model.authored.MarkdownBody;
import ru.hgd.sdlc.compiler.domain.model.authored.NodeId;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;

import java.util.List;

/**
 * Compiled phase in IR.
 * Represents a normalized phase with ordered nodes.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public class PhaseIr {

    @NonNull private final PhaseId id;

    /**
     * Human-readable name.
     */
    @NonNull private final String name;

    /**
     * Order of this phase in the flow.
     */
    private final int order;

    /**
     * Ordered list of executor node IDs in this phase.
     */
    @Builder.Default private final List<NodeId> nodeOrder = List.of();

    /**
     * Ordered list of gate node IDs in this phase.
     */
    @Builder.Default private final List<NodeId> gateOrder = List.of();

    /**
     * Description (compiled from markdown).
     */
    private final MarkdownBody description;

    /**
     * Total number of nodes in this phase.
     */
    public int totalNodes() {
        return nodeOrder.size() + gateOrder.size();
    }

    /**
     * Checks if this phase contains the given node.
     */
    public boolean containsNode(NodeId nodeId) {
        return nodeOrder.contains(nodeId) || gateOrder.contains(nodeId);
    }
}

package ru.hgd.sdlc.compiler.domain.model.authored;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;

/**
 * Represents a phase within a Flow document.
 * A phase is a logical grouping of nodes that execute together.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
public class PhaseDocument {

    @NonNull private final PhaseId id;
    @NonNull private final String name;
    private final MarkdownBody description;
    private final int order;
    @Builder.Default private final List<NodeId> nodeOrder = List.of();
    @Builder.Default private final List<NodeId> gateOrder = List.of();
    @Builder.Default private final Map<String, Object> extensions = Map.of();

    /**
     * Checks if this phase contains the given node.
     */
    public boolean containsNode(NodeId nodeId) {
        return nodeOrder.contains(nodeId) || gateOrder.contains(nodeId);
    }
}

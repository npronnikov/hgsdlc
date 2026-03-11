package ru.hgd.sdlc.compiler.domain.model.ir;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.compiler.domain.model.authored.NodeId;
import ru.hgd.sdlc.compiler.domain.model.authored.TransitionType;

import java.util.Optional;

/**
 * Compiled transition in IR.
 * Represents a normalized transition between nodes.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public class TransitionIr {

    /**
     * Source node ID.
     */
    @NonNull private final NodeId fromNode;

    /**
     * Target node ID.
     */
    @NonNull private final NodeId toNode;

    /**
     * Type of transition.
     */
    @NonNull private final TransitionType type;

    /**
     * Condition expression (optional).
     */
    private final String condition;

    /**
     * Index of this transition in the flow's transition list.
     */
    private final int index;

    /**
     * Returns the condition if present.
     */
    public Optional<String> condition() {
        return Optional.ofNullable(condition);
    }

    /**
     * Checks if this is a forward transition.
     */
    public boolean isForward() {
        return type == TransitionType.FORWARD;
    }

    /**
     * Checks if this is a rework (backward) transition.
     */
    public boolean isRework() {
        return type == TransitionType.REWORK;
    }

    /**
     * Checks if this is a skip transition.
     */
    public boolean isSkip() {
        return type == TransitionType.SKIP;
    }

    /**
     * Creates a forward transition.
     */
    public static TransitionIr forward(NodeId from, NodeId to, int index) {
        return TransitionIr.builder()
            .fromNode(from)
            .toNode(to)
            .type(TransitionType.FORWARD)
            .index(index)
            .build();
    }

    /**
     * Creates a conditional forward transition.
     */
    public static TransitionIr forward(NodeId from, NodeId to, String condition, int index) {
        return TransitionIr.builder()
            .fromNode(from)
            .toNode(to)
            .type(TransitionType.FORWARD)
            .condition(condition)
            .index(index)
            .build();
    }

    /**
     * Creates a rework transition.
     */
    public static TransitionIr rework(NodeId from, NodeId to, int index) {
        return TransitionIr.builder()
            .fromNode(from)
            .toNode(to)
            .type(TransitionType.REWORK)
            .index(index)
            .build();
    }

    /**
     * Creates a skip transition.
     */
    public static TransitionIr skip(NodeId from, NodeId to, String condition, int index) {
        return TransitionIr.builder()
            .fromNode(from)
            .toNode(to)
            .type(TransitionType.SKIP)
            .condition(condition)
            .index(index)
            .build();
    }
}

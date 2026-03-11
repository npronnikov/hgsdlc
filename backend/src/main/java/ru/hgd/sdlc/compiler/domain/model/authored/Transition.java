package ru.hgd.sdlc.compiler.domain.model.authored;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a transition from one node to another.
 */
public final class Transition {

    private final NodeId from;
    private final NodeId to;
    private final String condition;
    private final TransitionType type;

    private Transition(NodeId from, NodeId to, String condition, TransitionType type) {
        this.from = from;
        this.to = to;
        this.condition = condition;
        this.type = type;
    }

    /**
     * Creates a forward transition.
     *
     * @param from the source node
     * @param to the target node
     * @return a new Transition instance
     */
    public static Transition forward(NodeId from, NodeId to) {
        return new Transition(from, to, null, TransitionType.FORWARD);
    }

    /**
     * Creates a forward transition with a condition.
     *
     * @param from the source node
     * @param to the target node
     * @param condition the condition expression
     * @return a new Transition instance
     */
    public static Transition forward(NodeId from, NodeId to, String condition) {
        return new Transition(from, to, condition, TransitionType.FORWARD);
    }

    /**
     * Creates a rework transition (backward edge).
     *
     * @param from the source node
     * @param to the target node (must be earlier in the flow)
     * @return a new Transition instance
     */
    public static Transition rework(NodeId from, NodeId to) {
        return new Transition(from, to, null, TransitionType.REWORK);
    }

    /**
     * Creates a skip transition.
     *
     * @param from the source node
     * @param to the target node
     * @param condition the condition that triggers the skip
     * @return a new Transition instance
     */
    public static Transition skip(NodeId from, NodeId to, String condition) {
        return new Transition(from, to, condition, TransitionType.SKIP);
    }

    public NodeId from() {
        return from;
    }

    public NodeId to() {
        return to;
    }

    public Optional<String> condition() {
        return Optional.ofNullable(condition);
    }

    public TransitionType type() {
        return type;
    }

    public boolean isBackward() {
        return type == TransitionType.REWORK;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transition that = (Transition) o;
        return Objects.equals(from, that.from)
            && Objects.equals(to, that.to)
            && Objects.equals(condition, that.condition)
            && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, condition, type);
    }

    @Override
    public String toString() {
        String cond = condition != null ? " [" + condition + "]" : "";
        return "Transition{" + from + " --" + type + "--> " + to + cond + "}";
    }
}

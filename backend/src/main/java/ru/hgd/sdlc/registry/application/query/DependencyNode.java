package ru.hgd.sdlc.registry.application.query;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;

/**
 * Represents a single node in a dependency tree.
 * Each node represents a flow or skill release.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public final class DependencyNode {

    /**
     * The release identifier for this node.
     */
    @NonNull
    private final ReleaseId id;

    /**
     * The type of this dependency node.
     * Either "flow" or "skill".
     */
    @NonNull
    private final String type;

    /**
     * Human-readable name for this node.
     */
    @NonNull
    private final String name;

    /**
     * Creates a flow dependency node.
     *
     * @param id the release ID
     * @param name the display name
     * @return a new DependencyNode representing a flow
     */
    public static DependencyNode flow(ReleaseId id, String name) {
        return builder()
            .id(id)
            .type("flow")
            .name(name)
            .build();
    }

    /**
     * Creates a skill dependency node.
     *
     * @param id the release ID
     * @param name the display name
     * @return a new DependencyNode representing a skill
     */
    public static DependencyNode skill(ReleaseId id, String name) {
        return builder()
            .id(id)
            .type("skill")
            .name(name)
            .build();
    }

    /**
     * Checks if this node represents a flow.
     *
     * @return true if this is a flow node
     */
    public boolean isFlow() {
        return "flow".equals(type);
    }

    /**
     * Checks if this node represents a skill.
     *
     * @return true if this is a skill node
     */
    public boolean isSkill() {
        return "skill".equals(type);
    }
}

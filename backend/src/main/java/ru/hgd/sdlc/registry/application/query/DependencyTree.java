package ru.hgd.sdlc.registry.application.query;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Immutable dependency tree for a release.
 * Contains nodes (releases) and edges (dependency relationships).
 *
 * <p>This is a query projection optimized for UI display.
 * For runtime resolution, use {@link ru.hgd.sdlc.registry.domain.model.dependency.DependencyGraph}.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public final class DependencyTree {

    /**
     * The root release ID (the release for which this tree was built).
     */
    @NonNull
    private final ReleaseId root;

    /**
     * All nodes in the dependency tree.
     */
    @Singular("node")
    @NonNull
    private final List<DependencyNode> nodes;

    /**
     * All edges in the dependency tree.
     * An edge from A to B means "A depends on B".
     */
    @Singular("edge")
    @NonNull
    private final List<DependencyEdge> edges;

    /**
     * Returns an unmodifiable view of the nodes.
     *
     * @return unmodifiable list of nodes
     */
    public List<DependencyNode> nodes() {
        return Collections.unmodifiableList(nodes);
    }

    /**
     * Returns an unmodifiable view of the edges.
     *
     * @return unmodifiable list of edges
     */
    public List<DependencyEdge> edges() {
        return Collections.unmodifiableList(edges);
    }

    /**
     * Finds a node by release ID.
     *
     * @param id the release ID to search for
     * @return the node if found, empty otherwise
     */
    public Optional<DependencyNode> getNode(ReleaseId id) {
        return nodes.stream()
            .filter(n -> n.id().equals(id))
            .findFirst();
    }

    /**
     * Returns the number of nodes in the tree.
     *
     * @return node count
     */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * Returns the number of edges in the tree.
     *
     * @return edge count
     */
    public int edgeCount() {
        return edges.size();
    }

    /**
     * Checks if the tree is empty (only contains the root with no dependencies).
     *
     * @return true if there are no edges
     */
    public boolean isEmpty() {
        return edges.isEmpty();
    }

    /**
     * Checks if the tree contains a specific release.
     *
     * @param id the release ID to check
     * @return true if the release is in the tree
     */
    public boolean contains(ReleaseId id) {
        return getNode(id).isPresent();
    }
}

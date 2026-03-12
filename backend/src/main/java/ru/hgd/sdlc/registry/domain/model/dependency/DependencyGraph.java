package ru.hgd.sdlc.registry.domain.model.dependency;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable dependency graph for a release.
 * Represents the full transitive dependency tree.
 *
 * <p>Supports:
 * <ul>
 *   <li>Transitive dependency resolution</li>
 *   <li>Topological ordering for installation</li>
 *   <li>Cycle detection</li>
 * </ul>
 */
@JsonDeserialize(builder = DependencyGraph.DependencyGraphBuilder.class)
public final class DependencyGraph {

    private final ReleaseId root;
    private final Map<ReleaseId, Set<ReleaseId>> dependencies;

    /**
     * Creates a DependencyGraph with the given root and dependencies.
     *
     * @param root the root release
     * @param dependencies the dependency map
     */
    public DependencyGraph(ReleaseId root, Map<ReleaseId, Set<ReleaseId>> dependencies) {
        this.root = Objects.requireNonNull(root, "root cannot be null");
        this.dependencies = dependencies != null
            ? Collections.unmodifiableMap(new HashMap<>(dependencies))
            : Map.of();
    }

    /**
     * Creates a DependencyGraph with the given root and dependencies.
     * This factory method provides a fluent API.
     *
     * @param root the root release
     * @param dependencies the dependency map
     * @return a new DependencyGraph
     */
    public static DependencyGraph of(ReleaseId root, Map<ReleaseId, Set<ReleaseId>> dependencies) {
        return new DependencyGraph(root, dependencies);
    }

    /**
     * Returns the root release.
     */
    public ReleaseId root() {
        return root;
    }

    /**
     * Returns the dependency map.
     */
    public Map<ReleaseId, Set<ReleaseId>> dependencies() {
        return dependencies;
    }

    /**
     * Returns all transitive dependencies for the root.
     * This includes all dependencies, direct and indirect.
     *
     * @return set of all transitive dependencies (excluding the root)
     */
    public Set<ReleaseId> transitiveDependencies() {
        Set<ReleaseId> result = new HashSet<>();
        collectTransitive(root, result, new HashSet<>());
        result.remove(root);
        return Collections.unmodifiableSet(result);
    }

    /**
     * Returns transitive dependencies for a specific release.
     *
     * @param releaseId the release to get dependencies for
     * @return set of all transitive dependencies
     */
    public Set<ReleaseId> transitiveDependenciesFor(ReleaseId releaseId) {
        Set<ReleaseId> result = new HashSet<>();
        collectTransitive(releaseId, result, new HashSet<>());
        result.remove(releaseId);
        return Collections.unmodifiableSet(result);
    }

    private void collectTransitive(ReleaseId release, Set<ReleaseId> result, Set<ReleaseId> visiting) {
        // Check for cycle FIRST - if we're visiting a node that's already in the current path
        if (visiting.contains(release)) {
            throw new IllegalStateException("Circular dependency detected involving: " + release.canonicalId());
        }
        // Then check if already processed - skip if so
        if (result.contains(release)) {
            return;
        }

        visiting.add(release);
        result.add(release);

        Set<ReleaseId> directDeps = dependencies.get(release);
        if (directDeps != null) {
            for (ReleaseId dep : directDeps) {
                collectTransitive(dep, result, visiting);
            }
        }

        visiting.remove(release);
    }

    /**
     * Returns releases in topological order (dependencies first).
     * This is the order in which releases should be installed.
     *
     * @return list of releases in dependency order
     * @throws IllegalStateException if a cycle is detected
     */
    public List<ReleaseId> topologicalOrder() {
        List<ReleaseId> result = new ArrayList<>();
        Set<ReleaseId> visited = new HashSet<>();
        Set<ReleaseId> visiting = new HashSet<>();

        // Start from root and traverse
        visitTopological(root, result, visited, visiting);

        // Post-order DFS naturally gives dependencies first (children before parent)
        return Collections.unmodifiableList(result);
    }

    private void visitTopological(ReleaseId release, List<ReleaseId> result,
                                   Set<ReleaseId> visited, Set<ReleaseId> visiting) {
        if (visited.contains(release)) {
            return;
        }
        if (visiting.contains(release)) {
            throw new IllegalStateException("Circular dependency detected involving: " + release.canonicalId());
        }

        visiting.add(release);

        Set<ReleaseId> directDeps = dependencies.get(release);
        if (directDeps != null) {
            for (ReleaseId dep : directDeps) {
                visitTopological(dep, result, visited, visiting);
            }
        }

        visiting.remove(release);
        visited.add(release);
        result.add(release);
    }

    /**
     * Returns the direct dependencies for a release.
     *
     * @param releaseId the release to get dependencies for
     * @return set of direct dependencies (empty if none)
     */
    public Set<ReleaseId> directDependenciesOf(ReleaseId releaseId) {
        Set<ReleaseId> deps = dependencies.get(releaseId);
        return deps != null ? Collections.unmodifiableSet(deps) : Set.of();
    }

    /**
     * Checks if the graph contains a cycle.
     *
     * @return true if a cycle exists
     */
    public boolean hasCycle() {
        try {
            topologicalOrder();
            return false;
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /**
     * Returns the total number of unique releases in the graph (including root).
     */
    public int size() {
        Set<ReleaseId> all = new HashSet<>();
        all.add(root);
        all.addAll(transitiveDependencies());
        return all.size();
    }

    /**
     * Returns the number of nodes in the graph.
     * Alias for size().
     */
    public int nodeCount() {
        return size();
    }

    /**
     * Returns the number of edges in the graph.
     */
    public int edgeCount() {
        int count = 0;
        for (Set<ReleaseId> deps : dependencies.values()) {
            count += deps.size();
        }
        return count;
    }

    /**
     * Checks if the graph is empty (root has no dependencies).
     */
    public boolean isEmpty() {
        Set<ReleaseId> deps = dependencies.get(root);
        return deps == null || deps.isEmpty();
    }

    /**
     * Creates an empty dependency graph.
     */
    public static DependencyGraph empty() {
        return new DependencyGraph(
            ReleaseId.of(
                ru.hgd.sdlc.compiler.domain.model.authored.FlowId.of("empty"),
                ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion.of("0.0.0")
            ),
            Map.of()
        );
    }

    /**
     * Adds a dependency relationship to the graph.
     *
     * @param from the dependent release
     * @param to the dependency release
     * @return a new graph with the added dependency
     */
    public DependencyGraph addDependency(ReleaseId from, ReleaseId to) {
        Map<ReleaseId, Set<ReleaseId>> newDeps = new HashMap<>(dependencies);
        Set<ReleaseId> deps = new HashSet<>(newDeps.getOrDefault(from, Set.of()));
        deps.add(to);
        newDeps.put(from, deps);
        return new DependencyGraph(root, newDeps);
    }

    /**
     * Adds a node to the graph.
     *
     * @param node the node to add
     * @return a new graph with the added node
     */
    public DependencyGraph addNode(ReleaseId node) {
        if (dependencies.containsKey(node)) {
            return this;
        }
        Map<ReleaseId, Set<ReleaseId>> newDeps = new HashMap<>(dependencies);
        newDeps.putIfAbsent(node, Set.of());
        return new DependencyGraph(root, newDeps);
    }

    /**
     * Creates a builder for constructing DependencyGraph instances.
     */
    public static DependencyGraphBuilder builder() {
        return new DependencyGraphBuilder();
    }

    /**
     * Creates a builder initialized with this graph's values.
     */
    public DependencyGraphBuilder toBuilder() {
        return new DependencyGraphBuilder().root(root).dependencies(dependencies);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DependencyGraph that = (DependencyGraph) o;
        return Objects.equals(root, that.root)
            && Objects.equals(dependencies, that.dependencies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(root, dependencies);
    }

    @Override
    public String toString() {
        return "DependencyGraph{" +
            "root=" + root +
            ", dependencies=" + dependencies +
            '}';
    }

    /**
     * Builder for DependencyGraph.
     */
    @JsonPOJOBuilder(withPrefix = "")
    public static class DependencyGraphBuilder {
        private ReleaseId root;
        private Map<ReleaseId, Set<ReleaseId>> dependencies = new HashMap<>();

        public DependencyGraphBuilder root(ReleaseId root) {
            this.root = root;
            return this;
        }

        public DependencyGraphBuilder dependencies(Map<ReleaseId, Set<ReleaseId>> dependencies) {
            // Create new map to avoid reusing the same instance across multiple builds
            this.dependencies = new HashMap<>();
            if (dependencies != null) {
                this.dependencies.putAll(dependencies);
            }
            return this;
        }

        public DependencyGraphBuilder dependency(ReleaseId key, Set<ReleaseId> value) {
            this.dependencies.put(key, value);
            return this;
        }

        public DependencyGraph build() {
            return new DependencyGraph(root, dependencies);
        }
    }
}

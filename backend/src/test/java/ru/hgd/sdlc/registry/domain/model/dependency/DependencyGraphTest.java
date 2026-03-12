package ru.hgd.sdlc.registry.domain.model.dependency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DependencyGraph")
class DependencyGraphTest {

    private static ReleaseId releaseId(String flowId, String version) {
        return ReleaseId.of(FlowId.of(flowId), ReleaseVersion.of(version));
    }

    @Nested
    @DisplayName("builder")
    class Builder {

        @Test
        @DisplayName("should build graph with root and dependencies")
        void shouldBuildWithRootAndDependencies() {
            ReleaseId root = releaseId("main-flow", "1.0.0");
            ReleaseId dep1 = releaseId("skill-a", "1.0.0");

            DependencyGraph graph = DependencyGraph.of(root, Map.of(root, Set.of(dep1)));

            assertEquals(root, graph.root());
            assertTrue(graph.directDependenciesOf(root).contains(dep1));
        }
    }

    @Nested
    @DisplayName("transitiveDependencies()")
    class TransitiveDependencies {

        @Test
        @DisplayName("should return all transitive dependencies")
        void shouldReturnAllTransitive() {
            ReleaseId root = releaseId("main-flow", "1.0.0");
            ReleaseId dep1 = releaseId("skill-a", "1.0.0");
            ReleaseId dep2 = releaseId("skill-b", "1.0.0");

            DependencyGraph graph = DependencyGraph.of(root, Map.of(
                root, Set.of(dep1, dep2),
                dep1, Set.of(),
                dep2, Set.of()
            ));

            Set<ReleaseId> transitive = graph.transitiveDependencies();

            assertEquals(2, transitive.size());
            assertTrue(transitive.contains(dep1));
            assertTrue(transitive.contains(dep2));
            assertFalse(transitive.contains(root));
        }

        @Test
        @DisplayName("should resolve nested dependencies")
        void shouldResolveNestedDependencies() {
            ReleaseId root = releaseId("main-flow", "1.0.0");
            ReleaseId dep1 = releaseId("skill-a", "1.0.0");
            ReleaseId dep2 = releaseId("skill-b", "1.0.0");

            // root -> dep1 -> dep2
            DependencyGraph graph = DependencyGraph.of(root, Map.of(
                root, Set.of(dep1),
                dep1, Set.of(dep2),
                dep2, Set.of()
            ));

            Set<ReleaseId> transitive = graph.transitiveDependencies();

            assertEquals(2, transitive.size());
            assertTrue(transitive.contains(dep1));
            assertTrue(transitive.contains(dep2));
        }

        @Test
        @DisplayName("should detect circular dependencies")
        void shouldDetectCircularDependencies() {
            ReleaseId root = releaseId("main-flow", "1.0.0");
            ReleaseId dep1 = releaseId("skill-a", "1.0.0");
            ReleaseId dep2 = releaseId("skill-b", "1.0.0");

            // root -> dep1 -> dep2 -> dep1 (cycle!)
            DependencyGraph graph = DependencyGraph.of(root, Map.of(
                root, Set.of(dep1),
                dep1, Set.of(dep2),
                dep2, Set.of(dep1)
            ));

            assertThrows(IllegalStateException.class, graph::transitiveDependencies);
        }
    }

    @Nested
    @DisplayName("topologicalOrder()")
    class TopologicalOrder {

        @Test
        @DisplayName("should return dependencies first")
        void shouldReturnDependenciesFirst() {
            ReleaseId root = releaseId("main-flow", "1.0.0");
            ReleaseId dep1 = releaseId("skill-a", "1.0.0");
            ReleaseId dep2 = releaseId("skill-b", "1.0.0");

            // root -> dep1, dep2
            DependencyGraph graph = DependencyGraph.of(root, Map.of(
                root, Set.of(dep1, dep2),
                dep1, Set.of(),
                dep2, Set.of()
            ));

            List<ReleaseId> order = graph.topologicalOrder();

            // Dependencies should come before root
            int rootIndex = order.indexOf(root);
            int dep1Index = order.indexOf(dep1);
            int dep2Index = order.indexOf(dep2);

            assertTrue(rootIndex >= 0 && dep1Index >= 0 && dep2Index >= 0,
                "All nodes should be in the order. Got: " + order);
            assertTrue(dep1Index < rootIndex,
                "dep1 should come before root. Order: " + order);
            assertTrue(dep2Index < rootIndex,
                "dep2 should come before root. Order: " + order);
            assertEquals(3, order.size());
        }

        @Test
        @DisplayName("should order nested dependencies correctly")
        void shouldOrderNestedDependenciesCorrectly() {
            ReleaseId root = releaseId("main-flow", "1.0.0");
            ReleaseId dep1 = releaseId("skill-a", "1.0.0");
            ReleaseId dep2 = releaseId("skill-b", "1.0.0");

            // root -> dep1 -> dep2
            DependencyGraph graph = DependencyGraph.of(root, Map.of(
                root, Set.of(dep1),
                dep1, Set.of(dep2),
                dep2, Set.of()
            ));

            List<ReleaseId> order = graph.topologicalOrder();

            // Order should be: dep2, dep1, root (dependencies first)
            assertEquals(3, order.size());
            int dep2Index = order.indexOf(dep2);
            int dep1Index = order.indexOf(dep1);
            int rootIndex = order.indexOf(root);

            assertTrue(dep2Index >= 0 && dep1Index >= 0 && rootIndex >= 0,
                "All nodes should be present. Got: " + order);
            assertTrue(dep2Index < dep1Index,
                "dep2 should come before dep1. Order: " + order);
            assertTrue(dep1Index < rootIndex,
                "dep1 should come before root. Order: " + order);
        }

        @Test
        @DisplayName("should detect cycle in topological sort")
        void shouldDetectCycle() {
            ReleaseId root = releaseId("main-flow", "1.0.0");
            ReleaseId dep1 = releaseId("skill-a", "1.0.0");
            ReleaseId dep2 = releaseId("skill-b", "1.0.0");

            // root -> dep1 -> dep2 -> dep1 (cycle!)
            DependencyGraph graph = DependencyGraph.of(root, Map.of(
                root, Set.of(dep1),
                dep1, Set.of(dep2),
                dep2, Set.of(dep1)
            ));

            assertThrows(IllegalStateException.class, graph::topologicalOrder);
        }
    }

    @Nested
    @DisplayName("hasCycle()")
    class HasCycle {

        @Test
        @DisplayName("should return false for acyclic graph")
        void shouldReturnFalseForAcyclic() {
            ReleaseId root = releaseId("main-flow", "1.0.0");
            ReleaseId dep = releaseId("skill-a", "1.0.0");

            DependencyGraph graph = DependencyGraph.of(root, Map.of(
                root, Set.of(dep),
                dep, Set.of()
            ));

            assertFalse(graph.hasCycle());
        }

        @Test
        @DisplayName("should return true for cyclic graph")
        void shouldReturnTrueForCyclic() {
            ReleaseId root = releaseId("main-flow", "1.0.0");
            ReleaseId dep1 = releaseId("skill-a", "1.0.0");
            ReleaseId dep2 = releaseId("skill-b", "1.0.0");

            DependencyGraph graph = DependencyGraph.of(root, Map.of(
                root, Set.of(dep1),
                dep1, Set.of(dep2),
                dep2, Set.of(dep1)
            ));

            assertTrue(graph.hasCycle());
        }
    }

    @Nested
    @DisplayName("directDependenciesOf(ReleaseId)")
    class DirectDependenciesOf {

        @Test
        @DisplayName("should return direct dependencies")
        void shouldReturnDirectDependencies() {
            ReleaseId root = releaseId("main-flow", "1.0.0");
            ReleaseId dep1 = releaseId("skill-a", "1.0.0");
            ReleaseId dep2 = releaseId("skill-b", "1.0.0");

            DependencyGraph graph = DependencyGraph.of(root, Map.of(
                root, Set.of(dep1, dep2)
            ));

            Set<ReleaseId> deps = graph.directDependenciesOf(root);

            assertEquals(2, deps.size());
            assertTrue(deps.contains(dep1));
            assertTrue(deps.contains(dep2));
        }

        @Test
        @DisplayName("should return empty set for unknown release")
        void shouldReturnEmptyForUnknown() {
            ReleaseId root = releaseId("main-flow", "1.0.0");
            ReleaseId unknown = releaseId("unknown", "1.0.0");

            DependencyGraph graph = DependencyGraph.builder()
                .root(root)
                .build();

            Set<ReleaseId> deps = graph.directDependenciesOf(unknown);

            assertTrue(deps.isEmpty());
        }
    }

    @Nested
    @DisplayName("size()")
    class Size {

        @Test
        @DisplayName("should return total unique releases")
        void shouldReturnTotalUnique() {
            ReleaseId root = releaseId("main-flow", "1.0.0");
            ReleaseId dep1 = releaseId("skill-a", "1.0.0");
            ReleaseId dep2 = releaseId("skill-b", "1.0.0");

            DependencyGraph graph = DependencyGraph.of(root, Map.of(
                root, Set.of(dep1, dep2)
            ));

            assertEquals(3, graph.size());
        }

        @Test
        @DisplayName("should return 1 for empty graph")
        void shouldReturnOneForEmpty() {
            ReleaseId root = releaseId("main-flow", "1.0.0");

            DependencyGraph graph = DependencyGraph.of(root, Map.of());

            assertEquals(1, graph.size());
        }
    }

    @Nested
    @DisplayName("isEmpty()")
    class IsEmpty {

        @Test
        @DisplayName("should return true for no dependencies")
        void shouldReturnTrueForNoDependencies() {
            ReleaseId root = releaseId("main-flow", "1.0.0");

            DependencyGraph graph = DependencyGraph.builder()
                .root(root)
                .build();

            assertTrue(graph.isEmpty());
        }

        @Test
        @DisplayName("should return false for dependencies")
        void shouldReturnFalseForDependencies() {
            ReleaseId root = releaseId("main-flow", "1.0.0");
            ReleaseId dep = releaseId("skill-a", "1.0.0");

            DependencyGraph graph = DependencyGraph.of(root, Map.of(
                root, Set.of(dep)
            ));

            assertFalse(graph.isEmpty());
        }
    }
}

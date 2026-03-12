package ru.hgd.sdlc.registry.application.query;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.registry.application.resolver.PackageResolver;
import ru.hgd.sdlc.registry.application.resolver.ReleaseNotFoundException;
import ru.hgd.sdlc.registry.domain.model.dependency.DependencyGraph;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseMetadata;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;
import ru.hgd.sdlc.registry.domain.repository.ReleaseRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of ReleaseQueryService.
 * Queries the release repository and package resolver to provide
 * read-only access to release information.
 */
@Service
@ConditionalOnBean({ReleaseRepository.class, PackageResolver.class})
public class DefaultReleaseQueryService implements ReleaseQueryService {

    private final ReleaseRepository releaseRepository;
    private final PackageResolver packageResolver;

    /**
     * Creates a new DefaultReleaseQueryService.
     *
     * @param releaseRepository the release repository for metadata queries
     * @param packageResolver the package resolver for dependency resolution
     */
    public DefaultReleaseQueryService(ReleaseRepository releaseRepository, PackageResolver packageResolver) {
        this.releaseRepository = releaseRepository;
        this.packageResolver = packageResolver;
    }

    @Override
    public Optional<ReleasePackage> findById(ReleaseId id) {
        try {
            ReleasePackage pkg = packageResolver.resolveExact(id);
            return Optional.ofNullable(pkg);
        } catch (ReleaseNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ReleasePackage> findLatest(String flowId) {
        return releaseRepository.findLatestVersion(flowId)
            .flatMap(record -> findById(record.releaseId()));
    }

    @Override
    public List<ReleaseMetadata> listVersions(String flowId) {
        return releaseRepository.listVersions(flowId)
            .stream()
            .map(record -> {
                try {
                    ReleasePackage pkg = packageResolver.resolveExact(record.releaseId());
                    return pkg.metadata();
                } catch (ReleaseNotFoundException e) {
                    return null;
                }
            })
            .filter(metadata -> metadata != null)
            .collect(Collectors.toList());
    }

    @Override
    public List<ReleaseMetadata> search(ReleaseQuery query) {
        // Search requires a ReleaseRepository implementation with full-text search support
        // Until the repository supports search queries, throw UnsupportedOperationException
        throw new UnsupportedOperationException(
            "Release search is not yet supported. " +
            "This feature requires ReleaseRepository implementation with search capabilities. " +
            "Query: text=" + query.text() + ", author=" + query.author()
        );
    }

    @Override
    public Optional<DependencyTree> getDependencyTree(ReleaseId id) {
        try {
            // Resolve the root package
            ReleasePackage rootPackage = packageResolver.resolveExact(id);
            if (rootPackage == null) {
                return Optional.empty();
            }

            // Resolve all dependencies
            List<ReleasePackage> packages = packageResolver.resolve(id);

            // Build nodes from resolved packages
            List<DependencyNode> nodes = new ArrayList<>();
            for (ReleasePackage pkg : packages) {
                String type = determineType(pkg);
                String name = pkg.metadata().displayName();
                nodes.add(DependencyNode.builder()
                    .id(pkg.id())
                    .type(type)
                    .name(name)
                    .build());
            }

            // Build edges from the dependency graph
            List<DependencyEdge> edges = new ArrayList<>();
            DependencyGraph graph = buildDependencyGraph(packages);
            for (ReleaseId from : graph.dependencies().keySet()) {
                Set<ReleaseId> deps = graph.dependencies().get(from);
                for (ReleaseId to : deps) {
                    edges.add(DependencyEdge.of(from, to));
                }
            }

            DependencyTree tree = DependencyTree.builder()
                .root(id)
                .nodes(nodes)
                .edges(edges)
                .build();

            return Optional.of(tree);
        } catch (ReleaseNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Checks if metadata matches the search text.
     *
     * @param metadata the release metadata
     * @param searchText the lowercase search text
     * @return true if there's a match
     */
    private boolean matchesText(ReleaseMetadata metadata, String searchText) {
        if (metadata.displayName().toLowerCase(Locale.ROOT).contains(searchText)) {
            return true;
        }
        if (metadata.description().isPresent() &&
            metadata.description().get().toLowerCase(Locale.ROOT).contains(searchText)) {
            return true;
        }
        return false;
    }

    /**
     * Checks if metadata matches the date range.
     *
     * @param metadata the release metadata
     * @param after the after date (nullable)
     * @param before the before date (nullable)
     * @return true if there's a match
     */
    private boolean matchesDateRange(ReleaseMetadata metadata, Instant after, Instant before) {
        Instant createdAt = metadata.createdAt();

        if (after != null && createdAt.isBefore(after)) {
            return false;
        }

        if (before != null && createdAt.isAfter(before)) {
            return false;
        }

        return true;
    }

    /**
     * Determines the type of a release package.
     *
     * @param pkg the release package
     * @return "flow" or "skill"
     */
    private String determineType(ReleasePackage pkg) {
        // For now, all packages are flows
        // In the future, skill packages would return "skill"
        return "flow";
    }

    /**
     * Builds a dependency graph from resolved packages.
     *
     * @param packages the resolved packages in topological order
     * @return the dependency graph
     */
    private DependencyGraph buildDependencyGraph(List<ReleasePackage> packages) {
        if (packages.isEmpty()) {
            return DependencyGraph.builder().build();
        }

        ReleaseId root = packages.get(packages.size() - 1).id();

        // Build dependency edges
        // In a proper implementation, this would be extracted from the resolved skills
        // For now, we create a simple linear dependency chain based on topological order
        java.util.Map<ReleaseId, Set<ReleaseId>> dependencies = new java.util.HashMap<>();

        for (int i = 0; i < packages.size(); i++) {
            ReleasePackage pkg = packages.get(i);
            Set<ReleaseId> deps = new java.util.HashSet<>();

            // Add dependencies for packages earlier in topological order
            for (int j = 0; j < i; j++) {
                deps.add(packages.get(j).id());
            }

            dependencies.put(pkg.id(), deps);
        }

        return DependencyGraph.of(root, dependencies);
    }
}

package ru.hgd.sdlc.registry.application.resolver;

import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.registry.domain.model.dependency.DependencyGraph;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;
import ru.hgd.sdlc.registry.domain.repository.ReleaseRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of PackageResolver.
 * Resolves flow and skill dependencies using exact version matching.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Fetch flow release package</li>
 *   <li>Extract skill dependencies from resolvedSkills in IR</li>
 *   <li>Check if skills are bundled in the package</li>
 *   <li>For external skills, look up in registry by checksum</li>
 *   <li>Resolve each external skill recursively</li>
 *   <li>Detect version conflicts (same skill ID, different versions)</li>
 *   <li>Build dependency graph</li>
 *   <li>Return packages in topological order (dependencies first)</li>
 * </ol>
 *
 * <p>Diamond dependencies are handled by requiring all references to a skill
 * to have the same version. If different versions are required, a
 * VersionConflictException is thrown.
 */
public class DefaultPackageResolver implements PackageResolver {

    private final ReleaseRepository releaseRepository;
    private final PackageReader packageReader;

    /**
     * Creates a new DefaultPackageResolver.
     *
     * @param releaseRepository the release repository for metadata lookups
     * @param packageReader the package reader for loading full packages
     */
    public DefaultPackageResolver(ReleaseRepository releaseRepository, PackageReader packageReader) {
        this.releaseRepository = releaseRepository;
        this.packageReader = packageReader;
    }

    @Override
    public List<ReleasePackage> resolve(ReleaseId flowId)
            throws ReleaseNotFoundException, VersionConflictException {

        // Track all resolved packages by release ID
        Map<ReleaseId, ReleasePackage> resolved = new LinkedHashMap<>();

        // Track skill ID -> release ID mapping for conflict detection
        // This ensures the same skill version is used throughout
        Map<SkillId, ReleaseId> skillVersions = new HashMap<>();

        // Track dependency edges for graph building
        Map<ReleaseId, Set<ReleaseId>> dependencyEdges = new HashMap<>();

        // Track bundled skills (skills included in packages, not separate releases)
        Set<SkillId> bundledSkills = new HashSet<>();

        // Resolve recursively starting from the flow
        resolveRecursive(flowId, resolved, skillVersions, dependencyEdges, bundledSkills, new HashSet<>());

        // Build dependency graph
        DependencyGraph graph = DependencyGraph.of(flowId, dependencyEdges);

        // Get topological order and collect packages
        List<ReleaseId> topologicalOrder = graph.topologicalOrder();
        List<ReleasePackage> result = new ArrayList<>(topologicalOrder.size());

        for (ReleaseId id : topologicalOrder) {
            result.add(resolved.get(id));
        }

        return result;
    }

    private void resolveRecursive(
            ReleaseId releaseId,
            Map<ReleaseId, ReleasePackage> resolved,
            Map<SkillId, ReleaseId> skillVersions,
            Map<ReleaseId, Set<ReleaseId>> dependencyEdges,
            Set<SkillId> bundledSkills,
            Set<ReleaseId> visiting) throws ReleaseNotFoundException, VersionConflictException {

        // Check for circular dependency
        if (visiting.contains(releaseId)) {
            throw new IllegalStateException("Circular dependency detected: " + releaseId.canonicalId());
        }

        // Skip if already resolved
        if (resolved.containsKey(releaseId)) {
            return;
        }

        // Mark as visiting
        visiting.add(releaseId);

        // Load the package
        ReleasePackage pkg = packageReader.read(releaseId);
        if (pkg == null) {
            throw new ReleaseNotFoundException(releaseId);
        }

        // Initialize dependency set for this release
        Set<ReleaseId> dependencies = new HashSet<>();
        dependencyEdges.put(releaseId, dependencies);

        // Register bundled skills (skills included in this package)
        for (SkillId bundledSkillId : pkg.skills().keySet()) {
            bundledSkills.add(bundledSkillId);
            // Bundled skills are associated with this release
            skillVersions.put(bundledSkillId, releaseId);
        }

        // Extract skill dependencies from the flow IR
        Map<SkillId, ru.hgd.sdlc.shared.hashing.Sha256> resolvedSkills = pkg.flowIr().resolvedSkills();

        for (Map.Entry<SkillId, ru.hgd.sdlc.shared.hashing.Sha256> entry : resolvedSkills.entrySet()) {
            SkillId skillId = entry.getKey();

            // Check if this skill is bundled in the current package
            if (pkg.skills().containsKey(skillId)) {
                // Skill is bundled, no need to resolve externally
                continue;
            }

            // Check if this skill is bundled in another resolved package
            if (bundledSkills.contains(skillId)) {
                // Already bundled elsewhere, use that version
                ReleaseId bundledIn = skillVersions.get(skillId);
                if (bundledIn != null && !bundledIn.equals(releaseId)) {
                    dependencies.add(bundledIn);
                }
                continue;
            }

            // External skill - look up in registry by checksum
            ReleaseId skillReleaseId = findSkillRelease(skillId, entry.getValue());

            if (skillReleaseId == null) {
                throw new ReleaseNotFoundException(releaseId,
                    "Skill not found: " + skillId.value() + " required by " + releaseId.canonicalId());
            }

            // Check for version conflict (diamond dependency)
            ReleaseId existingVersion = skillVersions.get(skillId);
            if (existingVersion != null && !existingVersion.equals(skillReleaseId)) {
                // Version conflict detected - different versions of same skill required
                Set<ReleaseId> conflicts = new HashSet<>();
                conflicts.add(existingVersion);
                conflicts.add(skillReleaseId);
                throw new VersionConflictException(conflicts,
                    "Skill " + skillId.value() + " has conflicting versions: " +
                    existingVersion.canonicalId() + " vs " + skillReleaseId.canonicalId());
            }

            // Track the skill version
            skillVersions.put(skillId, skillReleaseId);
            dependencies.add(skillReleaseId);

            // Resolve skill dependencies recursively
            resolveRecursive(skillReleaseId, resolved, skillVersions, dependencyEdges, bundledSkills, visiting);
        }

        // Store the resolved package
        resolved.put(releaseId, pkg);

        // Mark as done visiting
        visiting.remove(releaseId);
    }

    /**
     * Finds a skill release by skill ID and checksum.
     * Queries the registry for a skill matching the given checksum.
     *
     * @param skillId the skill ID
     * @param checksum the expected IR checksum
     * @return the release ID containing this skill, or null if not found
     */
    private ReleaseId findSkillRelease(SkillId skillId, ru.hgd.sdlc.shared.hashing.Sha256 checksum) {
        // Query the repository for a skill with this ID and checksum
        // The repository should be able to find releases by skill content hash
        return releaseRepository.findLatestVersion(skillId.value())
            .filter(record -> matchesSkillChecksum(record, checksum))
            .map(record -> record.releaseId())
            .orElse(null);
    }

    /**
     * Checks if a release record matches the expected skill checksum.
     *
     * <p>NOTE: This method currently always returns false because the ReleaseRecord
     * does not contain skill-level checksums, only package-level hash.
     * Full implementation requires storage layer support for skill checksums.
     *
     * @param record the release record (unused until storage supports skill checksums)
     * @param expectedChecksum the expected checksum
     * @return true if the checksum matches
     * @throws UnsupportedOperationException always, until storage layer is ready
     */
    private boolean matchesSkillChecksum(Object record, ru.hgd.sdlc.shared.hashing.Sha256 expectedChecksum) {
        // ReleaseRecord contains packageHash but not individual skill checksums
        // To properly match skill checksums, we need to either:
        // 1. Load the full package and check skill IR checksum
        // 2. Store skill checksums separately in the repository
        // Until the storage layer supports this, throw UnsupportedOperationException
        throw new UnsupportedOperationException(
            "Skill checksum matching not yet supported. " +
            "ReleaseRecord does not contain skill-level checksums. " +
            "Expected checksum: " + (expectedChecksum != null ? expectedChecksum.hexValue() : "null")
        );
    }

    @Override
    public ReleasePackage resolveExact(ReleaseId releaseId) throws ReleaseNotFoundException {
        ReleasePackage pkg = packageReader.read(releaseId);
        if (pkg == null) {
            throw new ReleaseNotFoundException(releaseId);
        }
        return pkg;
    }
}

package ru.hgd.sdlc.registry.application.lockfile;

import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;

import java.util.List;

/**
 * Generates lockfiles from resolved dependency trees.
 *
 * <p>The lockfile generator takes a list of resolved packages in
 * topological order and produces a lockfile that captures:
 * <ul>
 *   <li>All packages and their versions</li>
 *   <li>Checksums for integrity verification</li>
 *   <li>Dependency relationships</li>
 * </ul>
 */
public interface LockfileGenerator {

    /**
     * Generates a lockfile from resolved dependencies.
     *
     * <p>The packages must be in topological order (dependencies first),
     * as returned by the package resolver.
     *
     * @param packages the resolved packages in dependency order
     * @return the generated lockfile
     * @throws IllegalArgumentException if packages is null or empty
     */
    Lockfile generate(List<ReleasePackage> packages);
}

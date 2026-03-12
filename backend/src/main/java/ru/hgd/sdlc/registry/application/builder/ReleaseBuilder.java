package ru.hgd.sdlc.registry.application.builder;

import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.PhaseIr;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;

import java.util.Map;

/**
 * Interface for building release packages from compiled IR.
 *
 * <p>The ReleaseBuilder orchestrates the complete release build process:
 * <ul>
 *   <li>Validates input IR components</li>
 *   <li>Generates provenance with source information</li>
 *   <li>Builds the ReleasePackage</li>
 *   <li>Signs the package if a signer is configured</li>
 * </ul>
 *
 * <p>Implementations should be thread-safe and reusable.
 */
public interface ReleaseBuilder {

    /**
     * Builds a release package from compiled IR.
     *
     * @param flowIr the compiled flow IR (required)
     * @param phases map of phase IRs indexed by phase ID
     * @param skills map of skill IRs indexed by skill ID
     * @param sourceInfo source provenance info (git repo, commit, etc.)
     * @return the built release package
     * @throws ReleaseBuildException if build fails (e.g., null flow IR, invalid inputs)
     */
    ReleasePackage build(FlowIr flowIr,
                         Map<PhaseId, PhaseIr> phases,
                         Map<SkillId, SkillIr> skills,
                         SourceInfo sourceInfo) throws ReleaseBuildException;

    /**
     * Builds a release package with empty phases and skills.
     *
     * @param flowIr the compiled flow IR (required)
     * @param sourceInfo source provenance info
     * @return the built release package
     * @throws ReleaseBuildException if build fails
     */
    default ReleasePackage build(FlowIr flowIr, SourceInfo sourceInfo) throws ReleaseBuildException {
        return build(flowIr, Map.of(), Map.of(), sourceInfo);
    }
}

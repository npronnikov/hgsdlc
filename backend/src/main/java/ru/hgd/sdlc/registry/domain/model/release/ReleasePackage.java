package ru.hgd.sdlc.registry.domain.model.release;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.PhaseIr;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable release package - the aggregate root for releases.
 * Contains the compiled IR, provenance, and checksums for a single flow release.
 *
 * <p>This is the unit of distribution for the registry. All components
 * are immutable and content-addressed via checksums.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public final class ReleasePackage {

    /**
     * Unique identifier for this release (flowId + version).
     */
    @NonNull private final ReleaseId id;

    /**
     * Human-readable metadata about the release.
     */
    @NonNull private final ReleaseMetadata metadata;

    /**
     * The compiled flow IR - the executable representation.
     */
    @NonNull private final FlowIr flowIr;

    /**
     * Compiled phase IRs indexed by phase ID.
     */
    @Singular("phase")
    @NonNull private final Map<PhaseId, PhaseIr> phases;

    /**
     * Compiled skill IRs indexed by skill ID.
     * These are bundled skills that this flow depends on.
     */
    @Singular("skill")
    @NonNull private final Map<SkillId, SkillIr> skills;

    /**
     * Provenance record showing where and how this release was built.
     */
    @NonNull private final Provenance provenance;

    /**
     * Checksum manifest for all files in the package.
     */
    @NonNull private final ChecksumManifest checksums;

    /**
     * Returns an unmodifiable view of the phases.
     */
    public Map<PhaseId, PhaseIr> phases() {
        return Collections.unmodifiableMap(phases);
    }

    /**
     * Returns an unmodifiable view of the skills.
     */
    public Map<SkillId, SkillIr> skills() {
        return Collections.unmodifiableMap(skills);
    }

    /**
     * Returns a phase by ID.
     *
     * @param phaseId the phase ID
     * @return the phase IR if present
     */
    public Optional<PhaseIr> getPhase(PhaseId phaseId) {
        return Optional.ofNullable(phases.get(phaseId));
    }

    /**
     * Returns a skill by ID.
     *
     * @param skillId the skill ID
     * @return the skill IR if present
     */
    public Optional<SkillIr> getSkill(SkillId skillId) {
        return Optional.ofNullable(skills.get(skillId));
    }

    /**
     * Returns the number of phases in this package.
     */
    public int phaseCount() {
        return phases.size();
    }

    /**
     * Returns the number of bundled skills in this package.
     */
    public int skillCount() {
        return skills.size();
    }

    /**
     * Verifies the structural integrity of this package.
     * Checks that all required components are present and checksums exist.
     *
     * <p>Note: Full checksum verification requires the serialized form.
     * Use {@link ChecksumManifest#verifyAll(Map)} for complete verification.
     *
     * @return true if the package has valid structural integrity
     */
    public boolean verifyIntegrity() {
        // Check that checksum manifest is present and populated
        if (checksums == null || checksums.size() == 0) {
            return false;
        }

        // Check that flow IR has required metadata
        if (flowIr.metadata() == null) {
            return false;
        }

        // Check that provenance has required fields
        if (provenance.getReleaseId() == null ||
            provenance.getRepositoryUrl() == null ||
            provenance.getCommitSha() == null) {
            return false;
        }

        // Check that all phases have valid IDs
        for (PhaseIr phase : phases.values()) {
            if (phase.id() == null) {
                return false;
            }
        }

        // Check that all skills have valid IDs
        for (SkillIr skill : skills.values()) {
            if (skill.skillId() == null) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the flow ID from the release ID.
     */
    public ru.hgd.sdlc.compiler.domain.model.authored.FlowId flowId() {
        return id.flowId();
    }

    /**
     * Returns the version from the release ID.
     */
    public ReleaseVersion version() {
        return id.version();
    }
}

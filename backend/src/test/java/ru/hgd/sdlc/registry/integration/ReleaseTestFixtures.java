package ru.hgd.sdlc.registry.integration;

import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.compiler.domain.model.authored.HandlerRef;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;
import ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.IrMetadata;
import ru.hgd.sdlc.compiler.domain.model.ir.PhaseIr;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test fixtures for creating test flow and skill IRs.
 * Used in integration tests to create consistent test data.
 */
public final class ReleaseTestFixtures {

    private ReleaseTestFixtures() {
        // Utility class
    }

    /**
     * Creates a simple FlowIr with minimal configuration.
     *
     * @param flowId the flow ID
     * @param version the version
     * @return a new FlowIr instance
     */
    public static FlowIr createSimpleFlowIr(String flowId, String version) {
        Sha256 packageChecksum = Sha256.of("test-package-" + flowId + "-" + version);
        Sha256 irChecksum = Sha256.of("test-ir-" + flowId + "-" + version);

        return FlowIr.builder()
            .flowId(FlowId.of(flowId))
            .flowVersion(SemanticVersion.of(version))
            .metadata(IrMetadata.create(packageChecksum, irChecksum, "1.0.0"))
            .build();
    }

    /**
     * Creates a FlowIr with phases.
     *
     * @param flowId the flow ID
     * @param version the version
     * @param phaseNames list of phase names
     * @return a new FlowIr instance with phases
     */
    public static FlowIr createFlowIrWithPhases(String flowId, String version, List<String> phaseNames) {
        Sha256 packageChecksum = Sha256.of("test-package-" + flowId + "-" + version);
        Sha256 irChecksum = Sha256.of("test-ir-" + flowId + "-" + version);

        List<PhaseIr> phases = new java.util.ArrayList<>();
        for (int i = 0; i < phaseNames.size(); i++) {
            phases.add(PhaseIr.builder()
                .id(PhaseId.of(phaseNames.get(i)))
                .name(phaseNames.get(i) + " phase")
                .order(i)
                .build());
        }

        return FlowIr.builder()
            .flowId(FlowId.of(flowId))
            .flowVersion(SemanticVersion.of(version))
            .metadata(IrMetadata.create(packageChecksum, irChecksum, "1.0.0"))
            .phases(phases)
            .build();
    }

    /**
     * Creates a simple SkillIr with minimal configuration.
     *
     * @param skillId the skill ID
     * @param version the version
     * @return a new SkillIr instance
     */
    public static SkillIr createSimpleSkillIr(String skillId, String version) {
        return SkillIr.builder()
            .skillId(SkillId.of(skillId))
            .skillVersion(SemanticVersion.of(version))
            .name(skillId + " skill")
            .handler(HandlerRef.of("skill://" + skillId))
            .irChecksum(Sha256.of("skill-checksum-" + skillId + "-" + version))
            .compiledAt(Instant.now())
            .compilerVersion("1.0.0")
            .build();
    }

    /**
     * Creates a map of phases for release building.
     *
     * @param phaseNames list of phase names
     * @return map of PhaseId to PhaseIr
     */
    public static Map<PhaseId, PhaseIr> createPhasesMap(String... phaseNames) {
        Map<PhaseId, PhaseIr> phases = new HashMap<>();
        for (int i = 0; i < phaseNames.length; i++) {
            PhaseId id = PhaseId.of(phaseNames[i]);
            phases.put(id, PhaseIr.builder()
                .id(id)
                .name(phaseNames[i] + " phase")
                .order(i)
                .build());
        }
        return phases;
    }

    /**
     * Creates a map of skills for release building.
     *
     * @param skillIds list of skill IDs
     * @return map of SkillId to SkillIr
     */
    public static Map<SkillId, SkillIr> createSkillsMap(String... skillIds) {
        Map<SkillId, SkillIr> skills = new HashMap<>();
        for (String skillId : skillIds) {
            SkillId id = SkillId.of(skillId);
            skills.put(id, createSimpleSkillIr(skillId, "1.0.0"));
        }
        return skills;
    }

    /**
     * Creates a complete release package fixture with phases and skills.
     *
     * @param flowId the flow ID
     * @param version the version
     * @param phaseCount number of phases to include
     * @param skillCount number of skills to include
     * @return a FlowIr ready for release building
     */
    public static FlowIr createCompleteFlowIr(String flowId, String version, int phaseCount, int skillCount) {
        Sha256 packageChecksum = Sha256.of("test-package-" + flowId + "-" + version);
        Sha256 irChecksum = Sha256.of("test-ir-" + flowId + "-" + version);

        List<PhaseIr> phases = new java.util.ArrayList<>();
        for (int i = 0; i < phaseCount; i++) {
            phases.add(PhaseIr.builder()
                .id(PhaseId.of("phase-" + i))
                .name("Phase " + i)
                .order(i)
                .build());
        }

        // Create resolved skills map
        Map<SkillId, Sha256> resolvedSkills = new HashMap<>();
        for (int i = 0; i < skillCount; i++) {
            SkillId skillId = SkillId.of("skill-" + i);
            resolvedSkills.put(skillId, Sha256.of("skill-checksum-" + i));
        }

        return FlowIr.builder()
            .flowId(FlowId.of(flowId))
            .flowVersion(SemanticVersion.of(version))
            .metadata(IrMetadata.create(packageChecksum, irChecksum, "1.0.0"))
            .phases(phases)
            .resolvedSkills(resolvedSkills)
            .build();
    }
}

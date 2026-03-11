package ru.hgd.sdlc.compiler.domain.model.ir;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.compiler.domain.model.authored.ArtifactTemplateId;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.compiler.domain.model.authored.MarkdownBody;
import ru.hgd.sdlc.compiler.domain.model.authored.NodeId;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;
import ru.hgd.sdlc.compiler.domain.model.authored.ResumePolicy;
import ru.hgd.sdlc.compiler.domain.model.authored.Role;
import ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Compiled flow IR - the canonical executable representation.
 * This is what the runtime executes - never raw Markdown.
 *
 * <p>Per ADR-002: Runtime executes compiled IR, not Markdown.
 * This ensures deterministic execution and reproducible historical runs.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public class FlowIr {

    /**
     * Identity of the source flow.
     */
    @NonNull private final FlowId flowId;

    /**
     * Version of the source flow.
     */
    @NonNull private final SemanticVersion flowVersion;

    /**
     * IR metadata (checksums, version, timestamp).
     */
    @NonNull private final IrMetadata metadata;

    /**
     * Ordered list of phases.
     */
    @Builder.Default private final List<PhaseIr> phases = List.of();

    /**
     * Index of all nodes by ID for fast lookup.
     */
    @Builder.Default private final Map<NodeId, NodeIr> nodeIndex = Map.of();

    /**
     * All transitions in normalized form.
     */
    @Builder.Default private final List<TransitionIr> transitions = List.of();

    /**
     * Artifact contracts with resolved schemas.
     */
    @Builder.Default private final Map<ArtifactTemplateId, ArtifactContractIr> artifactContracts = Map.of();

    /**
     * Resolved skill references with checksums for integrity.
     */
    @Builder.Default private final Map<SkillId, Sha256> resolvedSkills = Map.of();

    /**
     * Policy hooks attached to this flow.
     */
    @Builder.Default private final List<PolicyHook> policyHooks = List.of();

    /**
     * Roles that can start this flow.
     */
    @Builder.Default private final Set<Role> startRoles = Set.of();

    /**
     * Resume policy for interrupted executions.
     */
    @Builder.Default private final ResumePolicy resumePolicy = ResumePolicy.FROM_CHECKPOINT;

    /**
     * Flow description (compiled from markdown).
     */
    private final MarkdownBody description;

    /**
     * IR schema version for evolution.
     */
    @Builder.Default private final int irSchemaVersion = IrMetadata.CURRENT_SCHEMA_VERSION;

    /**
     * Returns a node by ID.
     */
    public Optional<NodeIr> getNode(NodeId nodeId) {
        return Optional.ofNullable(nodeIndex.get(nodeId));
    }

    /**
     * Returns a phase by ID.
     */
    public Optional<PhaseIr> getPhase(PhaseId phaseId) {
        return phases.stream()
            .filter(p -> p.id().equals(phaseId))
            .findFirst();
    }

    /**
     * Returns an artifact contract by ID.
     */
    public Optional<ArtifactContractIr> getArtifactContract(ArtifactTemplateId id) {
        return Optional.ofNullable(artifactContracts.get(id));
    }

    /**
     * Returns a transition by index.
     */
    public Optional<TransitionIr> getTransition(int index) {
        if (index >= 0 && index < transitions.size()) {
            return Optional.of(transitions.get(index));
        }
        return Optional.empty();
    }

    /**
     * Returns total number of nodes.
     */
    public int totalNodes() {
        return nodeIndex.size();
    }

    /**
     * Returns total number of phases.
     */
    public int totalPhases() {
        return phases.size();
    }
}

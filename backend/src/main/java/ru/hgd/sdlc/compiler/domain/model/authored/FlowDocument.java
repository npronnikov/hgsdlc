package ru.hgd.sdlc.compiler.domain.model.authored;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.With;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a parsed Flow document from Markdown.
 * This is an authored model - the source of truth for flow definitions.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FlowDocument {

    @EqualsAndHashCode.Include
    @NonNull private final FlowId id;

    private final String name;

    @EqualsAndHashCode.Include
    @NonNull private final SemanticVersion version;

    @With @Builder.Default private final MarkdownBody description = MarkdownBody.of("");

    // Structure
    @Builder.Default private final List<PhaseId> phaseOrder = List.of();
    @Builder.Default private final Map<PhaseId, PhaseDocument> phases = Map.of();
    @Builder.Default private final Map<NodeId, NodeDocument> nodes = Map.of();

    // Execution hints
    @Builder.Default private final Set<Role> startRoles = Set.of();
    @Builder.Default private final ResumePolicy resumePolicy = ResumePolicy.FROM_CHECKPOINT;

    // Artifacts
    @Builder.Default private final Map<String, ArtifactTemplateDocument> artifacts = Map.of();

    // Metadata
    private final Instant authoredAt;
    private final String author;
    @Builder.Default private final Map<String, Object> extensions = Map.of();

    public PhaseDocument getPhase(PhaseId phaseId) {
        return phases.get(phaseId);
    }

    public NodeDocument getNode(NodeId nodeId) {
        return nodes.get(nodeId);
    }

    public ArtifactTemplateDocument getArtifact(String artifactId) {
        return artifacts.get(artifactId);
    }
}

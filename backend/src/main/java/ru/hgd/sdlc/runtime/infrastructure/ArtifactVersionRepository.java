package ru.hgd.sdlc.runtime.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.runtime.domain.ArtifactVersionEntity;

public interface ArtifactVersionRepository extends JpaRepository<ArtifactVersionEntity, UUID> {
    Optional<ArtifactVersionEntity> findByRunIdAndNodeIdAndArtifactKey(UUID runId, String nodeId, String artifactKey);

    Optional<ArtifactVersionEntity> findFirstByRunIdAndArtifactKeyOrderByCreatedAtDesc(UUID runId, String artifactKey);

    List<ArtifactVersionEntity> findByRunIdOrderByCreatedAtDesc(UUID runId);
}

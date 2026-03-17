package ru.hgd.sdlc.runtime.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.NodeExecutionStatus;

public interface NodeExecutionRepository extends JpaRepository<NodeExecutionEntity, UUID> {
    Optional<NodeExecutionEntity> findFirstByRunIdAndNodeIdOrderByAttemptNoDesc(UUID runId, String nodeId);

    List<NodeExecutionEntity> findByRunIdOrderByStartedAtAsc(UUID runId);

    Optional<NodeExecutionEntity> findByIdAndRunId(UUID id, UUID runId);

    List<NodeExecutionEntity> findByRunIdAndStatusInOrderByStartedAtDesc(
            UUID runId,
            List<NodeExecutionStatus> statuses
    );
}

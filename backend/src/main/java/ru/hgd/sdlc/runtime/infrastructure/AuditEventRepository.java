package ru.hgd.sdlc.runtime.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.runtime.domain.AuditEventEntity;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
    List<AuditEventEntity> findByRunIdOrderBySequenceNoAsc(UUID runId);

    Optional<AuditEventEntity> findByIdAndRunId(UUID id, UUID runId);

    Optional<AuditEventEntity> findFirstByRunIdOrderBySequenceNoDesc(UUID runId);
}

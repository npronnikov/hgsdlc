package ru.hgd.sdlc.runtime.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.hgd.sdlc.runtime.domain.AuditEventEntity;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
    List<AuditEventEntity> findByRunIdOrderBySequenceNoAsc(UUID runId);

    long countByEventTimeGreaterThanEqual(Instant eventTime);

    Optional<AuditEventEntity> findByIdAndRunId(UUID id, UUID runId);

    Optional<AuditEventEntity> findFirstByRunIdOrderBySequenceNoDesc(UUID runId);

    Optional<AuditEventEntity> findFirstByRunIdAndEventTypeOrderBySequenceNoDesc(UUID runId, String eventType);

    @Query("SELECT e FROM AuditEventEntity e WHERE e.runId = :runId"
            + " AND (:nodeExecutionId IS NULL OR e.nodeExecutionId = :nodeExecutionId)"
            + " AND (:eventType IS NULL OR e.eventType = :eventType)"
            + " AND (:actorType IS NULL OR e.actorType = :actorType)"
            + " AND (:cursor IS NULL OR e.sequenceNo < :cursor)"
            + " ORDER BY e.sequenceNo DESC")
    List<AuditEventEntity> queryFiltered(
            @Param("runId") UUID runId,
            @Param("nodeExecutionId") UUID nodeExecutionId,
            @Param("eventType") String eventType,
            @Param("actorType") ru.hgd.sdlc.runtime.domain.ActorType actorType,
            @Param("cursor") Long cursor,
            Pageable pageable
    );
}

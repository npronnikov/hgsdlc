package ru.hgd.sdlc.runtime.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.runtime.domain.GateInstanceEntity;
import ru.hgd.sdlc.runtime.domain.GateStatus;

public interface GateInstanceRepository extends JpaRepository<GateInstanceEntity, UUID> {
    Optional<GateInstanceEntity> findFirstByRunIdAndStatusInOrderByOpenedAtDesc(
            UUID runId,
            Collection<GateStatus> statuses
    );

    long countByStatusIn(Collection<GateStatus> statuses);

    List<GateInstanceEntity> findByStatusInOrderByOpenedAtAsc(Collection<GateStatus> statuses);

    List<GateInstanceEntity> findByRunIdOrderByOpenedAtDesc(UUID runId);
}

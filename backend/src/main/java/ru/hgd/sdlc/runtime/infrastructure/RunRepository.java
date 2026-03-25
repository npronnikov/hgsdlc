package ru.hgd.sdlc.runtime.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.domain.RunStatus;

public interface RunRepository extends JpaRepository<RunEntity, UUID> {
    List<RunEntity> findAllByOrderByCreatedAtDesc();

    long countByStatusIn(Collection<RunStatus> statuses);

    boolean existsByProjectIdAndTargetBranchAndStatusIn(
            UUID projectId,
            String targetBranch,
            Collection<RunStatus> statuses
    );

    List<RunEntity> findByStatusInOrderByCreatedAtAsc(Collection<RunStatus> statuses);

    List<RunEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}

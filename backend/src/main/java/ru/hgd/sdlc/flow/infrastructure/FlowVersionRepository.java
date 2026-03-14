package ru.hgd.sdlc.flow.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.flow.domain.FlowStatus;
import ru.hgd.sdlc.flow.domain.FlowVersion;

public interface FlowVersionRepository extends JpaRepository<FlowVersion, UUID> {
    Optional<FlowVersion> findFirstByFlowIdAndStatusOrderBySavedAtDesc(String flowId, FlowStatus status);

    Optional<FlowVersion> findFirstByFlowIdAndVersionOrderBySavedAtDesc(String flowId, String version);

    List<FlowVersion> findByFlowIdOrderBySavedAtDesc(String flowId);

    List<FlowVersion> findAllByOrderBySavedAtDesc();
}

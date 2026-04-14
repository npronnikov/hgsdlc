package ru.hgd.sdlc.runtime.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.runtime.domain.GateChatMessageEntity;

public interface GateChatMessageRepository extends JpaRepository<GateChatMessageEntity, UUID> {
    List<GateChatMessageEntity> findByGateIdOrderByCreatedAtAsc(UUID gateId);
}

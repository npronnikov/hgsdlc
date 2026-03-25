package ru.hgd.sdlc.publication.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.publication.domain.PublicationEntityType;
import ru.hgd.sdlc.publication.domain.PublicationRequest;
import ru.hgd.sdlc.publication.domain.PublicationStatus;

public interface PublicationRequestRepository extends JpaRepository<PublicationRequest, UUID> {
    Optional<PublicationRequest> findByEntityTypeAndEntityIdAndVersion(PublicationEntityType entityType, String entityId, String version);

    List<PublicationRequest> findByStatusOrderByCreatedAtDesc(PublicationStatus status);

    List<PublicationRequest> findAllByOrderByCreatedAtDesc();
}

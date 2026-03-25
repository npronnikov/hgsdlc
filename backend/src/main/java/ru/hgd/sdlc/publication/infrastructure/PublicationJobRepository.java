package ru.hgd.sdlc.publication.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.publication.domain.PublicationJob;
import ru.hgd.sdlc.publication.domain.PublicationJobStatus;

public interface PublicationJobRepository extends JpaRepository<PublicationJob, UUID> {
    List<PublicationJob> findByStatusOrderByCreatedAtDesc(PublicationJobStatus status);

    List<PublicationJob> findAllByOrderByCreatedAtDesc();

    List<PublicationJob> findByRequestIdOrderByCreatedAtDesc(UUID requestId);
}

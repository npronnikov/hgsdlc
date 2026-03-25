package ru.hgd.sdlc.publication.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.publication.domain.PublicationApproval;

public interface PublicationApprovalRepository extends JpaRepository<PublicationApproval, UUID> {
    List<PublicationApproval> findByRequestIdOrderByCreatedAtDesc(UUID requestId);
}

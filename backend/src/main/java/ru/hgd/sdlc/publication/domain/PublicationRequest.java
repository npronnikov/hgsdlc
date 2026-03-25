package ru.hgd.sdlc.publication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "publication_requests")
public class PublicationRequest {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 32)
    private PublicationEntityType entityType;

    @Column(name = "entity_id", nullable = false, length = 255)
    private String entityId;

    @Column(nullable = false, length = 64)
    private String version;

    @Column(name = "canonical_name", nullable = false, length = 255)
    private String canonicalName;

    @Column(nullable = false, length = 128)
    private String author;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_target", nullable = false, length = 32)
    private PublicationTarget requestedTarget;

    @Column(name = "requested_mode", nullable = false, length = 32)
    private String requestedMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PublicationStatus status;

    @Column(name = "approval_count", nullable = false)
    private int approvalCount;

    @Column(name = "required_approvals", nullable = false)
    private int requiredApprovals;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
}

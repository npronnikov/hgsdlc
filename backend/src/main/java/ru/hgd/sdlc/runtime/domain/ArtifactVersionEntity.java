package ru.hgd.sdlc.runtime.domain;

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
@Table(name = "artifact_versions")
public class ArtifactVersionEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "node_id", nullable = false, length = 255)
    private String nodeId;

    @Column(name = "artifact_key", nullable = false, length = 255)
    private String artifactKey;

    @Column(nullable = false, length = 2048)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ArtifactScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ArtifactKind kind;

    @Column(length = 128)
    private String checksum;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

package ru.hgd.sdlc.benchmark.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "benchmark_cases")
public class BenchmarkCaseEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(length = 512)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String instruction;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "artifact_type", length = 16)
    private String artifactType;

    @Column(name = "artifact_id", length = 255)
    private String artifactId;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

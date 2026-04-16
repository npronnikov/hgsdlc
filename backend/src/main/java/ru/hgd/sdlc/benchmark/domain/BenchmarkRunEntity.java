package ru.hgd.sdlc.benchmark.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "benchmark_runs")
public class BenchmarkRunEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 16)
    private ArtifactType artifactType;

    @Column(name = "artifact_id", nullable = false, length = 255)
    private String artifactId;

    @Column(name = "artifact_version_id")
    private UUID artifactVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_b_type", length = 16)
    private ArtifactType artifactBType;

    @Column(name = "artifact_b_id", length = 255)
    private String artifactBId;

    @Column(name = "artifact_b_version_id")
    private UUID artifactBVersionId;

    @Column(name = "coding_agent", nullable = false, length = 64)
    private String codingAgent;

    @Column(name = "run_a_id")
    private UUID runAId;

    @Column(name = "run_b_id")
    private UUID runBId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private BenchmarkStatus status = BenchmarkStatus.RUNNING;

    @Enumerated(EnumType.STRING)
    @Column(name = "human_verdict", length = 32)
    private BenchmarkVerdict humanVerdict;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "line_comments_json", columnDefinition = "TEXT")
    private String lineCommentsJson;

    @Column(name = "decision_scores_json", columnDefinition = "TEXT")
    private String decisionScoresJson;

    @Column(name = "judge_result", columnDefinition = "TEXT")
    private String judgeResult;

    @Column(name = "diff_a", columnDefinition = "TEXT")
    private String diffA;

    @Column(name = "diff_b", columnDefinition = "TEXT")
    private String diffB;

    @Column(name = "diff_of_diffs", columnDefinition = "TEXT")
    private String diffOfDiffs;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "resource_version", nullable = false)
    private long resourceVersion;
}

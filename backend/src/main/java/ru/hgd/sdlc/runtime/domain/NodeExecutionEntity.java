package ru.hgd.sdlc.runtime.domain;

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
@Table(name = "node_executions")
public class NodeExecutionEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "node_id", nullable = false, length = 255)
    private String nodeId;

    @Column(name = "node_kind", nullable = false, length = 64)
    private String nodeKind;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NodeExecutionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "checkpoint_enabled", nullable = false)
    private boolean checkpointEnabled;

    @Column(name = "checkpoint_commit_sha", length = 64)
    private String checkpointCommitSha;

    @Column(name = "checkpoint_created_at")
    private Instant checkpointCreatedAt;

    @Column(name = "agent_session_id", length = 255)
    private String agentSessionId;

    @Column(name = "step_summary_json", columnDefinition = "TEXT")
    private String stepSummaryJson;

    @Version
    @Column(name = "resource_version", nullable = false)
    private long resourceVersion;
}

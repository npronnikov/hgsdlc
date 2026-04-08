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
@Table(name = "runs")
public class RunEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "target_branch", nullable = false, length = 255)
    private String targetBranch;

    @Column(name = "flow_canonical_name", nullable = false, length = 255)
    private String flowCanonicalName;

    @Column(name = "flow_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String flowSnapshotJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_mode", nullable = false, length = 16)
    @Builder.Default
    private RunPublishMode publishMode = RunPublishMode.LOCAL;

    @Column(name = "work_branch", nullable = false, length = 255)
    @Builder.Default
    private String workBranch = "main";

    @Enumerated(EnumType.STRING)
    @Column(name = "pr_commit_strategy", length = 32)
    private PrCommitStrategy prCommitStrategy;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_status", nullable = false, length = 32)
    @Builder.Default
    private RunPublishStatus publishStatus = RunPublishStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_status", nullable = false, length = 32)
    @Builder.Default
    private RunPublishStatus pushStatus = RunPublishStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "pr_status", nullable = false, length = 32)
    @Builder.Default
    private RunPublishStatus prStatus = RunPublishStatus.PENDING;

    @Column(name = "publish_error_step", length = 64)
    private String publishErrorStep;

    @Column(name = "publish_commit_sha", length = 64)
    private String publishCommitSha;

    @Column(name = "pr_url", length = 1024)
    private String prUrl;

    @Column(name = "pr_number")
    private Integer prNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RunStatus status;

    @Column(name = "current_node_id", nullable = false, length = 255)
    private String currentNodeId;

    @Column(name = "feature_request", nullable = false, columnDefinition = "TEXT")
    private String featureRequest;

    @Column(name = "pending_rework_instruction", columnDefinition = "TEXT")
    private String pendingReworkInstruction;

    @Column(name = "context_file_manifest_json", columnDefinition = "TEXT")
    private String contextFileManifestJson;

    @Column(name = "workspace_root", length = 2048)
    private String workspaceRoot;

    @Column(name = "error_code", length = 128)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "skip_gates", nullable = false)
    @Builder.Default
    private boolean skipGates = false;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    @Column(name = "resource_version", nullable = false)
    private long resourceVersion;
}

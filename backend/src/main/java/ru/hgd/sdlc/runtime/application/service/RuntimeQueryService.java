package ru.hgd.sdlc.runtime.application.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.runtime.application.dto.ArtifactContentResult;
import ru.hgd.sdlc.runtime.application.dto.AuditQueryResult;
import ru.hgd.sdlc.runtime.application.dto.GateChangesResult;
import ru.hgd.sdlc.runtime.application.dto.GateDiffResult;
import ru.hgd.sdlc.runtime.application.dto.NodeLogResult;
import ru.hgd.sdlc.runtime.application.port.WorkspacePort;
import ru.hgd.sdlc.runtime.domain.ActorType;
import ru.hgd.sdlc.runtime.domain.ArtifactVersionEntity;
import ru.hgd.sdlc.runtime.domain.AuditEventEntity;
import ru.hgd.sdlc.runtime.domain.GateInstanceEntity;
import ru.hgd.sdlc.runtime.domain.GateStatus;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.infrastructure.ArtifactVersionRepository;
import ru.hgd.sdlc.runtime.infrastructure.AuditEventRepository;
import ru.hgd.sdlc.runtime.infrastructure.GateInstanceRepository;
import ru.hgd.sdlc.runtime.infrastructure.NodeExecutionRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;

@Service
public class RuntimeQueryService {
    private static final List<GateStatus> OPEN_GATE_STATUSES = List.of(
            GateStatus.AWAITING_INPUT,
            GateStatus.AWAITING_DECISION,
            GateStatus.FAILED_VALIDATION
    );
    private static final String DEFAULT_GATE_ASSIGNEE_ROLE = Role.TECH_APPROVER.name();

    private final RunRepository runRepository;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final GateInstanceRepository gateInstanceRepository;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final AuditEventRepository auditEventRepository;
    private final GitReviewService gitReviewService;
    private final NodeLogService nodeLogService;
    private final WorkspacePort workspacePort;

    public RuntimeQueryService(
            RunRepository runRepository,
            NodeExecutionRepository nodeExecutionRepository,
            GateInstanceRepository gateInstanceRepository,
            ArtifactVersionRepository artifactVersionRepository,
            AuditEventRepository auditEventRepository,
            GitReviewService gitReviewService,
            NodeLogService nodeLogService,
            WorkspacePort workspacePort
    ) {
        this.runRepository = runRepository;
        this.nodeExecutionRepository = nodeExecutionRepository;
        this.gateInstanceRepository = gateInstanceRepository;
        this.artifactVersionRepository = artifactVersionRepository;
        this.auditEventRepository = auditEventRepository;
        this.gitReviewService = gitReviewService;
        this.nodeLogService = nodeLogService;
        this.workspacePort = workspacePort;
    }

    @Transactional(readOnly = true)
    public RunEntity findRun(UUID runId) {
        return getRunEntity(runId);
    }

    @Transactional(readOnly = true)
    public List<RunEntity> findRuns(int limit) {
        List<RunEntity> runs = runRepository.findAllByOrderByCreatedAtDesc();
        if (limit <= 0 || runs.size() <= limit) {
            return runs;
        }
        return runs.subList(0, limit);
    }

    @Transactional(readOnly = true)
    public List<RunEntity> findRunsByProject(UUID projectId, int limit) {
        List<RunEntity> runs = runRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        if (limit <= 0 || runs.size() <= limit) {
            return runs;
        }
        return runs.subList(0, limit);
    }

    @Transactional(readOnly = true)
    public Optional<GateInstanceEntity> findCurrentGate(UUID runId) {
        getRunEntity(runId);
        return gateInstanceRepository.findFirstByRunIdAndStatusInOrderByOpenedAtDesc(
                runId,
                OPEN_GATE_STATUSES
        );
    }

    @Transactional(readOnly = true)
    public List<GateInstanceEntity> findGatesByRunId(UUID runId) {
        getRunEntity(runId);
        return gateInstanceRepository.findByRunIdOrderByOpenedAtDesc(runId);
    }

    @Transactional(readOnly = true)
    public List<GateInstanceEntity> findGateInbox(User user) {
        List<GateInstanceEntity> all = gateInstanceRepository.findByStatusInOrderByOpenedAtAsc(OPEN_GATE_STATUSES);
        if (user == null) {
            return all;
        }
        return all.stream()
                .filter((gate) -> {
                    String role = gate.getAssigneeRole();
                    if (role == null || role.isBlank()) {
                        role = DEFAULT_GATE_ASSIGNEE_ROLE;
                    }
                    return user.hasRoleName(role);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public GateChangesResult findGateChanges(UUID gateId, User user) {
        return gitReviewService.collectGateChanges(gateId, user);
    }

    @Transactional(readOnly = true)
    public GateDiffResult findGateDiff(UUID gateId, String path, User user) {
        return gitReviewService.buildGateDiff(gateId, path, user);
    }

    @Transactional(readOnly = true)
    public List<NodeExecutionEntity> findNodeExecutions(UUID runId) {
        getRunEntity(runId);
        return nodeExecutionRepository.findByRunIdOrderByStartedAtAsc(runId);
    }

    @Transactional(readOnly = true)
    public List<ArtifactVersionEntity> findArtifacts(UUID runId) {
        getRunEntity(runId);
        return artifactVersionRepository.findByRunIdOrderByCreatedAtDesc(runId);
    }

    @Transactional(readOnly = true)
    public ArtifactContentResult findArtifactContent(UUID runId, UUID artifactVersionId) {
        getRunEntity(runId);
        ArtifactVersionEntity artifact = artifactVersionRepository.findById(artifactVersionId)
                .orElseThrow(() -> new NotFoundException("Artifact version not found: " + artifactVersionId));
        if (!artifact.getRunId().equals(runId)) {
            throw new NotFoundException("Artifact version not found in run: " + artifactVersionId);
        }
        String content = readFileContent(Path.of(artifact.getPath()));
        if (content == null) {
            throw new NotFoundException("Artifact content not available: " + artifactVersionId);
        }
        return new ArtifactContentResult(artifact, content);
    }

    @Transactional(readOnly = true)
    public AuditEventEntity findAuditEvent(UUID runId, UUID eventId) {
        getRunEntity(runId);
        return auditEventRepository.findByIdAndRunId(eventId, runId)
                .orElseThrow(() -> new NotFoundException("Audit event not found: " + eventId));
    }

    @Transactional(readOnly = true)
    public List<AuditEventEntity> findAuditEvents(UUID runId) {
        getRunEntity(runId);
        return auditEventRepository.findByRunIdOrderBySequenceNoAsc(runId);
    }

    @Transactional(readOnly = true)
    public AuditQueryResult findAuditEvents(
            UUID runId,
            UUID nodeExecutionId,
            String eventType,
            String actorType,
            Long cursor,
            int limit
    ) {
        getRunEntity(runId);
        int effectiveLimit = Math.min(Math.max(limit, 1), 500);
        ActorType parsedActorType = parseActorType(actorType);
        List<AuditEventEntity> events = auditEventRepository.queryFiltered(
                runId,
                nodeExecutionId,
                eventType != null && eventType.isBlank() ? null : eventType,
                parsedActorType,
                cursor,
                PageRequest.of(0, effectiveLimit + 1)
        );
        boolean hasMore = events.size() > effectiveLimit;
        List<AuditEventEntity> page = hasMore ? events.subList(0, effectiveLimit) : events;
        Long nextCursor = hasMore ? page.get(page.size() - 1).getSequenceNo() : null;
        return new AuditQueryResult(page, nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public NodeLogResult findNodeLog(UUID runId, UUID nodeExecutionId, long offset) {
        return nodeLogService.readNodeLog(runId, nodeExecutionId, offset);
    }

    private RunEntity getRunEntity(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Run not found: " + runId));
    }

    private ActorType parseActorType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ActorType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String readFileContent(Path path) {
        if (path == null || !workspacePort.exists(path) || workspacePort.isDirectory(path)) {
            return null;
        }
        try {
            long size = workspacePort.size(path);
            if (size > 512_000) {
                return null;
            }
            return workspacePort.readString(path, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }
}

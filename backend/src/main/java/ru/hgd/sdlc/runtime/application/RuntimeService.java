package ru.hgd.sdlc.runtime.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.runtime.application.command.ApproveGateCommand;
import ru.hgd.sdlc.runtime.application.command.CreateRunCommand;
import ru.hgd.sdlc.runtime.application.command.ReworkGateCommand;
import ru.hgd.sdlc.runtime.application.command.SubmitInputCommand;
import ru.hgd.sdlc.runtime.application.dto.ArtifactContentResult;
import ru.hgd.sdlc.runtime.application.dto.AuditQueryResult;
import ru.hgd.sdlc.runtime.application.dto.GateActionResult;
import ru.hgd.sdlc.runtime.application.dto.GateChangesResult;
import ru.hgd.sdlc.runtime.application.dto.GateDiffResult;
import ru.hgd.sdlc.runtime.application.dto.NodeLogResult;
import ru.hgd.sdlc.runtime.application.service.RuntimeCommandService;
import ru.hgd.sdlc.runtime.application.service.RuntimeQueryService;
import ru.hgd.sdlc.runtime.domain.ArtifactVersionEntity;
import ru.hgd.sdlc.runtime.domain.AuditEventEntity;
import ru.hgd.sdlc.runtime.domain.GateInstanceEntity;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;

@Service
public class RuntimeService {
    private final RuntimeCommandService runtimeCommandService;
    private final RuntimeQueryService runtimeQueryService;

    public RuntimeService(
            RuntimeCommandService runtimeCommandService,
            RuntimeQueryService runtimeQueryService
    ) {
        this.runtimeCommandService = runtimeCommandService;
        this.runtimeQueryService = runtimeQueryService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Deprecated(since = "stage-f", forRemoval = false)
    public RunEntity createRun(CreateRunCommand command, User user) {
        return runtimeCommandService.createRun(command, user);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Deprecated(since = "stage-f", forRemoval = false)
    public void startRun(UUID runId) {
        runtimeCommandService.startRun(runId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Deprecated(since = "stage-f", forRemoval = false)
    public RunEntity resumeRun(UUID runId) {
        return runtimeCommandService.resumeRun(runId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Deprecated(since = "stage-f", forRemoval = false)
    public RunEntity cancelRun(UUID runId, User user) {
        return runtimeCommandService.cancelRun(runId, user);
    }

    @Transactional(readOnly = true)
    @Deprecated(since = "stage-c", forRemoval = false)
    public RunEntity getRun(UUID runId) {
        return runtimeQueryService.findRun(runId);
    }

    @Transactional(readOnly = true)
    @Deprecated(since = "stage-c", forRemoval = false)
    public List<NodeExecutionEntity> listNodeExecutions(UUID runId) {
        return runtimeQueryService.findNodeExecutions(runId);
    }

    @Transactional(readOnly = true)
    @Deprecated(since = "stage-c", forRemoval = false)
    public List<ArtifactVersionEntity> listArtifacts(UUID runId) {
        return runtimeQueryService.findArtifacts(runId);
    }

    @Transactional(readOnly = true)
    @Deprecated(since = "stage-c", forRemoval = false)
    public ArtifactContentResult getArtifactContent(UUID runId, UUID artifactVersionId) {
        return runtimeQueryService.findArtifactContent(runId, artifactVersionId);
    }

    @Deprecated(since = "stage-c", forRemoval = false)
    public NodeLogResult getNodeLog(UUID runId, UUID nodeExecutionId, long offset) {
        return runtimeQueryService.findNodeLog(runId, nodeExecutionId, offset);
    }

    @Transactional(readOnly = true)
    @Deprecated(since = "stage-c", forRemoval = false)
    public Optional<GateInstanceEntity> findCurrentGate(UUID runId) {
        return runtimeQueryService.findCurrentGate(runId);
    }

    @Transactional(readOnly = true)
    @Deprecated(since = "stage-c", forRemoval = false)
    public List<GateInstanceEntity> listInboxGates(User user) {
        return runtimeQueryService.findGateInbox(user);
    }

    @Transactional(readOnly = true)
    @Deprecated(since = "stage-c", forRemoval = false)
    public GateChangesResult getGateChanges(UUID gateId, User user) {
        return runtimeQueryService.findGateChanges(gateId, user);
    }

    @Transactional(readOnly = true)
    @Deprecated(since = "stage-c", forRemoval = false)
    public GateDiffResult getGateDiff(UUID gateId, String path, User user) {
        return runtimeQueryService.findGateDiff(gateId, path, user);
    }

    @Transactional(readOnly = true)
    @Deprecated(since = "stage-c", forRemoval = false)
    public List<AuditEventEntity> listAuditEvents(UUID runId) {
        return runtimeQueryService.findAuditEvents(runId);
    }

    @Transactional(readOnly = true)
    @Deprecated(since = "stage-c", forRemoval = false)
    public AuditQueryResult queryAuditEvents(
            UUID runId,
            UUID nodeExecutionId,
            String eventType,
            String actorType,
            Long cursor,
            int limit
    ) {
        return runtimeQueryService.findAuditEvents(runId, nodeExecutionId, eventType, actorType, cursor, limit);
    }

    @Transactional(readOnly = true)
    @Deprecated(since = "stage-c", forRemoval = false)
    public List<RunEntity> listRunsByProject(UUID projectId, int limit) {
        return runtimeQueryService.findRunsByProject(projectId, limit);
    }

    @Transactional(readOnly = true)
    @Deprecated(since = "stage-c", forRemoval = false)
    public List<RunEntity> listRuns(int limit) {
        return runtimeQueryService.findRuns(limit);
    }

    @Transactional(readOnly = true)
    @Deprecated(since = "stage-c", forRemoval = false)
    public AuditEventEntity getAuditEvent(UUID runId, UUID eventId) {
        return runtimeQueryService.findAuditEvent(runId, eventId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Deprecated(since = "stage-d", forRemoval = false)
    public GateActionResult submitInput(UUID gateId, SubmitInputCommand command, User user) {
        return runtimeCommandService.submitInput(gateId, command, user);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Deprecated(since = "stage-d", forRemoval = false)
    public GateActionResult approveGate(UUID gateId, ApproveGateCommand command, User user) {
        return runtimeCommandService.approveGate(gateId, command, user);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Deprecated(since = "stage-d", forRemoval = false)
    public GateActionResult requestRework(UUID gateId, ReworkGateCommand command, User user) {
        return runtimeCommandService.requestRework(gateId, command, user);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Deprecated(since = "stage-f", forRemoval = false)
    public void recoverActiveRuns() {
        runtimeCommandService.recoverActiveRuns();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Deprecated(since = "stage-e", forRemoval = false)
    public void tick(UUID runId) {
        runtimeCommandService.processRunStep(runId);
    }
}

package ru.hgd.sdlc.runtime.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.runtime.domain.ActorType;
import ru.hgd.sdlc.runtime.domain.ArtifactKind;
import ru.hgd.sdlc.runtime.domain.ArtifactScope;
import ru.hgd.sdlc.runtime.domain.ArtifactVersionEntity;
import ru.hgd.sdlc.runtime.domain.AuditEventEntity;
import ru.hgd.sdlc.runtime.domain.GateInstanceEntity;
import ru.hgd.sdlc.runtime.domain.GateKind;
import ru.hgd.sdlc.runtime.domain.GateStatus;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.NodeExecutionStatus;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.infrastructure.ArtifactVersionRepository;
import ru.hgd.sdlc.runtime.infrastructure.AuditEventRepository;
import ru.hgd.sdlc.runtime.infrastructure.GateInstanceRepository;
import ru.hgd.sdlc.runtime.infrastructure.NodeExecutionRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;

@Service
public class RuntimeStepTxService {
    private static final Logger log = LoggerFactory.getLogger(RuntimeStepTxService.class);
    private static final List<GateStatus> OPEN_GATE_STATUSES = List.of(
            GateStatus.AWAITING_INPUT,
            GateStatus.AWAITING_DECISION,
            GateStatus.FAILED_VALIDATION
    );

    private final RunRepository runRepository;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final GateInstanceRepository gateInstanceRepository;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    public RuntimeStepTxService(
            RunRepository runRepository,
            NodeExecutionRepository nodeExecutionRepository,
            GateInstanceRepository gateInstanceRepository,
            ArtifactVersionRepository artifactVersionRepository,
            AuditEventRepository auditEventRepository,
            ObjectMapper objectMapper
    ) {
        this.runRepository = runRepository;
        this.nodeExecutionRepository = nodeExecutionRepository;
        this.gateInstanceRepository = gateInstanceRepository;
        this.artifactVersionRepository = artifactVersionRepository;
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity createRun(
            UUID runId,
            UUID projectId,
            String targetBranch,
            String flowCanonicalName,
            String flowSnapshotJson,
            String currentNodeId,
            String featureRequest,
            String contextFileManifestJson,
            String workspaceRoot,
            String createdBy,
            Instant createdAt
    ) {
        RunEntity entity = RunEntity.builder()
                .id(runId)
                .projectId(projectId)
                .targetBranch(targetBranch)
                .flowCanonicalName(flowCanonicalName)
                .flowSnapshotJson(flowSnapshotJson)
                .status(RunStatus.CREATED)
                .currentNodeId(currentNodeId)
                .featureRequest(featureRequest)
                .contextFileManifestJson(contextFileManifestJson)
                .workspaceRoot(workspaceRoot)
                .createdBy(createdBy)
                .createdAt(createdAt)
                .build();
        runRepository.save(entity);
        appendAuditInternal(
                runId,
                null,
                null,
                "run_created",
                ActorType.HUMAN,
                createdBy,
                Map.of(
                        "project_id", projectId,
                        "target_branch", targetBranch,
                        "flow_canonical_name", flowCanonicalName
                )
        );
        return entity;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity markRunStarted(UUID runId, Instant startedAt) {
        RunEntity run = getRun(runId);
        if (run.getStatus() == RunStatus.CREATED) {
            run.setStatus(RunStatus.RUNNING);
            run.setStartedAt(startedAt);
            runRepository.save(run);
            appendAuditInternal(runId, null, null, "run_started", ActorType.SYSTEM, "runtime", Map.of());
        }
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEventEntity appendAudit(
            UUID runId,
            UUID nodeExecutionId,
            UUID gateId,
            String eventType,
            ActorType actorType,
            String actorId,
            Object payload
    ) {
        return appendAuditInternal(runId, nodeExecutionId, gateId, eventType, actorType, actorId, payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity cancelRun(UUID runId, String actorId) {
        RunEntity run = getRun(runId);
        if (run.getStatus() == RunStatus.COMPLETED || run.getStatus() == RunStatus.FAILED || run.getStatus() == RunStatus.CANCELLED) {
            return run;
        }
        run.setStatus(RunStatus.CANCELLED);
        run.setFinishedAt(Instant.now());
        runRepository.save(run);

        gateInstanceRepository.findFirstByRunIdAndStatusInOrderByOpenedAtDesc(runId, OPEN_GATE_STATUSES)
                .ifPresent(gate -> {
                    gate.setStatus(GateStatus.CANCELLED);
                    gate.setClosedAt(Instant.now());
                    gateInstanceRepository.save(gate);
                });

        List<NodeExecutionEntity> cancellableExecutions = nodeExecutionRepository.findByRunIdAndStatusInOrderByStartedAtDesc(
                runId,
                List.of(NodeExecutionStatus.CREATED, NodeExecutionStatus.RUNNING, NodeExecutionStatus.WAITING_GATE)
        );
        for (NodeExecutionEntity execution : cancellableExecutions) {
            execution.setStatus(NodeExecutionStatus.CANCELLED);
            execution.setFinishedAt(Instant.now());
            nodeExecutionRepository.save(execution);
        }

        appendAuditInternal(runId, null, null, "run_cancelled", ActorType.HUMAN, actorId, Map.of());
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NodeExecutionEntity createNodeExecution(UUID runId, String nodeId, String nodeKind, int attemptNo) {
        NodeExecutionEntity execution = NodeExecutionEntity.builder()
                .id(UUID.randomUUID())
                .runId(runId)
                .nodeId(nodeId)
                .nodeKind(nodeKind)
                .attemptNo(attemptNo)
                .status(NodeExecutionStatus.RUNNING)
                .startedAt(Instant.now())
                .build();
        nodeExecutionRepository.save(execution);
        appendAuditInternal(
                runId,
                execution.getId(),
                null,
                "node_execution_started",
                ActorType.SYSTEM,
                "runtime",
                Map.of("node_id", nodeId, "node_kind", nodeKind, "attempt_no", attemptNo)
        );
        return execution;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NodeExecutionEntity markNodeExecutionFailed(
            UUID runId,
            UUID executionId,
            String errorCode,
            String errorMessage,
            String auditEventType
    ) {
        NodeExecutionEntity execution = getNodeExecution(runId, executionId);
        execution.setStatus(NodeExecutionStatus.FAILED);
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorMessage);
        execution.setFinishedAt(Instant.now());
        nodeExecutionRepository.save(execution);
        appendAuditInternal(
                runId,
                executionId,
                null,
                auditEventType,
                ActorType.SYSTEM,
                "runtime",
                Map.of(
                        "error_code", errorCode,
                        "error_message", errorMessage
                )
        );
        return execution;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NodeExecutionEntity markNodeExecutionSucceeded(UUID runId, UUID executionId, String nodeId) {
        NodeExecutionEntity execution = getNodeExecution(runId, executionId);
        execution.setStatus(NodeExecutionStatus.SUCCEEDED);
        execution.setFinishedAt(Instant.now());
        nodeExecutionRepository.save(execution);
        appendAuditInternal(
                runId,
                executionId,
                null,
                "node_execution_succeeded",
                ActorType.SYSTEM,
                "runtime",
                Map.of("node_id", nodeId)
        );
        return execution;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GateInstanceEntity openGate(
            UUID runId,
            UUID executionId,
            String nodeId,
            GateKind gateKind,
            GateStatus initialStatus,
            String assigneeRole
    ) {
        NodeExecutionEntity execution = getNodeExecution(runId, executionId);
        execution.setStatus(NodeExecutionStatus.WAITING_GATE);
        nodeExecutionRepository.save(execution);

        GateInstanceEntity gate = GateInstanceEntity.builder()
                .id(UUID.randomUUID())
                .runId(runId)
                .nodeExecutionId(executionId)
                .nodeId(nodeId)
                .gateKind(gateKind)
                .status(initialStatus)
                .assigneeRole(assigneeRole)
                .openedAt(Instant.now())
                .build();
        gateInstanceRepository.save(gate);

        RunEntity run = getRun(runId);
        run.setStatus(RunStatus.WAITING_GATE);
        runRepository.save(run);

        appendAuditInternal(
                runId,
                executionId,
                gate.getId(),
                "gate_opened",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "gate_kind", gateKind.name().toLowerCase(Locale.ROOT),
                        "assignee_role", assigneeRole
                )
        );
        appendAuditInternal(runId, executionId, gate.getId(), "run_waiting_gate", ActorType.SYSTEM, "runtime", Map.of());
        return gate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GateInstanceEntity markGateValidationFailed(
            UUID runId,
            UUID gateId,
            UUID nodeExecutionId,
            String comment,
            List<String> validationErrors
    ) {
        GateInstanceEntity gate = getGate(gateId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("comment", comment);
        payload.put("validation_errors", validationErrors);
        gate.setStatus(GateStatus.FAILED_VALIDATION);
        gate.setPayloadJson(toJson(payload));
        gateInstanceRepository.save(gate);
        appendAuditInternal(
                runId,
                nodeExecutionId,
                gateId,
                "node_validation_failed",
                ActorType.SYSTEM,
                "runtime",
                Map.of("errors", validationErrors)
        );
        return gate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GateInstanceEntity submitGateInput(
            UUID runId,
            UUID gateId,
            UUID nodeExecutionId,
            String comment
    ) {
        GateInstanceEntity gate = getGate(gateId);
        gate.setStatus(GateStatus.SUBMITTED);
        gate.setClosedAt(Instant.now());
        gate.setPayloadJson(toJson(mapOf("comment", comment)));
        gateInstanceRepository.save(gate);
        return gate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GateInstanceEntity approveGate(
            UUID runId,
            UUID gateId,
            UUID nodeExecutionId,
            String actorId,
            String comment,
            List<UUID> reviewedArtifactVersionIds
    ) {
        GateInstanceEntity gate = getGate(gateId);
        gate.setStatus(GateStatus.APPROVED);
        gate.setClosedAt(Instant.now());
        gate.setPayloadJson(toJson(mapOf(
                "comment", comment,
                "reviewed_artifact_version_ids", reviewedArtifactVersionIds == null ? List.of() : reviewedArtifactVersionIds
        )));
        gateInstanceRepository.save(gate);
        appendAuditInternal(
                runId,
                nodeExecutionId,
                gateId,
                "gate_approved",
                ActorType.HUMAN,
                actorId,
                mapOf("comment", comment)
        );
        return gate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GateInstanceEntity requestRework(
            UUID runId,
            UUID gateId,
            UUID nodeExecutionId,
            String actorId,
            String mode,
            String comment,
            String instruction,
            List<UUID> reviewedArtifactVersionIds
    ) {
        GateInstanceEntity gate = getGate(gateId);
        gate.setStatus(GateStatus.REWORK_REQUESTED);
        gate.setClosedAt(Instant.now());
        gate.setPayloadJson(toJson(mapOf(
                "mode", mode,
                "comment", comment,
                "instruction", instruction,
                "reviewed_artifact_version_ids", reviewedArtifactVersionIds == null ? List.of() : reviewedArtifactVersionIds
        )));
        gateInstanceRepository.save(gate);
        appendAuditInternal(
                runId,
                nodeExecutionId,
                gateId,
                "gate_rework_requested",
                ActorType.HUMAN,
                actorId,
                mapOf("mode", mode, "comment", comment, "instruction", instruction)
        );
        return gate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity replacePendingReworkInstruction(UUID runId, UUID gateId, String instruction) {
        RunEntity run = getRun(runId);
        String nextInstruction = trimToNull(instruction);
        run.setPendingReworkInstruction(nextInstruction);
        runRepository.save(run);
        appendAuditInternal(
                runId,
                null,
                gateId,
                "rework_instruction_staged",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "instruction", nextInstruction,
                        "action", nextInstruction == null ? "cleared" : "set"
                )
        );
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity appendFeatureRequestClarification(UUID runId, UUID gateId, String clarification) {
        RunEntity run = getRun(runId);
        String trimmedClarification = trimToNull(clarification);
        if (trimmedClarification == null) {
            return run;
        }
        String currentFeatureRequest = run.getFeatureRequest();
        if (currentFeatureRequest == null || currentFeatureRequest.isBlank()) {
            run.setFeatureRequest(trimmedClarification);
        } else {
            run.setFeatureRequest(currentFeatureRequest + "\n" + trimmedClarification);
        }
        run.setPendingReworkInstruction(null);
        runRepository.save(run);
        appendAuditInternal(
                runId,
                null,
                gateId,
                "feature_request_clarified",
                ActorType.SYSTEM,
                "runtime",
                mapOf("clarification", trimmedClarification)
        );
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity consumePendingReworkInstruction(UUID runId, UUID nodeExecutionId, String nodeId) {
        RunEntity run = getRun(runId);
        String instruction = trimToNull(run.getPendingReworkInstruction());
        if (instruction == null) {
            return run;
        }
        run.setPendingReworkInstruction(null);
        runRepository.save(run);
        appendAuditInternal(
                runId,
                nodeExecutionId,
                null,
                "rework_instruction_consumed",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "node_id", nodeId,
                        "instruction", instruction
                )
        );
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity applyTransition(
            UUID runId,
            UUID nodeExecutionId,
            UUID gateId,
            String targetNodeId,
            String transitionLabel
    ) {
        RunEntity run = getRun(runId);
        run.setCurrentNodeId(targetNodeId);
        run.setStatus(RunStatus.RUNNING);
        runRepository.save(run);
        appendAuditInternal(
                runId,
                nodeExecutionId,
                gateId,
                "transition_applied",
                ActorType.SYSTEM,
                "runtime",
                Map.of(
                        "transition", transitionLabel,
                        "target_node_id", targetNodeId
                )
        );
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity completeRun(UUID runId, UUID executionId, String nodeId) {
        NodeExecutionEntity execution = getNodeExecution(runId, executionId);
        execution.setStatus(NodeExecutionStatus.SUCCEEDED);
        execution.setFinishedAt(Instant.now());
        nodeExecutionRepository.save(execution);
        appendAuditInternal(
                runId,
                executionId,
                null,
                "node_execution_succeeded",
                ActorType.SYSTEM,
                "runtime",
                Map.of("node_id", nodeId)
        );

        RunEntity run = getRun(runId);
        run.setStatus(RunStatus.COMPLETED);
        run.setFinishedAt(Instant.now());
        runRepository.save(run);
        appendAuditInternal(runId, executionId, null, "run_completed", ActorType.SYSTEM, "runtime", Map.of());
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity failRun(UUID runId, String errorCode, String message) {
        RunEntity run = getRun(runId);
        run.setStatus(RunStatus.FAILED);
        run.setErrorCode(errorCode);
        run.setErrorMessage(message);
        run.setFinishedAt(Instant.now());
        runRepository.save(run);
        appendAuditInternal(
                runId,
                null,
                null,
                "run_failed",
                ActorType.SYSTEM,
                "runtime",
                Map.of(
                        "error_code", errorCode,
                        "error_message", message
                )
        );
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ArtifactVersionEntity recordArtifactVersion(
            UUID runId,
            String nodeId,
            String artifactKey,
            String path,
            ArtifactScope scope,
            ArtifactKind kind,
            String checksum,
            long sizeBytes
    ) {
        ArtifactVersionEntity previous = artifactVersionRepository.findFirstByRunIdAndArtifactKeyOrderByCreatedAtDesc(runId, artifactKey)
                .orElse(null);
        ArtifactVersionEntity artifact = ArtifactVersionEntity.builder()
                .id(UUID.randomUUID())
                .runId(runId)
                .nodeId(nodeId)
                .artifactKey(artifactKey)
                .path(path)
                .scope(scope)
                .kind(kind)
                .checksum(checksum)
                .sizeBytes(sizeBytes)
                .supersedesArtifactVersionId(previous == null ? null : previous.getId())
                .createdAt(Instant.now())
                .build();
        artifactVersionRepository.save(artifact);
        appendAuditInternal(
                runId,
                null,
                null,
                "artifact_version_created",
                ActorType.SYSTEM,
                "runtime",
                Map.of(
                        "artifact_version_id", artifact.getId(),
                        "artifact_key", artifact.getArtifactKey(),
                        "path", artifact.getPath(),
                        "kind", artifact.getKind().name().toLowerCase(Locale.ROOT)
                )
        );
        return artifact;
    }

    private AuditEventEntity appendAuditInternal(
            UUID runId,
            UUID nodeExecutionId,
            UUID gateId,
            String eventType,
            ActorType actorType,
            String actorId,
            Object payload
    ) {
        long nextSequence = auditEventRepository.findFirstByRunIdOrderBySequenceNoDesc(runId)
                .map((event) -> event.getSequenceNo() + 1)
                .orElse(1L);
        AuditEventEntity event = AuditEventEntity.builder()
                .id(UUID.randomUUID())
                .runId(runId)
                .nodeExecutionId(nodeExecutionId)
                .gateId(gateId)
                .sequenceNo(nextSequence)
                .eventType(eventType)
                .eventTime(Instant.now())
                .actorType(actorType)
                .actorId(actorId)
                .payloadJson(payload == null ? null : toJson(payload))
                .build();
        AuditEventEntity saved = auditEventRepository.save(event);
        logAuditEvent(saved);
        return saved;
    }

    private void logAuditEvent(AuditEventEntity event) {
        log.info(
                "runtime_event seq={} run_id={} event_type={} actor_type={} actor_id={} node_execution_id={} gate_id={} payload={}",
                event.getSequenceNo(),
                event.getRunId(),
                event.getEventType(),
                event.getActorType().name().toLowerCase(Locale.ROOT),
                event.getActorId(),
                event.getNodeExecutionId(),
                event.getGateId(),
                truncate(event.getPayloadJson(), 16000)
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "{}";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ValidationException("Failed to serialize JSON payload");
        }
    }

    private Map<String, Object> mapOf(Object... keyValues) {
        if (keyValues == null || keyValues.length % 2 != 0) {
            throw new ValidationException("Invalid payload map");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object key = keyValues[i];
            if (!(key instanceof String keyString)) {
                throw new ValidationException("Invalid payload map key");
            }
            map.put(keyString, keyValues[i + 1]);
        }
        return map;
    }

    private RunEntity getRun(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Run not found: " + runId));
    }

    private NodeExecutionEntity getNodeExecution(UUID runId, UUID executionId) {
        return nodeExecutionRepository.findByIdAndRunId(executionId, runId)
                .orElseThrow(() -> new NotFoundException("Node execution not found: " + executionId));
    }

    private GateInstanceEntity getGate(UUID gateId) {
        return gateInstanceRepository.findById(gateId)
                .orElseThrow(() -> new NotFoundException("Gate not found: " + gateId));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

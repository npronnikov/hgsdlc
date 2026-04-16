package ru.hgd.sdlc.runtime.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
import ru.hgd.sdlc.runtime.domain.AiSessionMode;
import ru.hgd.sdlc.runtime.domain.AuditEventEntity;
import ru.hgd.sdlc.runtime.domain.GateInstanceEntity;
import ru.hgd.sdlc.runtime.domain.GateKind;
import ru.hgd.sdlc.runtime.domain.GateStatus;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.NodeExecutionStatus;
import ru.hgd.sdlc.runtime.domain.PrCommitStrategy;
import ru.hgd.sdlc.runtime.domain.ReworkSessionPolicy;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.domain.RunPublishMode;
import ru.hgd.sdlc.runtime.domain.RunPublishStatus;
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
            AiSessionMode aiSessionMode,
            String runSessionId,
            RunPublishMode publishMode,
            String workBranch,
            PrCommitStrategy prCommitStrategy,
            RunPublishStatus publishStatus,
            RunPublishStatus pushStatus,
            RunPublishStatus prStatus,
            String currentNodeId,
            String featureRequest,
            String contextFileManifestJson,
            String workspaceRoot,
            String createdBy,
            Instant createdAt,
            boolean skipGates
    ) {
        RunEntity entity = RunEntity.builder()
                .id(runId)
                .projectId(projectId)
                .targetBranch(targetBranch)
                .flowCanonicalName(flowCanonicalName)
                .flowSnapshotJson(flowSnapshotJson)
                .aiSessionMode(aiSessionMode)
                .runSessionId(runSessionId)
                .publishMode(publishMode)
                .workBranch(workBranch)
                .prCommitStrategy(prCommitStrategy)
                .publishStatus(publishStatus)
                .pushStatus(pushStatus)
                .prStatus(prStatus)
                .status(RunStatus.CREATED)
                .currentNodeId(currentNodeId)
                .featureRequest(featureRequest)
                .contextFileManifestJson(contextFileManifestJson)
                .workspaceRoot(workspaceRoot)
                .createdBy(createdBy)
                .createdAt(createdAt)
                .skipGates(skipGates)
                .build();
        runRepository.save(entity);
        appendAuditInternal(
                runId,
                null,
                null,
                "run_created",
                ActorType.HUMAN,
                createdBy,
                mapOf(
                        "project_id", projectId,
                        "target_branch", targetBranch,
                        "flow_canonical_name", flowCanonicalName,
                        "ai_session_mode", aiSessionMode == null ? null : aiSessionMode.apiValue(),
                        "run_session_id", runSessionId,
                        "publish_mode", publishMode == null ? null : publishMode.name().toLowerCase(Locale.ROOT),
                        "work_branch", workBranch,
                        "pr_commit_strategy", prCommitStrategy == null ? null : prCommitStrategy.name().toLowerCase(Locale.ROOT)
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
        RunStatus fromStatus = run.getStatus();
        run.setStatus(RunStatus.CANCELLED);
        run.setFinishedAt(Instant.now());
        run.setPublishStatus(RunPublishStatus.SKIPPED);
        run.setPushStatus(RunPublishStatus.SKIPPED);
        run.setPrStatus(RunPublishStatus.SKIPPED);
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

        appendAuditInternal(runId, null, null, "run_cancelled", ActorType.HUMAN, actorId,
                mapOf("from_status", fromStatus.name().toLowerCase(Locale.ROOT)));
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
                .checkpointEnabled(false)
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
    public NodeExecutionEntity markNodeExecutionCheckpoint(
            UUID runId,
            UUID executionId,
            String checkpointCommitSha,
            Instant checkpointCreatedAt
    ) {
        NodeExecutionEntity execution = getNodeExecution(runId, executionId);
        execution.setCheckpointEnabled(true);
        execution.setCheckpointCommitSha(checkpointCommitSha);
        execution.setCheckpointCreatedAt(checkpointCreatedAt);
        return nodeExecutionRepository.save(execution);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NodeExecutionEntity markNodeExecutionFailed(
            UUID runId,
            UUID executionId,
            String errorCode,
            String errorMessage,
            String auditEventType,
            Map<String, Object> details
    ) {
        RunEntity run = getRun(runId);
        NodeExecutionEntity execution = getNodeExecution(runId, executionId);
        if (run.getStatus() == RunStatus.CANCELLED || execution.getStatus() == NodeExecutionStatus.CANCELLED) {
            if (execution.getStatus() != NodeExecutionStatus.CANCELLED) {
                execution.setStatus(NodeExecutionStatus.CANCELLED);
                execution.setFinishedAt(Instant.now());
                nodeExecutionRepository.save(execution);
            }
            return execution;
        }
        execution.setStatus(NodeExecutionStatus.FAILED);
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorMessage);
        execution.setFinishedAt(Instant.now());
        nodeExecutionRepository.save(execution);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error_code", errorCode);
        payload.put("error_message", errorMessage);
        if (details != null && !details.isEmpty()) {
            payload.putAll(details);
        }
        appendAuditInternal(
                runId,
                executionId,
                null,
                auditEventType,
                ActorType.SYSTEM,
                "runtime",
                payload
        );
        return execution;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NodeExecutionEntity markNodeExecutionSucceeded(
            UUID runId, UUID executionId, String nodeId, String stepSummaryJson) {
        RunEntity run = getRun(runId);
        NodeExecutionEntity execution = getNodeExecution(runId, executionId);
        if (run.getStatus() == RunStatus.CANCELLED || execution.getStatus() == NodeExecutionStatus.CANCELLED) {
            if (execution.getStatus() != NodeExecutionStatus.CANCELLED) {
                execution.setStatus(NodeExecutionStatus.CANCELLED);
                execution.setFinishedAt(Instant.now());
                nodeExecutionRepository.save(execution);
            }
            return execution;
        }
        execution.setStatus(NodeExecutionStatus.SUCCEEDED);
        execution.setFinishedAt(Instant.now());
        execution.setStepSummaryJson(stepSummaryJson);
        nodeExecutionRepository.save(execution);
        appendAuditInternal(
                runId,
                executionId,
                null,
                "node_execution_succeeded",
                ActorType.SYSTEM,
                "runtime",
                Map.of("node_id", nodeId, "node_kind", execution.getNodeKind())
        );
        return execution;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NodeExecutionEntity markNodeExecutionCancelled(UUID runId, UUID executionId, String reason) {
        NodeExecutionEntity execution = getNodeExecution(runId, executionId);
        if (execution.getStatus() == NodeExecutionStatus.CANCELLED) {
            return execution;
        }
        execution.setStatus(NodeExecutionStatus.CANCELLED);
        execution.setFinishedAt(Instant.now());
        nodeExecutionRepository.save(execution);
        appendAuditInternal(
                runId,
                executionId,
                null,
                "node_execution_cancelled",
                ActorType.SYSTEM,
                "runtime",
                mapOf("reason", trimToNull(reason))
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
            String assigneeRole,
            String payloadJson
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
                .payloadJson(payloadJson)
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
                mapOf(
                        "comment", comment,
                        "reviewed_artifact_version_ids", reviewedArtifactVersionIds == null ? List.of() : reviewedArtifactVersionIds
                )
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
            String sessionPolicyRequested,
            String sessionPolicyEffective,
            List<UUID> reviewedArtifactVersionIds
    ) {
        GateInstanceEntity gate = getGate(gateId);
        gate.setStatus(GateStatus.REWORK_REQUESTED);
        gate.setClosedAt(Instant.now());
        gate.setPayloadJson(toJson(mapOf(
                "mode", mode,
                "comment", comment,
                "instruction", instruction,
                "session_policy_requested", sessionPolicyRequested,
                "session_policy_effective", sessionPolicyEffective,
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
                mapOf(
                        "mode", mode,
                        "comment", comment,
                        "instruction", instruction,
                        "session_policy_requested", sessionPolicyRequested,
                        "session_policy_effective", sessionPolicyEffective,
                        "reviewed_artifact_version_ids", reviewedArtifactVersionIds == null ? List.of() : reviewedArtifactVersionIds
                )
        );
        return gate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity stagePendingReworkSessionPolicy(
            UUID runId,
            UUID gateId,
            boolean keepChanges,
            String sessionPolicyRequested,
            ReworkSessionPolicy sessionPolicyEffective,
            boolean shouldPersistForRuntime
    ) {
        RunEntity run = getRun(runId);
        run.setPendingReworkSessionPolicy(shouldPersistForRuntime ? sessionPolicyEffective : null);
        runRepository.save(run);
        appendAuditInternal(
                runId,
                null,
                gateId,
                "rework_session_policy_staged",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "gate_id", gateId,
                        "keep_changes", keepChanges,
                        "session_policy_requested", sessionPolicyRequested,
                        "session_policy_effective",
                        sessionPolicyEffective == null ? null : sessionPolicyEffective.apiValue()
                )
        );
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReworkSessionPolicy consumePendingReworkSessionPolicy(UUID runId) {
        RunEntity run = getRun(runId);
        ReworkSessionPolicy policy = run.getPendingReworkSessionPolicy();
        if (policy == null) {
            return null;
        }
        run.setPendingReworkSessionPolicy(null);
        runRepository.save(run);
        return policy;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NodeExecutionEntity assignAgentSession(
            UUID runId,
            UUID nodeExecutionId,
            AiSessionMode sessionMode,
            String sessionId
    ) {
        NodeExecutionEntity execution = getNodeExecution(runId, nodeExecutionId);
        execution.setAgentSessionId(sessionId);
        nodeExecutionRepository.save(execution);
        appendAuditInternal(
                runId,
                nodeExecutionId,
                null,
                "agent_session_selected",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "node_execution_id", nodeExecutionId,
                        "session_mode", sessionMode == null ? null : sessionMode.apiValue(),
                        "session_id", sessionId
                )
        );
        return execution;
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
        if (run.getStatus() == RunStatus.CANCELLED) {
            return run;
        }
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
    public RunEntity completeRun(UUID runId, UUID executionId, String nodeId, RunStatus terminalStatus) {
        RunEntity run = getRun(runId);
        if (run.getStatus() == RunStatus.CANCELLED) {
            return run;
        }
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
                Map.of("node_id", nodeId, "node_kind", execution.getNodeKind())
        );

        run.setStatus(terminalStatus);
        run.setFinishedAt(Instant.now());
        run.setPublishErrorStep(null);
        if (terminalStatus == RunStatus.FAILED || terminalStatus == RunStatus.CANCELLED) {
            run.setPublishStatus(RunPublishStatus.SKIPPED);
            run.setPushStatus(RunPublishStatus.SKIPPED);
            run.setPrStatus(RunPublishStatus.SKIPPED);
        } else if (run.getPublishMode() == RunPublishMode.LOCAL) {
            run.setPublishStatus(RunPublishStatus.SUCCEEDED);
            run.setPushStatus(RunPublishStatus.SKIPPED);
            run.setPrStatus(RunPublishStatus.SKIPPED);
        }
        runRepository.save(run);
        String auditEventType = terminalStatus == RunStatus.FAILED ? "run_failed" : "run_completed";
        appendAuditInternal(runId, executionId, null, auditEventType, ActorType.SYSTEM, "runtime",
                Map.of("terminal_status", terminalStatus.name().toLowerCase()));
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity markRunWaitingPublish(UUID runId, UUID executionId, String nodeId) {
        RunEntity run = getRun(runId);
        if (run.getStatus() == RunStatus.CANCELLED) {
            return run;
        }
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
                Map.of("node_id", nodeId, "node_kind", execution.getNodeKind())
        );

        run.setStatus(RunStatus.WAITING_PUBLISH);
        run.setPublishStatus(RunPublishStatus.PENDING);
        if (run.getPublishMode() == RunPublishMode.PR) {
            run.setPushStatus(RunPublishStatus.PENDING);
            run.setPrStatus(RunPublishStatus.PENDING);
        } else if (run.getPublishMode() == RunPublishMode.BRANCH) {
            run.setPushStatus(RunPublishStatus.PENDING);
            run.setPrStatus(RunPublishStatus.SKIPPED);
        } else {
            run.setPushStatus(RunPublishStatus.SKIPPED);
            run.setPrStatus(RunPublishStatus.SKIPPED);
        }
        run.setPublishErrorStep(null);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.setFinishedAt(null);
        runRepository.save(run);
        appendAuditInternal(
                runId,
                executionId,
                null,
                "run_waiting_publish",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "publish_mode", run.getPublishMode() == null ? null : run.getPublishMode().name().toLowerCase(Locale.ROOT),
                        "work_branch", run.getWorkBranch()
                )
        );
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity markPublishRunning(UUID runId) {
        RunEntity run = getRun(runId);
        if (run.getStatus() == RunStatus.CANCELLED || run.getStatus() == RunStatus.COMPLETED) {
            return run;
        }
        run.setStatus(RunStatus.WAITING_PUBLISH);
        run.setPublishStatus(RunPublishStatus.RUNNING);
        if (run.getPublishMode() == RunPublishMode.PR) {
            if (run.getPushStatus() == RunPublishStatus.FAILED) {
                run.setPushStatus(RunPublishStatus.PENDING);
            }
            if (run.getPrStatus() == RunPublishStatus.FAILED) {
                run.setPrStatus(RunPublishStatus.PENDING);
            }
        } else if (run.getPublishMode() == RunPublishMode.BRANCH) {
            if (run.getPushStatus() == RunPublishStatus.FAILED || run.getPushStatus() == RunPublishStatus.SKIPPED) {
                run.setPushStatus(RunPublishStatus.PENDING);
            }
            run.setPrStatus(RunPublishStatus.SKIPPED);
        } else {
            run.setPushStatus(RunPublishStatus.SKIPPED);
            run.setPrStatus(RunPublishStatus.SKIPPED);
        }
        run.setPublishErrorStep(null);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        runRepository.save(run);
        appendAuditInternal(runId, null, null, "publish_started", ActorType.SYSTEM, "runtime",
                mapOf("publish_mode", run.getPublishMode() == null ? null : run.getPublishMode().name().toLowerCase(Locale.ROOT)));
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity markPublishCommitSucceeded(UUID runId, String publishCommitSha) {
        RunEntity run = getRun(runId);
        run.setPublishCommitSha(trimToNull(publishCommitSha));
        run.setPublishStatus(RunPublishStatus.RUNNING);
        runRepository.save(run);
        appendAuditInternal(
                runId,
                null,
                null,
                "publish_commit_succeeded",
                ActorType.SYSTEM,
                "runtime",
                mapOf("publish_commit_sha", trimToNull(publishCommitSha))
        );
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity markPublishCommitSkipped(UUID runId, String reason) {
        RunEntity run = getRun(runId);
        run.setPublishStatus(RunPublishStatus.RUNNING);
        runRepository.save(run);
        appendAuditInternal(
                runId,
                null,
                null,
                "publish_commit_skipped",
                ActorType.SYSTEM,
                "runtime",
                mapOf("reason", trimToNull(reason))
        );
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity markPublishPushSucceeded(UUID runId) {
        RunEntity run = getRun(runId);
        run.setPushStatus(RunPublishStatus.SUCCEEDED);
        run.setPublishStatus(RunPublishStatus.RUNNING);
        runRepository.save(run);
        appendAuditInternal(runId, null, null, "publish_push_succeeded", ActorType.SYSTEM, "runtime",
                mapOf("work_branch", run.getWorkBranch()));
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity markPublishPrSucceeded(UUID runId, String prUrl, Integer prNumber) {
        RunEntity run = getRun(runId);
        run.setPrStatus(RunPublishStatus.SUCCEEDED);
        run.setPrUrl(trimToNull(prUrl));
        run.setPrNumber(prNumber);
        run.setPublishStatus(RunPublishStatus.RUNNING);
        runRepository.save(run);
        appendAuditInternal(
                runId,
                null,
                null,
                "publish_pr_succeeded",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "pr_url", trimToNull(prUrl),
                        "pr_number", prNumber
                )
        );
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity markPublishFailed(UUID runId, String step, String errorCode, String errorMessage) {
        RunEntity run = getRun(runId);
        if (run.getStatus() == RunStatus.CANCELLED) {
            return run;
        }
        run.setStatus(RunStatus.PUBLISH_FAILED);
        run.setPublishStatus(RunPublishStatus.FAILED);
        String normalizedStep = trimToNull(step);
        if ("push".equals(normalizedStep)) {
            run.setPushStatus(RunPublishStatus.FAILED);
        } else if ("create_pr".equals(normalizedStep)) {
            run.setPrStatus(RunPublishStatus.FAILED);
        }
        run.setPublishErrorStep(normalizedStep);
        run.setErrorCode(trimToNull(errorCode) == null ? "PUBLISH_FAILED" : trimToNull(errorCode));
        run.setErrorMessage(trimToNull(errorMessage));
        run.setFinishedAt(null);
        runRepository.save(run);
        appendAuditInternal(
                runId,
                null,
                null,
                "publish_failed",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "step", normalizedStep,
                        "error_code", run.getErrorCode(),
                        "error_message", run.getErrorMessage()
                )
        );
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity markPublishCompleted(UUID runId) {
        RunEntity run = getRun(runId);
        if (run.getStatus() == RunStatus.CANCELLED) {
            return run;
        }
        run.setStatus(RunStatus.COMPLETED);
        run.setPublishStatus(RunPublishStatus.SUCCEEDED);
        if (run.getPublishMode() == RunPublishMode.PR) {
            run.setPushStatus(RunPublishStatus.SUCCEEDED);
            run.setPrStatus(RunPublishStatus.SUCCEEDED);
        } else if (run.getPublishMode() == RunPublishMode.BRANCH) {
            run.setPushStatus(RunPublishStatus.SUCCEEDED);
            run.setPrStatus(RunPublishStatus.SKIPPED);
        } else {
            run.setPushStatus(RunPublishStatus.SKIPPED);
            run.setPrStatus(RunPublishStatus.SKIPPED);
        }
        run.setPublishErrorStep(null);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.setFinishedAt(Instant.now());
        runRepository.save(run);
        appendAuditInternal(runId, null, null, "run_completed", ActorType.SYSTEM, "runtime",
                Map.of("terminal_status", RunStatus.COMPLETED.name().toLowerCase(Locale.ROOT)));
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity resetRunForValidationRetry(UUID runId, String nodeId, String actorId, int previousAttemptNo) {
        RunEntity run = getRun(runId);
        run.setStatus(RunStatus.RUNNING);
        run.setCurrentNodeId(nodeId);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.setFinishedAt(null);
        runRepository.save(run);
        appendAuditInternal(
                runId,
                null,
                null,
                "run_retry_requested",
                ActorType.HUMAN,
                trimToNull(actorId) == null ? "runtime" : trimToNull(actorId),
                mapOf(
                        "reason", "node_validation_failed",
                        "node_id", nodeId,
                        "previous_attempt_no", previousAttemptNo
                )
        );
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity giveUpRun(UUID runId, String onFailureNodeId, String failedNodeId, String actorId) {
        RunEntity run = getRun(runId);
        run.setStatus(RunStatus.RUNNING);
        run.setCurrentNodeId(onFailureNodeId);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.setFinishedAt(null);
        runRepository.save(run);
        appendAuditInternal(
                runId,
                null,
                null,
                "run_give_up_requested",
                ActorType.HUMAN,
                trimToNull(actorId) == null ? "runtime" : trimToNull(actorId),
                mapOf("failed_node_id", failedNodeId, "on_failure_target", onFailureNodeId)
        );
        appendAuditInternal(
                runId,
                null,
                null,
                "transition_applied",
                ActorType.HUMAN,
                trimToNull(actorId) == null ? "runtime" : trimToNull(actorId),
                mapOf("transition", "on_failure", "target_node_id", onFailureNodeId)
        );
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity resetPublishForRetry(UUID runId, String actorId) {
        RunEntity run = getRun(runId);
        if (run.getStatus() != RunStatus.PUBLISH_FAILED && run.getStatus() != RunStatus.WAITING_PUBLISH) {
            return run;
        }
        String failedStep = run.getPublishErrorStep();
        run.setStatus(RunStatus.WAITING_PUBLISH);
        run.setPublishStatus(RunPublishStatus.PENDING);
        if (run.getPushStatus() == RunPublishStatus.FAILED) {
            run.setPushStatus(RunPublishStatus.PENDING);
        }
        if (run.getPrStatus() == RunPublishStatus.FAILED) {
            run.setPrStatus(RunPublishStatus.PENDING);
        }
        run.setPublishErrorStep(null);
        run.setErrorCode(null);
        run.setErrorMessage(null);
        run.setFinishedAt(null);
        runRepository.save(run);
        appendAuditInternal(
                runId,
                null,
                null,
                "publish_retry_requested",
                ActorType.HUMAN,
                trimToNull(actorId) == null ? "runtime" : trimToNull(actorId),
                mapOf("failed_step", failedStep)
        );
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunEntity failRun(UUID runId, String errorCode, String message) {
        RunEntity run = getRun(runId);
        if (run.getStatus() == RunStatus.CANCELLED) {
            return run;
        }
        run.setStatus(RunStatus.FAILED);
        run.setPublishStatus(RunPublishStatus.SKIPPED);
        run.setPushStatus(RunPublishStatus.SKIPPED);
        run.setPrStatus(RunPublishStatus.SKIPPED);
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
        Optional<ArtifactVersionEntity> existingOpt = artifactVersionRepository.findByRunIdAndNodeIdAndArtifactKey(
                runId,
                nodeId,
                artifactKey
        );
        boolean isUpdate = existingOpt.isPresent();
        ArtifactVersionEntity artifact;
        if (existingOpt.isPresent()) {
            artifact = existingOpt.get();
            artifact.setNodeId(nodeId);
            artifact.setPath(path);
            artifact.setScope(scope);
            artifact.setKind(kind);
            artifact.setChecksum(checksum);
            artifact.setSizeBytes(sizeBytes);
            artifact.setVersionNo(artifact.getVersionNo() + 1);
        } else {
            artifact = ArtifactVersionEntity.builder()
                    .id(UUID.randomUUID())
                    .runId(runId)
                    .nodeId(nodeId)
                    .artifactKey(artifactKey)
                    .path(path)
                    .scope(scope)
                    .kind(kind)
                    .checksum(checksum)
                    .sizeBytes(sizeBytes)
                    .versionNo(1)
                    .createdAt(Instant.now())
                    .build();
        }
        artifactVersionRepository.save(artifact);
        String eventType = isUpdate ? "artifact_version_updated" : "artifact_version_created";
        appendAuditInternal(
                runId,
                null,
                null,
                eventType,
                ActorType.SYSTEM,
                "runtime",
                Map.of(
                        "artifact_version_id", artifact.getId(),
                        "artifact_key", artifact.getArtifactKey(),
                        "path", artifact.getPath(),
                        "scope", artifact.getScope().name().toLowerCase(Locale.ROOT),
                        "kind", artifact.getKind().name().toLowerCase(Locale.ROOT),
                        "version_no", artifact.getVersionNo()
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

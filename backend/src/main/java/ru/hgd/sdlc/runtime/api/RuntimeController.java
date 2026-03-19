package ru.hgd.sdlc.runtime.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.ForbiddenException;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.UnprocessableEntityException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.idempotency.application.IdempotencyService;
import ru.hgd.sdlc.runtime.application.RuntimeService;
import ru.hgd.sdlc.runtime.domain.ArtifactVersionEntity;
import ru.hgd.sdlc.runtime.domain.AuditEventEntity;
import ru.hgd.sdlc.runtime.domain.GateInstanceEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;

@RestController
@RequestMapping("/api")
public class RuntimeController {
    private static final Logger log = LoggerFactory.getLogger(RuntimeController.class);

    private final RuntimeService runtimeService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;

    public RuntimeController(
            RuntimeService runtimeService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            TaskExecutor taskExecutor
    ) {
        this.runtimeService = runtimeService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    @PostMapping("/runs")
    public ResponseEntity<RunCreateResponse> createRun(
            @RequestBody RunCreateRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new ValidationException("idempotency_key is required");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request", request);
        payload.put("user", user == null ? null : user.getUsername());
        String requestHash = idempotencyService.hashPayload(payload);

        RunCreateResponse response = idempotencyService.execute(
                request.idempotencyKey(),
                "runs.create",
                requestHash,
                RunCreateResponse.class,
                () -> {
                    RunEntity run = runtimeService.createRun(
                            new RuntimeService.CreateRunCommand(
                                    request.projectId(),
                                    request.targetBranch(),
                                    request.flowCanonicalName(),
                                    request.featureRequest()
                            ),
                            user
                    );
                    RunCreateResponse created = new RunCreateResponse(
                            run.getId(),
                            run.getStatus().name().toLowerCase(),
                            run.getFlowCanonicalName(),
                            run.getResourceVersion()
                    );
                    if (TransactionSynchronizationManager.isSynchronizationActive()) {
                        UUID createdRunId = run.getId();
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                startRunAsync(createdRunId);
                            }
                        });
                    } else {
                        startRunAsync(run.getId());
                    }
                    return created;
                }
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private void startRunAsync(UUID runId) {
        taskExecutor.execute(() -> {
            try {
                runtimeService.startRun(runId);
            } catch (Exception ex) {
                log.error("Failed to start run {}", runId, ex);
            }
        });
    }

    @GetMapping("/runs/{runId}")
    public RunResponse getRun(@PathVariable UUID runId) {
        RunEntity run = runtimeService.getRun(runId);
        GateSummaryResponse currentGate = runtimeService.findCurrentGate(runId).map(this::toGateSummary).orElse(null);
        return RunResponse.from(run, currentGate);
    }

    @GetMapping("/runs")
    public List<RunResponse> listRuns(@RequestParam(name = "limit", defaultValue = "20") int limit) {
        return runtimeService.listRuns(limit).stream()
                .map((run) -> RunResponse.from(
                        run,
                        runtimeService.findCurrentGate(run.getId()).map(this::toGateSummary).orElse(null)
                ))
                .toList();
    }

    @PostMapping("/runs/{runId}/resume")
    public RunResponse resumeRun(@PathVariable UUID runId) {
        RunEntity run = runtimeService.resumeRun(runId);
        GateSummaryResponse currentGate = runtimeService.findCurrentGate(runId).map(this::toGateSummary).orElse(null);
        return RunResponse.from(run, currentGate);
    }

    @PostMapping("/runs/{runId}/cancel")
    public RunResponse cancelRun(@PathVariable UUID runId, @AuthenticationPrincipal User user) {
        RunEntity run = runtimeService.cancelRun(runId, user);
        GateSummaryResponse currentGate = runtimeService.findCurrentGate(runId).map(this::toGateSummary).orElse(null);
        return RunResponse.from(run, currentGate);
    }

    @GetMapping("/runs/{runId}/nodes")
    public List<NodeExecutionResponse> listNodes(@PathVariable UUID runId) {
        return runtimeService.listNodeExecutions(runId).stream().map(NodeExecutionResponse::from).toList();
    }

    @GetMapping("/runs/{runId}/artifacts")
    public List<ArtifactResponse> listArtifacts(@PathVariable UUID runId) {
        return runtimeService.listArtifacts(runId).stream().map(ArtifactResponse::from).toList();
    }

    @GetMapping("/runs/{runId}/artifacts/{artifactVersionId}/content")
    public ArtifactContentResponse getArtifactContent(
            @PathVariable UUID runId,
            @PathVariable UUID artifactVersionId
    ) {
        RuntimeService.ArtifactContentResult result = runtimeService.getArtifactContent(runId, artifactVersionId);
        return new ArtifactContentResponse(
                result.artifact().getId(),
                result.artifact().getRunId(),
                result.artifact().getArtifactKey(),
                result.artifact().getPath(),
                result.content()
        );
    }

    @GetMapping("/runs/{runId}/gates/current")
    public GateSummaryResponse currentGate(@PathVariable UUID runId) {
        return runtimeService.findCurrentGate(runId)
                .map(this::toGateSummary)
                .orElse(null);
    }

    @GetMapping("/gates/inbox")
    public List<GateSummaryResponse> gateInbox(@AuthenticationPrincipal User user) {
        return runtimeService.listInboxGates(user).stream().map(this::toGateSummary).toList();
    }

    @PostMapping("/gates/{gateId}/submit-input")
    public GateActionResponse submitInput(
            @PathVariable UUID gateId,
            @RequestBody GateSubmitInputRequest request,
            @AuthenticationPrincipal User user
    ) {
        RuntimeService.GateActionResult result = runtimeService.submitInput(
                gateId,
                new RuntimeService.SubmitInputCommand(
                        request.expectedGateVersion(),
                        request.artifacts() == null ? List.of() : request.artifacts().stream()
                                .map((artifact) -> new RuntimeService.SubmittedArtifact(
                                        artifact.artifactKey(),
                                        artifact.path(),
                                        artifact.scope(),
                                        artifact.contentBase64()
                                ))
                                .toList(),
                        request.comment()
                ),
                user
        );
        return GateActionResponse.from(result.gate(), result.run(), "on_submit");
    }

    @PostMapping("/gates/{gateId}/approve")
    public GateActionResponse approveGate(
            @PathVariable UUID gateId,
            @RequestBody GateApproveRequest request,
            @AuthenticationPrincipal User user
    ) {
        RuntimeService.GateActionResult result = runtimeService.approveGate(
                gateId,
                new RuntimeService.ApproveGateCommand(
                        request.expectedGateVersion(),
                        request.comment(),
                        request.reviewedArtifactVersionIds()
                ),
                user
        );
        return GateActionResponse.from(result.gate(), result.run(), "on_approve");
    }

    @PostMapping("/gates/{gateId}/request-rework")
    public GateActionResponse requestRework(
            @PathVariable UUID gateId,
            @RequestBody GateReworkRequest request,
            @AuthenticationPrincipal User user
    ) {
        RuntimeService.GateActionResult result = runtimeService.requestRework(
                gateId,
                new RuntimeService.ReworkGateCommand(
                        request.expectedGateVersion(),
                        request.mode(),
                        request.comment(),
                        request.instruction(),
                        request.reviewedArtifactVersionIds()
                ),
                user
        );
        return GateActionResponse.from(result.gate(), result.run(), "on_rework");
    }

    @GetMapping("/runs/{runId}/audit")
    public List<AuditEventResponse> listAudit(@PathVariable UUID runId) {
        return runtimeService.listAuditEvents(runId).stream().map(this::toAuditResponse).toList();
    }

    @GetMapping("/runs/{runId}/audit/{eventId}")
    public AuditEventResponse getAuditEvent(@PathVariable UUID runId, @PathVariable UUID eventId) {
        return toAuditResponse(runtimeService.getAuditEvent(runId, eventId));
    }

    private AuditEventResponse toAuditResponse(AuditEventEntity event) {
        return new AuditEventResponse(
                event.getId(),
                event.getRunId(),
                event.getNodeExecutionId(),
                event.getGateId(),
                event.getSequenceNo(),
                event.getEventType(),
                event.getEventTime(),
                event.getActorType().name().toLowerCase(),
                event.getActorId(),
                parseJson(event.getPayloadJson())
        );
    }

    private GateSummaryResponse toGateSummary(GateInstanceEntity gate) {
        return new GateSummaryResponse(
                gate.getId(),
                gate.getRunId(),
                gate.getNodeExecutionId(),
                gate.getNodeId(),
                gate.getGateKind().name().toLowerCase(),
                gate.getStatus().name().toLowerCase(),
                gate.getAssigneeRole(),
                parseJson(gate.getPayloadJson()),
                gate.getOpenedAt(),
                gate.getClosedAt(),
                gate.getResourceVersion()
        );
    }

    private Object parseJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(rawJson, Object.class);
        } catch (Exception ex) {
            return rawJson;
        }
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<String> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<String> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<String> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    public ResponseEntity<String> handleUnprocessable(UnprocessableEntityException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.getMessage());
    }

    public record RunCreateRequest(
            @JsonProperty("project_id") UUID projectId,
            @JsonProperty("target_branch") String targetBranch,
            @JsonProperty("flow_canonical_name") String flowCanonicalName,
            @JsonProperty("feature_request") String featureRequest,
            @JsonProperty("idempotency_key") String idempotencyKey
    ) {}

    public record RunCreateResponse(
            @JsonProperty("run_id") UUID runId,
            @JsonProperty("status") String status,
            @JsonProperty("flow_canonical_name") String flowCanonicalName,
            @JsonProperty("resource_version") long resourceVersion
    ) {}

    public record RunResponse(
            @JsonProperty("run_id") UUID runId,
            @JsonProperty("project_id") UUID projectId,
            @JsonProperty("target_branch") String targetBranch,
            @JsonProperty("flow_canonical_name") String flowCanonicalName,
            @JsonProperty("status") String status,
            @JsonProperty("current_node_id") String currentNodeId,
            @JsonProperty("feature_request") String featureRequest,
            @JsonProperty("workspace_root") String workspaceRoot,
            @JsonProperty("error_code") String errorCode,
            @JsonProperty("error_message") String errorMessage,
            @JsonProperty("created_by") String createdBy,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("started_at") Instant startedAt,
            @JsonProperty("finished_at") Instant finishedAt,
            @JsonProperty("resource_version") long resourceVersion,
            @JsonProperty("current_gate") GateSummaryResponse currentGate
    ) {
        static RunResponse from(RunEntity run, GateSummaryResponse currentGate) {
            return new RunResponse(
                    run.getId(),
                    run.getProjectId(),
                    run.getTargetBranch(),
                    run.getFlowCanonicalName(),
                    run.getStatus().name().toLowerCase(),
                    run.getCurrentNodeId(),
                    run.getFeatureRequest(),
                    run.getWorkspaceRoot(),
                    run.getErrorCode(),
                    run.getErrorMessage(),
                    run.getCreatedBy(),
                    run.getCreatedAt(),
                    run.getStartedAt(),
                    run.getFinishedAt(),
                    run.getResourceVersion(),
                    currentGate
            );
        }
    }

    public record NodeExecutionResponse(
            @JsonProperty("node_execution_id") UUID nodeExecutionId,
            @JsonProperty("run_id") UUID runId,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("node_kind") String nodeKind,
            @JsonProperty("attempt_no") int attemptNo,
            @JsonProperty("status") String status,
            @JsonProperty("started_at") Instant startedAt,
            @JsonProperty("finished_at") Instant finishedAt,
            @JsonProperty("error_code") String errorCode,
            @JsonProperty("error_message") String errorMessage,
            @JsonProperty("resource_version") long resourceVersion
    ) {
        static NodeExecutionResponse from(ru.hgd.sdlc.runtime.domain.NodeExecutionEntity execution) {
            return new NodeExecutionResponse(
                    execution.getId(),
                    execution.getRunId(),
                    execution.getNodeId(),
                    execution.getNodeKind(),
                    execution.getAttemptNo(),
                    execution.getStatus().name().toLowerCase(),
                    execution.getStartedAt(),
                    execution.getFinishedAt(),
                    execution.getErrorCode(),
                    execution.getErrorMessage(),
                    execution.getResourceVersion()
            );
        }
    }

    public record GateSummaryResponse(
            @JsonProperty("gate_id") UUID gateId,
            @JsonProperty("run_id") UUID runId,
            @JsonProperty("node_execution_id") UUID nodeExecutionId,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("gate_kind") String gateKind,
            @JsonProperty("status") String status,
            @JsonProperty("assignee_role") String assigneeRole,
            @JsonProperty("payload") Object payload,
            @JsonProperty("opened_at") Instant openedAt,
            @JsonProperty("closed_at") Instant closedAt,
            @JsonProperty("resource_version") long resourceVersion
    ) {}

    public record ArtifactResponse(
            @JsonProperty("artifact_version_id") UUID artifactVersionId,
            @JsonProperty("run_id") UUID runId,
            @JsonProperty("node_id") String nodeId,
            @JsonProperty("artifact_key") String artifactKey,
            @JsonProperty("path") String path,
            @JsonProperty("scope") String scope,
            @JsonProperty("kind") String kind,
            @JsonProperty("checksum") String checksum,
            @JsonProperty("size_bytes") Long sizeBytes,
            @JsonProperty("supersedes_artifact_version_id") UUID supersedesArtifactVersionId,
            @JsonProperty("created_at") Instant createdAt
    ) {
        static ArtifactResponse from(ArtifactVersionEntity artifact) {
            return new ArtifactResponse(
                    artifact.getId(),
                    artifact.getRunId(),
                    artifact.getNodeId(),
                    artifact.getArtifactKey(),
                    artifact.getPath(),
                    artifact.getScope().name().toLowerCase(),
                    artifact.getKind().name().toLowerCase(),
                    artifact.getChecksum(),
                    artifact.getSizeBytes(),
                    artifact.getSupersedesArtifactVersionId(),
                    artifact.getCreatedAt()
            );
        }
    }

    public record AuditEventResponse(
            @JsonProperty("event_id") UUID eventId,
            @JsonProperty("run_id") UUID runId,
            @JsonProperty("node_execution_id") UUID nodeExecutionId,
            @JsonProperty("gate_id") UUID gateId,
            @JsonProperty("sequence_no") long sequenceNo,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("event_time") Instant eventTime,
            @JsonProperty("actor_type") String actorType,
            @JsonProperty("actor_id") String actorId,
            @JsonProperty("payload") Object payload
    ) {}

    public record SubmittedArtifactRequest(
            @JsonProperty("artifact_key") String artifactKey,
            @JsonProperty("path") String path,
            @JsonProperty("scope") String scope,
            @JsonProperty("content_base64") String contentBase64
    ) {}

    public record GateSubmitInputRequest(
            @JsonProperty("expected_gate_version") Long expectedGateVersion,
            @JsonProperty("artifacts") List<SubmittedArtifactRequest> artifacts,
            @JsonProperty("comment") String comment
    ) {}

    public record GateApproveRequest(
            @JsonProperty("expected_gate_version") Long expectedGateVersion,
            @JsonProperty("comment") String comment,
            @JsonProperty("reviewed_artifact_version_ids") List<UUID> reviewedArtifactVersionIds
    ) {}

    public record GateReworkRequest(
            @JsonProperty("expected_gate_version") Long expectedGateVersion,
            @JsonProperty("mode") String mode,
            @JsonProperty("comment") String comment,
            @JsonProperty("instruction") String instruction,
            @JsonProperty("reviewed_artifact_version_ids") List<UUID> reviewedArtifactVersionIds
    ) {}

    public record ArtifactContentResponse(
            @JsonProperty("artifact_version_id") UUID artifactVersionId,
            @JsonProperty("run_id") UUID runId,
            @JsonProperty("artifact_key") String artifactKey,
            @JsonProperty("path") String path,
            @JsonProperty("content") String content
    ) {}

    public record GateActionResponse(
            @JsonProperty("gate_id") UUID gateId,
            @JsonProperty("status") String status,
            @JsonProperty("resource_version") long resourceVersion,
            @JsonProperty("next_run_status") String nextRunStatus,
            @JsonProperty("transition") String transition
    ) {
        static GateActionResponse from(GateInstanceEntity gate, RunEntity run, String transition) {
            return new GateActionResponse(
                    gate.getId(),
                    gate.getStatus().name().toLowerCase(),
                    gate.getResourceVersion(),
                    run.getStatus().name().toLowerCase(),
                    transition
            );
        }
    }
}

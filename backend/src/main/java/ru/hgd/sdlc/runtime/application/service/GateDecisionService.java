package ru.hgd.sdlc.runtime.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.ForbiddenException;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.UnprocessableEntityException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.flow.domain.ExecutionContextEntry;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.flow.domain.PathRequirement;
import ru.hgd.sdlc.runtime.application.command.ApproveGateCommand;
import ru.hgd.sdlc.runtime.application.command.ReworkGateCommand;
import ru.hgd.sdlc.runtime.application.command.SubmitInputCommand;
import ru.hgd.sdlc.runtime.application.command.SubmittedArtifact;
import ru.hgd.sdlc.runtime.application.dto.GateActionResult;
import ru.hgd.sdlc.runtime.application.port.IdentityPort;
import ru.hgd.sdlc.runtime.application.port.ProcessExecutionPort;
import ru.hgd.sdlc.runtime.application.port.WorkspacePort;
import ru.hgd.sdlc.runtime.application.RuntimeStepTxService;
import ru.hgd.sdlc.runtime.domain.ArtifactKind;
import ru.hgd.sdlc.runtime.domain.ArtifactScope;
import ru.hgd.sdlc.runtime.domain.ArtifactVersionEntity;
import ru.hgd.sdlc.runtime.domain.AiSessionMode;
import ru.hgd.sdlc.runtime.domain.ActorType;
import ru.hgd.sdlc.runtime.domain.GateInstanceEntity;
import ru.hgd.sdlc.runtime.domain.GateKind;
import ru.hgd.sdlc.runtime.domain.GateStatus;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.NodeExecutionStatus;
import ru.hgd.sdlc.runtime.domain.ReworkSessionPolicy;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.infrastructure.ArtifactVersionRepository;
import ru.hgd.sdlc.runtime.infrastructure.GateInstanceRepository;
import ru.hgd.sdlc.runtime.infrastructure.NodeExecutionRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;
import ru.hgd.sdlc.settings.application.SettingsService;

@Service
public class GateDecisionService {
    private static final List<String> DEFAULT_GATE_ALLOWED_ROLES = List.of(Role.TECH_APPROVER.name());

    private final RunRepository runRepository;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final GateInstanceRepository gateInstanceRepository;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final RuntimeStepTxService runtimeStepTxService;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final ProcessExecutionPort processExecutionPort;
    private final WorkspacePort workspacePort;
    private final IdentityPort identityPort;

    public GateDecisionService(
            RunRepository runRepository,
            NodeExecutionRepository nodeExecutionRepository,
            GateInstanceRepository gateInstanceRepository,
            ArtifactVersionRepository artifactVersionRepository,
            RuntimeStepTxService runtimeStepTxService,
            SettingsService settingsService,
            ObjectMapper objectMapper,
            ProcessExecutionPort processExecutionPort,
            WorkspacePort workspacePort,
            IdentityPort identityPort
    ) {
        this.runRepository = runRepository;
        this.nodeExecutionRepository = nodeExecutionRepository;
        this.gateInstanceRepository = gateInstanceRepository;
        this.artifactVersionRepository = artifactVersionRepository;
        this.runtimeStepTxService = runtimeStepTxService;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.processExecutionPort = processExecutionPort;
        this.workspacePort = workspacePort;
        this.identityPort = identityPort;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GateActionResult submitInput(UUID gateId, SubmitInputCommand command, User user) {
        if (command == null) {
            throw new ValidationException("Request body is required");
        }
        if (command.expectedGateVersion() == null) {
            throw new ValidationException("expected_gate_version is required");
        }
        if (command.artifacts() == null || command.artifacts().isEmpty()) {
            throw new ValidationException("artifacts are required");
        }

        GateInstanceEntity gate = getGateEntity(gateId);
        RunEntity run = getRunEntity(gate.getRunId());
        if (gate.getGateKind() != GateKind.HUMAN_INPUT) {
            throw new ValidationException("Gate is not human_input");
        }
        if (gate.getResourceVersion() != command.expectedGateVersion()) {
            throw new ConflictException("expected_gate_version mismatch");
        }
        if (gate.getStatus() != GateStatus.AWAITING_INPUT && gate.getStatus() != GateStatus.FAILED_VALIDATION) {
            throw new ConflictException("Gate is not accepting input");
        }

        FlowModel flowModel = parseFlowSnapshot(run);
        NodeModel node = requireNode(flowModel, gate.getNodeId());
        enforceGateRole(node, user);

        String nodeKind = normalizeNodeKind(node);
        if (!"human_input".equals(nodeKind)) {
            throw new ValidationException("Current gate node is not human_input");
        }
        NodeExecutionEntity gateExecution = nodeExecutionRepository.findById(gate.getNodeExecutionId())
                .orElseThrow(() -> new ValidationException("Gate execution not found: " + gate.getNodeExecutionId()));
        List<HumanInputEditableArtifact> allowedArtifacts = resolveHumanInputEditableArtifacts(run, node, gateExecution);
        Map<String, HumanInputEditableArtifact> allowedByComposite = new LinkedHashMap<>();
        for (HumanInputEditableArtifact allowed : allowedArtifacts) {
            allowedByComposite.put(artifactCompositeKey(allowed.artifactKey(), allowed.path(), allowed.scope()), allowed);
        }
        if (allowedByComposite.isEmpty()) {
            throw new ValidationException("human_input has no modifiable artifacts in execution_context");
        }
        Map<String, String> checksumBeforeEdit = new LinkedHashMap<>();
        for (HumanInputEditableArtifact allowed : allowedArtifacts) {
            Path outputPath = resolveProducedArtifactPath(run, gateExecution, allowed.scope(), allowed.path());
            checksumBeforeEdit.put(
                    artifactCompositeKey(allowed.artifactKey(), allowed.path(), allowed.scope()),
                    fileChecksumOrNull(outputPath)
            );
        }

        for (SubmittedArtifact artifact : command.artifacts()) {
            validateSubmittedArtifact(artifact);
            HumanInputEditableArtifact expected = allowedByComposite.get(
                    artifactCompositeKey(artifact.artifactKey(), artifact.path(), artifact.scope())
            );
            if (expected == null) {
                throw new ValidationException("Submitted artifact is not allowed for this human_input gate: "
                        + artifact.artifactKey() + " (" + artifact.path() + ")");
            }
            byte[] content = decodeBase64(artifact.contentBase64());
            Path path = resolveProducedArtifactPath(run, gateExecution, expected.scope(), expected.path());
            writeFile(path, content);
            recordArtifactVersion(
                    run,
                    gate.getNodeId(),
                    expected.artifactKey(),
                    path,
                    toArtifactScope(expected.scope()),
                    ArtifactKind.HUMAN_INPUT,
                    content.length
            );
        }
        List<String> validationErrors = validateHumanInputOutputs(run, gateExecution, allowedArtifacts, checksumBeforeEdit);
        if (!validationErrors.isEmpty()) {
            runtimeStepTxService.markGateValidationFailed(
                    run.getId(),
                    gate.getId(),
                    gate.getNodeExecutionId(),
                    trimToNull(command.comment()),
                    validationErrors
            );
            throw new UnprocessableEntityException(validationErrors.getFirst());
        }

        runtimeStepTxService.appendAudit(
                run.getId(),
                gate.getNodeExecutionId(),
                gate.getId(),
                "gate_input_submitted",
                ActorType.HUMAN,
                identityPort.resolveActorId(user),
                mapOf("comment", trimToNull(command.comment()))
        );
        GateInstanceEntity updatedGate = runtimeStepTxService.submitGateInput(
                run.getId(),
                gate.getId(),
                gate.getNodeExecutionId(),
                trimToNull(command.comment())
        );
        runtimeStepTxService.markNodeExecutionSucceeded(run.getId(), gate.getNodeExecutionId(), gate.getNodeId(), null);
        applyTransition(run, null, updatedGate, node.getOnSubmit(), "on_submit");
        return new GateActionResult(updatedGate, getRunEntity(run.getId()));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GateActionResult approveGate(UUID gateId, ApproveGateCommand command, User user) {
        if (command == null || command.expectedGateVersion() == null) {
            throw new ValidationException("expected_gate_version is required");
        }
        GateInstanceEntity gate = getGateEntity(gateId);
        RunEntity run = getRunEntity(gate.getRunId());
        if (gate.getGateKind() != GateKind.HUMAN_APPROVAL) {
            throw new ValidationException("Gate is not human_approval");
        }
        if (gate.getStatus() != GateStatus.AWAITING_DECISION) {
            throw new ConflictException("Gate is not awaiting decision");
        }
        if (gate.getResourceVersion() != command.expectedGateVersion()) {
            throw new ConflictException("expected_gate_version mismatch");
        }

        FlowModel flowModel = parseFlowSnapshot(run);
        NodeModel node = requireNode(flowModel, gate.getNodeId());
        enforceGateRole(node, user);

        GateInstanceEntity updatedGate = runtimeStepTxService.approveGate(
                run.getId(),
                gate.getId(),
                gate.getNodeExecutionId(),
                identityPort.resolveActorId(user),
                trimToNull(command.comment()),
                command.reviewedArtifactVersionIds()
        );
        runtimeStepTxService.markNodeExecutionSucceeded(run.getId(), gate.getNodeExecutionId(), gate.getNodeId(), null);
        applyTransition(run, null, updatedGate, node.getOnApprove(), "on_approve");
        return new GateActionResult(updatedGate, getRunEntity(run.getId()));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GateActionResult requestRework(UUID gateId, ReworkGateCommand command, User user) {
        if (command == null || command.expectedGateVersion() == null) {
            throw new ValidationException("expected_gate_version is required");
        }
        GateInstanceEntity gate = getGateEntity(gateId);
        RunEntity run = getRunEntity(gate.getRunId());
        if (gate.getGateKind() != GateKind.HUMAN_APPROVAL) {
            throw new ValidationException("Gate is not human_approval");
        }
        if (gate.getStatus() != GateStatus.AWAITING_DECISION) {
            throw new ConflictException("Gate is not awaiting decision");
        }
        if (gate.getResourceVersion() != command.expectedGateVersion()) {
            throw new ConflictException("expected_gate_version mismatch");
        }

        FlowModel flowModel = parseFlowSnapshot(run);
        NodeModel node = requireNode(flowModel, gate.getNodeId());
        enforceGateRole(node, user);

        String transitionTarget = resolveReworkTarget(node);
        boolean checkpointConfigured = isCheckpointEnabledForReworkTarget(run, transitionTarget);
        boolean requestedKeepChanges = command.keepChanges() == null ? checkpointConfigured : Boolean.TRUE.equals(command.keepChanges());
        boolean keepChanges = checkpointConfigured ? requestedKeepChanges : false;
        String effectiveMode = keepChanges ? "keep" : "discard";
        AiSessionMode aiSessionMode = run.getAiSessionMode() == null
                ? AiSessionMode.ISOLATED_ATTEMPT_SESSIONS
                : run.getAiSessionMode();

        String sessionPolicyRaw = trimToNull(command.sessionPolicy());
        ReworkSessionPolicy sessionPolicyRequested = null;
        ReworkSessionPolicy sessionPolicyEffective;
        boolean stageForRuntime = false;

        if (!keepChanges) {
            sessionPolicyEffective = ReworkSessionPolicy.NEW_SESSION;
        } else if (aiSessionMode == AiSessionMode.ISOLATED_ATTEMPT_SESSIONS) {
            if (sessionPolicyRaw == null) {
                throw new ValidationException(
                        "session_policy is required when keep_changes=true and ai_session_mode=isolated_attempt_sessions"
                );
            }
            sessionPolicyRequested = parseSessionPolicy(sessionPolicyRaw);
            sessionPolicyEffective = sessionPolicyRequested;
            stageForRuntime = true;
        } else {
            String runSessionId = trimToNull(run.getRunSessionId());
            boolean hasSessionHistory = runSessionId != null
                    && nodeExecutionRepository.existsByRunIdAndAgentSessionId(run.getId(), runSessionId);
            sessionPolicyEffective = hasSessionHistory
                    ? ReworkSessionPolicy.RESUME_PREVIOUS_SESSION
                    : ReworkSessionPolicy.NEW_SESSION;
        }

        if (stageForRuntime && sessionPolicyEffective == ReworkSessionPolicy.RESUME_PREVIOUS_SESSION) {
            ensureResumeSessionExists(run, flowModel, transitionTarget);
        }

        if (!keepChanges) {
            rollbackWorkspaceToCheckpoint(run, transitionTarget, gate.getId());
        }
        String reworkInstruction = trimToNull(command.instruction());
        GateInstanceEntity updatedGate = runtimeStepTxService.requestRework(
                run.getId(),
                gate.getId(),
                gate.getNodeExecutionId(),
                identityPort.resolveActorId(user),
                effectiveMode,
                trimToNull(command.comment()),
                reworkInstruction,
                sessionPolicyRaw,
                sessionPolicyEffective.apiValue(),
                command.reviewedArtifactVersionIds()
        );
        runtimeStepTxService.stagePendingReworkSessionPolicy(
                run.getId(),
                gate.getId(),
                keepChanges,
                sessionPolicyRaw,
                sessionPolicyEffective,
                stageForRuntime
        );
        if (transitionTarget.equals(flowModel.getStartNodeId())) {
            runtimeStepTxService.appendFeatureRequestClarification(run.getId(), gate.getId(), reworkInstruction);
            runtimeStepTxService.replacePendingReworkInstruction(run.getId(), gate.getId(), null);
        } else {
            runtimeStepTxService.replacePendingReworkInstruction(run.getId(), gate.getId(), reworkInstruction);
        }
        runtimeStepTxService.markNodeExecutionSucceeded(run.getId(), gate.getNodeExecutionId(), gate.getNodeId(), null);
        applyTransition(run, null, updatedGate, transitionTarget, "on_rework");
        return new GateActionResult(updatedGate, getRunEntity(run.getId()));
    }

    private List<String> validateHumanInputOutputs(
            RunEntity run,
            NodeExecutionEntity execution,
            List<HumanInputEditableArtifact> allowedArtifacts,
            Map<String, String> checksumBeforeEdit
    ) {
        List<String> errors = new ArrayList<>();
        for (HumanInputEditableArtifact artifact : allowedArtifacts) {
            if (!artifact.required()) {
                continue;
            }
            Path path = resolveProducedArtifactPath(run, execution, artifact.scope(), artifact.path());
            if (!workspacePort.exists(path) || workspacePort.isDirectory(path)) {
                errors.add("Required human_input artifact missing: " + artifact.path());
                continue;
            }
            if (isFileEmptyOrBlank(path)) {
                errors.add("Required human_input artifact is empty: " + artifact.path());
                continue;
            }
            String currentChecksum = fileChecksumOrNull(path);
            String previousChecksum = checksumBeforeEdit.get(
                    artifactCompositeKey(artifact.artifactKey(), artifact.path(), artifact.scope())
            );
            if (previousChecksum != null && previousChecksum.equals(currentChecksum)) {
                errors.add("Required human_input artifact was not modified: " + artifact.path());
            }
        }
        return errors;
    }

    private List<HumanInputEditableArtifact> resolveHumanInputEditableArtifacts(
            RunEntity run,
            NodeModel humanInputNode,
            NodeExecutionEntity execution
    ) {
        FlowModel flowModel = parseFlowSnapshot(run);
        Map<String, NodeModel> nodesById = flowModel.getNodes() == null
                ? Map.of()
                : flowModel.getNodes().stream()
                .filter((candidate) -> candidate != null && candidate.getId() != null && !candidate.getId().isBlank())
                .collect(java.util.stream.Collectors.toMap(NodeModel::getId, Function.identity(), (a, b) -> a));
        List<HumanInputEditableArtifact> result = new ArrayList<>();
        List<ExecutionContextEntry> entries = humanInputNode.getExecutionContext() == null ? List.of() : humanInputNode.getExecutionContext();
        for (ExecutionContextEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String type = normalize(entry.getType());
            if (!"artifact_ref".equals(type)) {
                continue;
            }
            String scope = defaultScope(entry.getScope());
            if (!"run".equals(scope)) {
                continue;
            }
            String sourceNodeId = trimToNull(entry.getNodeId());
            String artifactPath = trimToNull(entry.getPath());
            if (sourceNodeId == null || artifactPath == null) {
                continue;
            }
            NodeModel sourceNode = nodesById.get(sourceNodeId);
            if (sourceNode == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(entry.getModifiable())) {
                continue;
            }
            if (!isProducedArtifactDeclared(sourceNode, artifactPath, scope)) {
                continue;
            }
            Path sourcePath = resolveArtifactRefPath(run, sourceNodeId, scope, artifactPath);
            if (sourcePath == null || !workspacePort.exists(sourcePath) || workspacePort.isDirectory(sourcePath)) {
                continue;
            }
            ArtifactVersionEntity sourceVersion = artifactVersionRepository
                    .findByRunIdAndNodeIdAndArtifactKey(run.getId(), sourceNodeId, artifactKeyForPath(artifactPath))
                    .orElse(null);
            result.add(new HumanInputEditableArtifact(
                    artifactKeyForPath(artifactPath),
                    artifactPath,
                    "run",
                    Boolean.TRUE.equals(entry.getRequired()),
                    sourceNodeId,
                    sourcePath.toString(),
                    sourceVersion == null ? null : sourceVersion.getId().toString()
            ));
        }
        return result;
    }

    private boolean isProducedArtifactDeclared(NodeModel sourceNode, String artifactPath, String scope) {
        if (sourceNode == null || sourceNode.getProducedArtifacts() == null || artifactPath == null || artifactPath.isBlank()) {
            return false;
        }
        for (PathRequirement requirement : sourceNode.getProducedArtifacts()) {
            if (requirement == null || requirement.getPath() == null || requirement.getPath().isBlank()) {
                continue;
            }
            if (!artifactPath.equals(requirement.getPath())) {
                continue;
            }
            if (!scope.equals(defaultScope(requirement.getScope()))) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean isFileEmptyOrBlank(Path path) {
        try {
            if (!workspacePort.exists(path) || workspacePort.isDirectory(path)) {
                return true;
            }
            long size = workspacePort.size(path);
            if (size == 0) {
                return true;
            }
            if (size > 1_048_576) {
                return false;
            }
            String content = workspacePort.readString(path, StandardCharsets.UTF_8);
            return content.trim().isEmpty();
        } catch (IOException ex) {
            return true;
        }
    }

    private void applyTransition(
            RunEntity run,
            NodeExecutionEntity execution,
            GateInstanceEntity gate,
            String targetNodeId,
            String transitionLabel
    ) {
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new ValidationException("Transition target is missing for " + transitionLabel);
        }
        FlowModel flowModel = parseFlowSnapshot(run);
        requireNode(flowModel, targetNodeId);
        runtimeStepTxService.applyTransition(
                run.getId(),
                execution == null ? null : execution.getId(),
                gate == null ? null : gate.getId(),
                targetNodeId,
                transitionLabel
        );
    }

    private void rollbackWorkspaceToCheckpoint(RunEntity run, String reworkTargetNodeId, UUID gateId) {
        NodeExecutionEntity targetExecution = nodeExecutionRepository
                .findFirstByRunIdAndNodeIdOrderByAttemptNoDesc(run.getId(), reworkTargetNodeId)
                .orElseThrow(() -> new ValidationException(
                        "CHECKPOINT_NOT_FOUND_FOR_REWORK: execution not found for node " + reworkTargetNodeId
                ));

        String checkpointCommitSha = trimToNull(targetExecution.getCheckpointCommitSha());
        if (!targetExecution.isCheckpointEnabled() || checkpointCommitSha == null) {
            throw new ValidationException(
                    "CHECKPOINT_NOT_FOUND_FOR_REWORK: checkpoint is missing for node " + reworkTargetNodeId
            );
        }

        Path operationRoot = resolveRunScopeRoot(resolveRunWorkspaceRoot(run)).resolve(".runtime").resolve("rework-reset");
        Path workingDirectory = resolveProjectScopeRoot(resolveRunWorkspaceRoot(run));
        CommandResult resetResult;
        CommandResult cleanResult;
        try {
            ProcessExecutionPort.ProcessExecutionResult result = processExecutionPort.execute(
                    new ProcessExecutionPort.ProcessExecutionRequest(
                            run.getId(),
                            List.of("git", "reset", "--hard", checkpointCommitSha),
                            workingDirectory,
                            settingsService.getAiTimeoutSeconds(),
                            operationRoot.resolve("checkpoint_reset.stdout.log"),
                            operationRoot.resolve("checkpoint_reset.stderr.log"),
                            true
                    )
            );
            resetResult = new CommandResult(
                    result.exitCode(),
                    result.stdout(),
                    result.stderr(),
                    result.stdoutPath(),
                    result.stderrPath()
            );
            ProcessExecutionPort.ProcessExecutionResult clean = processExecutionPort.execute(
                    new ProcessExecutionPort.ProcessExecutionRequest(
                            run.getId(),
                            List.of("git", "clean", "-fd"),
                            workingDirectory,
                            settingsService.getAiTimeoutSeconds(),
                            operationRoot.resolve("checkpoint_clean.stdout.log"),
                            operationRoot.resolve("checkpoint_clean.stderr.log"),
                            true
                    )
            );
            cleanResult = new CommandResult(
                    clean.exitCode(),
                    clean.stdout(),
                    clean.stderr(),
                    clean.stdoutPath(),
                    clean.stderrPath()
            );
        } catch (IOException ex) {
            runtimeStepTxService.appendAudit(
                    run.getId(),
                    null,
                    gateId,
                    "checkpoint_reset_failed",
                    ActorType.SYSTEM,
                    "runtime",
                    mapOf(
                            "rework_target_node_id", reworkTargetNodeId,
                            "checkpoint_commit_sha", checkpointCommitSha,
                            "error", ex.getMessage()
                    )
            );
            throw new ValidationException(
                    "REWORK_RESET_FAILED: git reset --hard failed for checkpoint " + checkpointCommitSha
            );
        }
        if (resetResult.exitCode() != 0) {
            runtimeStepTxService.appendAudit(
                    run.getId(),
                    null,
                    gateId,
                    "checkpoint_reset_failed",
                    ActorType.SYSTEM,
                    "runtime",
                    mapOf(
                            "rework_target_node_id", reworkTargetNodeId,
                            "checkpoint_commit_sha", checkpointCommitSha,
                            "stdout", truncate(resetResult.stdout(), 4000),
                            "stderr", truncate(resetResult.stderr(), 4000)
                    )
            );
            throw new ValidationException(
                    "REWORK_RESET_FAILED: git reset --hard failed for checkpoint " + checkpointCommitSha
            );
        }
        if (cleanResult.exitCode() != 0) {
            runtimeStepTxService.appendAudit(
                    run.getId(),
                    null,
                    gateId,
                    "checkpoint_reset_failed",
                    ActorType.SYSTEM,
                    "runtime",
                    mapOf(
                            "phase", "clean",
                            "rework_target_node_id", reworkTargetNodeId,
                            "checkpoint_commit_sha", checkpointCommitSha,
                            "stdout", truncate(cleanResult.stdout(), 4000),
                            "stderr", truncate(cleanResult.stderr(), 4000)
                    )
            );
            throw new ValidationException(
                    "REWORK_RESET_FAILED: git clean -fd failed after reset for checkpoint " + checkpointCommitSha
            );
        }

        runtimeStepTxService.appendAudit(
                run.getId(),
                null,
                gateId,
                "checkpoint_reset_applied",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "rework_target_node_id", reworkTargetNodeId,
                        "checkpoint_commit_sha", checkpointCommitSha,
                        "reset_stdout", truncate(resetResult.stdout(), 4000),
                        "reset_stderr", truncate(resetResult.stderr(), 4000),
                        "clean_stdout", truncate(cleanResult.stdout(), 4000),
                        "clean_stderr", truncate(cleanResult.stderr(), 4000)
                )
        );
    }

    private String resolveReworkTarget(NodeModel node) {
        if (node.getOnRework() != null && node.getOnRework().getNextNode() != null && !node.getOnRework().getNextNode().isBlank()) {
            return node.getOnRework().getNextNode();
        }
        throw new ValidationException("on_rework target is missing");
    }

    private boolean isCheckpointEnabledForReworkTarget(RunEntity run, String targetNodeId) {
        if (targetNodeId == null || targetNodeId.isBlank()) {
            return false;
        }
        FlowModel flowModel = parseFlowSnapshot(run);
        NodeModel targetNode = requireNode(flowModel, targetNodeId);
        String targetKind = normalizeNodeKind(targetNode);
        return Set.of("ai", "command").contains(targetKind) && Boolean.TRUE.equals(targetNode.getCheckpointBeforeRun());
    }

    private ReworkSessionPolicy parseSessionPolicy(String value) {
        try {
            return ReworkSessionPolicy.fromApiValue(value);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(ex.getMessage());
        }
    }

    private void ensureResumeSessionExists(RunEntity run, FlowModel flowModel, String transitionTargetNodeId) {
        NodeModel transitionTarget = requireNode(flowModel, transitionTargetNodeId);
        if (!"ai".equals(normalizeNodeKind(transitionTarget))) {
            throw new ValidationException(
                    "session_policy=resume_previous_session is only available for AI rework targets"
            );
        }
        boolean sessionExists = nodeExecutionRepository
                .findFirstByRunIdAndNodeIdAndAgentSessionIdIsNotNullOrderByAttemptNoDesc(run.getId(), transitionTargetNodeId)
                .map((nodeExecution) -> trimToNull(nodeExecution.getAgentSessionId()))
                .isPresent();
        if (!sessionExists) {
            throw new ValidationException(
                    "session_policy=resume_previous_session requested, but no previous AI session exists"
            );
        }
    }

    private void enforceGateRole(NodeModel node, User user) {
        List<String> allowedRoles = resolveAllowedRoles(node);
        if (user == null || !user.hasAnyRoleName(allowedRoles)) {
            throw new ForbiddenException("Actor role is not allowed for this gate");
        }
    }

    private List<String> resolveAllowedRoles(NodeModel node) {
        List<String> configured = node.getAllowedRoles() == null ? List.of() : node.getAllowedRoles();
        List<String> normalized = configured.stream()
                .filter((role) -> role != null && !role.isBlank())
                .map(String::trim)
                .toList();
        if (normalized.isEmpty()) {
            return DEFAULT_GATE_ALLOWED_ROLES;
        }
        return normalized;
    }

    private void validateSubmittedArtifact(SubmittedArtifact artifact) {
        if (artifact == null) {
            throw new ValidationException("artifact entry is required");
        }
        if (artifact.artifactKey() == null || artifact.artifactKey().isBlank()) {
            throw new ValidationException("artifact_key is required");
        }
        if (artifact.path() == null || artifact.path().isBlank()) {
            throw new ValidationException("path is required");
        }
        if (artifact.scope() == null || artifact.scope().isBlank()) {
            throw new ValidationException("scope is required");
        }
        if (artifact.contentBase64() == null || artifact.contentBase64().isBlank()) {
            throw new ValidationException("content_base64 is required");
        }
    }

    private void recordArtifactVersion(
            RunEntity run,
            String nodeId,
            String artifactKey,
            Path path,
            ArtifactScope scope,
            ArtifactKind kind,
            Integer explicitSizeBytes
    ) {
        long size = explicitSizeBytes == null ? fileSize(path) : explicitSizeBytes.longValue();
        runtimeStepTxService.recordArtifactVersion(
                run.getId(),
                nodeId,
                artifactKey,
                path.toString(),
                scope,
                kind,
                fileChecksumOrNull(path),
                size
        );
    }

    private FlowModel parseFlowSnapshot(RunEntity run) {
        try {
            return objectMapper.readValue(run.getFlowSnapshotJson(), FlowModel.class);
        } catch (JsonProcessingException ex) {
            throw new ValidationException("Invalid run flow_snapshot_json");
        }
    }

    private NodeModel requireNode(FlowModel flowModel, String nodeId) {
        if (flowModel.getNodes() == null) {
            throw new ValidationException("Flow nodes are empty");
        }
        for (NodeModel node : flowModel.getNodes()) {
            if (nodeId != null && nodeId.equals(node.getId())) {
                return node;
            }
        }
        throw new ValidationException("Node not found in flow: " + nodeId);
    }

    private GateInstanceEntity getGateEntity(UUID gateId) {
        return gateInstanceRepository.findById(gateId)
                .orElseThrow(() -> new NotFoundException("Gate not found: " + gateId));
    }

    private RunEntity getRunEntity(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Run not found: " + runId));
    }

    private Path resolveRunWorkspaceRoot(String workspaceRoot, UUID runId) {
        return Path.of(workspaceRoot).resolve(runId.toString()).toAbsolutePath().normalize();
    }

    private Path resolveProjectScopeRoot(Path runWorkspaceRoot) {
        return runWorkspaceRoot.toAbsolutePath().normalize();
    }

    private Path resolveRunScopeRoot(Path runWorkspaceRoot) {
        return runWorkspaceRoot.resolve(".hgsdlc").toAbsolutePath().normalize();
    }

    private Path resolveRunWorkspaceRoot(RunEntity run) {
        String storedRoot = trimToNull(run.getWorkspaceRoot());
        if (storedRoot != null) {
            return Path.of(storedRoot).toAbsolutePath().normalize();
        }
        return resolveRunWorkspaceRoot(settingsService.getWorkspaceRoot(), run.getId());
    }

    private Path resolveNodeExecutionRoot(RunEntity run, NodeExecutionEntity execution) {
        Path path = resolveRunScopeRoot(resolveRunWorkspaceRoot(run))
                .resolve("nodes")
                .resolve(execution.getNodeId())
                .resolve("attempt-" + execution.getAttemptNo());
        createDirectories(path);
        return path;
    }

    private Path resolvePath(RunEntity run, String scopeRaw, String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("Path is required");
        }
        if (Path.of(value).isAbsolute()) {
            throw new ValidationException("Absolute path is forbidden: " + value);
        }
        Path runWorkspaceRoot = resolveRunWorkspaceRoot(run);
        Path root = "project".equals(defaultScope(scopeRaw))
                ? resolveProjectScopeRoot(runWorkspaceRoot)
                : resolveRunScopeRoot(runWorkspaceRoot);
        Path resolved = root.resolve(value).normalize();
        if (!resolved.startsWith(root)) {
            throw new ValidationException("Path escapes root: " + value);
        }
        return resolved;
    }

    private Path resolveProducedArtifactPath(RunEntity run, NodeExecutionEntity execution, String scopeRaw, String fileName) {
        if ("project".equals(defaultScope(scopeRaw))) {
            return resolvePath(run, scopeRaw, fileName);
        }
        Path nodeDir = resolveNodeExecutionRoot(run, execution);
        Path resolved = nodeDir.resolve(fileName).normalize();
        if (!resolved.startsWith(nodeDir)) {
            throw new ValidationException("Path escapes node dir: " + fileName);
        }
        return resolved;
    }

    private Path resolveArtifactRefPath(RunEntity run, String sourceNodeId, String scopeRaw, String fileName) {
        if ("project".equals(defaultScope(scopeRaw))) {
            return resolvePath(run, "project", fileName);
        }
        if (sourceNodeId == null || sourceNodeId.isBlank()) {
            throw new ValidationException("node_id is required for run-scoped artifact_ref");
        }
        NodeExecutionEntity sourceExecution = nodeExecutionRepository
                .findFirstByRunIdAndNodeIdAndStatusOrderByAttemptNoDesc(
                        run.getId(), sourceNodeId, NodeExecutionStatus.SUCCEEDED)
                .orElse(null);
        if (sourceExecution == null) {
            return null;
        }
        Path nodeDir = resolveRunScopeRoot(resolveRunWorkspaceRoot(run))
                .resolve("nodes")
                .resolve(sourceExecution.getNodeId())
                .resolve("attempt-" + sourceExecution.getAttemptNo());
        return nodeDir.resolve(fileName).normalize();
    }

    private byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid content_base64");
        }
    }

    private String fileChecksumOrNull(Path path) {
        if (!workspacePort.exists(path) || workspacePort.isDirectory(path)) {
            return null;
        }
        try {
            return ChecksumUtil.sha256(new String(workspacePort.readAllBytes(path), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new ValidationException("Failed to read file checksum: " + path);
        }
    }

    private long fileSize(Path path) {
        try {
            return workspacePort.exists(path) ? workspacePort.size(path) : 0L;
        } catch (IOException ex) {
            throw new ValidationException("Failed to read file size: " + path);
        }
    }

    private void writeFile(Path path, byte[] bytes) {
        createDirectories(path.getParent());
        try {
            workspacePort.write(path, bytes);
        } catch (IOException ex) {
            throw new ValidationException("Failed to write file: " + path);
        }
    }

    private void createDirectories(Path path) {
        try {
            workspacePort.createDirectories(path);
        } catch (IOException ex) {
            throw new ValidationException("Failed to create directories: " + path);
        }
    }

    private ArtifactScope toArtifactScope(String scopeRaw) {
        return "project".equals(defaultScope(scopeRaw)) ? ArtifactScope.PROJECT : ArtifactScope.RUN;
    }

    private String defaultScope(String scopeRaw) {
        String normalized = normalize(scopeRaw);
        return "project".equals(normalized) ? "project" : "run";
    }

    private String normalizeNodeKind(NodeModel node) {
        String kind = trimToNull(node.getNodeKind());
        if (kind == null) {
            kind = trimToNull(node.getType());
        }
        if (kind == null && node.getGateKind() != null) {
            kind = node.getGateKind();
        }
        if (kind == null) {
            return "";
        }
        String normalized = normalize(kind);
        if ("external_command".equals(normalized)) {
            return "command";
        }
        return normalized;
    }

    private String artifactKeyForPath(String path) {
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dot = fileName.lastIndexOf('.');
        String key = dot > 0 ? fileName.substring(0, dot) : fileName;
        String trimmed = trimToNull(key);
        return trimmed == null ? "artifact" : trimmed;
    }

    private String artifactCompositeKey(String artifactKey, String path, String scope) {
        return String.join("::",
                trimToNull(artifactKey) == null ? "" : trimToNull(artifactKey),
                trimToNull(path) == null ? "" : trimToNull(path),
                defaultScope(scope)
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
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

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record HumanInputEditableArtifact(
            String artifactKey,
            String path,
            String scope,
            boolean required,
            String sourceNodeId,
            String sourcePath,
            String sourceArtifactVersionId
    ) {}

    private record CommandResult(
            int exitCode,
            String stdout,
            String stderr,
            String stdoutPath,
            String stderrPath
    ) {}
}

package ru.hgd.sdlc.runtime.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.ForbiddenException;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.UnprocessableEntityException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.flow.application.FlowYamlParser;
import ru.hgd.sdlc.flow.domain.ExecutionContextEntry;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.FlowStatus;
import ru.hgd.sdlc.flow.domain.FlowVersion;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.flow.domain.PathRequirement;
import ru.hgd.sdlc.flow.infrastructure.FlowVersionRepository;
import ru.hgd.sdlc.project.domain.Project;
import ru.hgd.sdlc.project.infrastructure.ProjectRepository;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;
import ru.hgd.sdlc.runtime.domain.ArtifactKind;
import ru.hgd.sdlc.runtime.domain.ArtifactScope;
import ru.hgd.sdlc.runtime.domain.ArtifactVersionEntity;
import ru.hgd.sdlc.runtime.domain.ActorType;
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
import ru.hgd.sdlc.settings.application.SettingsService;

@Service
public class RuntimeService {
    private static final Logger log = LoggerFactory.getLogger(RuntimeService.class);
    private static final List<RunStatus> ACTIVE_RUN_STATUSES = List.of(
            RunStatus.CREATED,
            RunStatus.RUNNING,
            RunStatus.WAITING_GATE
    );
    private static final List<GateStatus> OPEN_GATE_STATUSES = List.of(
            GateStatus.AWAITING_INPUT,
            GateStatus.AWAITING_DECISION,
            GateStatus.FAILED_VALIDATION
    );
    private static final int DEFAULT_MAX_TICK_ITERATIONS = 128;

    private final RunRepository runRepository;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final GateInstanceRepository gateInstanceRepository;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final AuditEventRepository auditEventRepository;
    private final ProjectRepository projectRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final RuntimeStepTxService runtimeStepTxService;
    private final AgentPromptBuilder agentPromptBuilder;
    private final ExecutionTraceBuilder executionTraceBuilder;
    private final FlowYamlParser flowYamlParser;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final int maxTickIterations;
    private final int aiTimeoutSeconds;

    public RuntimeService(
            RunRepository runRepository,
            NodeExecutionRepository nodeExecutionRepository,
            GateInstanceRepository gateInstanceRepository,
            ArtifactVersionRepository artifactVersionRepository,
            AuditEventRepository auditEventRepository,
            ProjectRepository projectRepository,
            FlowVersionRepository flowVersionRepository,
            RuleVersionRepository ruleVersionRepository,
            SkillVersionRepository skillVersionRepository,
            RuntimeStepTxService runtimeStepTxService,
            AgentPromptBuilder agentPromptBuilder,
            ExecutionTraceBuilder executionTraceBuilder,
            FlowYamlParser flowYamlParser,
            ObjectMapper objectMapper,
            SettingsService settingsService,
            @Value("${runtime.max-tick-iterations:128}") Integer maxTickIterations,
            @Value("${runtime.ai-timeout-seconds:900}") Integer aiTimeoutSeconds
    ) {
        this.runRepository = runRepository;
        this.nodeExecutionRepository = nodeExecutionRepository;
        this.gateInstanceRepository = gateInstanceRepository;
        this.artifactVersionRepository = artifactVersionRepository;
        this.auditEventRepository = auditEventRepository;
        this.projectRepository = projectRepository;
        this.flowVersionRepository = flowVersionRepository;
        this.ruleVersionRepository = ruleVersionRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.runtimeStepTxService = runtimeStepTxService;
        this.agentPromptBuilder = agentPromptBuilder;
        this.executionTraceBuilder = executionTraceBuilder;
        this.flowYamlParser = flowYamlParser;
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.maxTickIterations = maxTickIterations == null ? DEFAULT_MAX_TICK_ITERATIONS : maxTickIterations;
        this.aiTimeoutSeconds = aiTimeoutSeconds == null ? 900 : aiTimeoutSeconds;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RunEntity createRun(CreateRunCommand command, User user) {
        validateCreateRunCommand(command);
        Project project = projectRepository.findById(command.projectId())
                .orElseThrow(() -> new NotFoundException("Project not found: " + command.projectId()));
        FlowVersion flowVersion = flowVersionRepository.findFirstByCanonicalNameAndStatus(
                        command.flowCanonicalName(),
                        FlowStatus.PUBLISHED
                )
                .orElseThrow(() -> new NotFoundException("Flow not found or not published: " + command.flowCanonicalName()));
        FlowModel flowModel = flowYamlParser.parse(flowVersion.getFlowYaml());
        if (flowModel.getNodes() == null || flowModel.getNodes().isEmpty()) {
            throw new ValidationException("Flow has no nodes");
        }
        if (flowModel.getStartNodeId() == null || flowModel.getStartNodeId().isBlank()) {
            throw new ValidationException("Flow start_node_id is required");
        }

        boolean activeRunExists = runRepository.existsByProjectIdAndTargetBranchAndStatusIn(
                project.getId(),
                normalizeBranch(command.targetBranch()),
                ACTIVE_RUN_STATUSES
        );
        if (activeRunExists) {
            throw new ConflictException("Active run already exists for project and target branch");
        }

        UUID runId = UUID.randomUUID();
        Path runWorkspaceRoot = resolveRunWorkspaceRoot(settingsService.getWorkspaceRoot(), runId);
        createDirectories(runWorkspaceRoot);

        List<String> manifestEntries = List.of();
        return runtimeStepTxService.createRun(
                runId,
                project.getId(),
                normalizeBranch(command.targetBranch()),
                flowVersion.getCanonicalName(),
                toJson(flowModel),
                flowModel.getStartNodeId(),
                command.featureRequest().trim(),
                trimToNull(command.contextRootDir()),
                toJson(manifestEntries),
                runWorkspaceRoot.toString(),
                resolveActorId(user),
                Instant.now()
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void startRun(UUID runId) {
        RunEntity run = getRunEntity(runId);
        if (run.getStatus() == RunStatus.CREATED) {
            run = runtimeStepTxService.markRunStarted(runId, Instant.now());
        }
        if (run.getStatus() == RunStatus.RUNNING) {
            ensureWorkspacePrepared(run);
            tick(runId);
        }
    }

    private void ensureWorkspacePrepared(RunEntity run) {
        Path runWorkspaceRoot = resolveRunWorkspaceRoot(run);
        Path projectRoot = resolveProjectScopeRoot(runWorkspaceRoot);
        Path runScopeRoot = resolveRunScopeRoot(runWorkspaceRoot);
        createDirectories(runWorkspaceRoot);

        boolean checkoutCompleted = Files.exists(projectRoot.resolve(".git"));
        if (!checkoutCompleted) {
            Project project = resolveProject(run.getProjectId());
            runtimeStepTxService.appendAudit(
                    run.getId(),
                    null,
                    null,
                    "checkout_started",
                    ActorType.SYSTEM,
                    "runtime",
                    mapOf(
                            "repo_url", redactRepoUrl(project.getRepoUrl()),
                            "target_branch", run.getTargetBranch(),
                            "project_root", projectRoot.toString()
                    )
            );
            try {
                CommandResult checkoutResult = runGitCheckout(run, projectRoot);
                runtimeStepTxService.appendAudit(
                        run.getId(),
                        null,
                        null,
                        "checkout_finished",
                        ActorType.SYSTEM,
                        "runtime",
                        mapOf(
                                "project_root", projectRoot.toString(),
                                "target_branch", run.getTargetBranch(),
                                "head", readGitHead(run, projectRoot),
                                "stdout_path", checkoutResult.stdoutPath(),
                                "stderr_path", checkoutResult.stderrPath(),
                                "stdout", truncate(checkoutResult.stdout(), 12000),
                                "stderr", truncate(checkoutResult.stderr(), 12000)
                        )
                );
            } catch (NodeFailureException ex) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("error_code", ex.errorCode);
                payload.put("error_message", ex.getMessage());
                if (ex.details != null && !ex.details.isEmpty()) {
                    payload.putAll(ex.details);
                }
                runtimeStepTxService.appendAudit(
                        run.getId(),
                        null,
                        null,
                        "checkout_failed",
                        ActorType.SYSTEM,
                        "runtime",
                        payload
                );
                failRun(run, ex.errorCode, ex.getMessage());
                throw ex;
            }
        }

        createDirectories(runScopeRoot.resolve("context"));
        createDirectories(runScopeRoot.resolve("nodes"));
        createDirectories(runScopeRoot.resolve("logs"));

        writeContextManifest(
                runScopeRoot,
                run.getContextRootDir(),
                run.getFeatureRequest(),
                parseContextManifestEntries(run.getContextFileManifestJson())
        );

        runtimeStepTxService.appendAudit(
                run.getId(),
                null,
                null,
                "workspace_prepared",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "workspace_root", runWorkspaceRoot.toString(),
                        "project_root", projectRoot.toString(),
                        "run_scope_root", runScopeRoot.toString()
                )
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RunEntity resumeRun(UUID runId) {
        RunEntity run = getRunEntity(runId);
        if (run.getStatus() == RunStatus.CREATED || run.getStatus() == RunStatus.RUNNING) {
            startRun(runId);
        }
        return getRunEntity(runId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RunEntity cancelRun(UUID runId, User user) {
        return runtimeStepTxService.cancelRun(runId, resolveActorId(user));
    }

    @Transactional(readOnly = true)
    public RunEntity getRun(UUID runId) {
        return getRunEntity(runId);
    }

    @Transactional(readOnly = true)
    public List<NodeExecutionEntity> listNodeExecutions(UUID runId) {
        getRunEntity(runId);
        return nodeExecutionRepository.findByRunIdOrderByStartedAtAsc(runId);
    }

    @Transactional(readOnly = true)
    public List<ArtifactVersionEntity> listArtifacts(UUID runId) {
        getRunEntity(runId);
        return artifactVersionRepository.findByRunIdOrderByCreatedAtDesc(runId);
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
    public List<GateInstanceEntity> listInboxGates(User user) {
        String role = user == null || user.getRole() == null ? null : user.getRole().name();
        List<GateInstanceEntity> all = gateInstanceRepository.findByStatusInOrderByOpenedAtAsc(OPEN_GATE_STATUSES);
        if (role == null) {
            return all;
        }
        return all.stream()
                .filter((gate) -> gate.getAssigneeRole() == null || gate.getAssigneeRole().isBlank() || role.equals(gate.getAssigneeRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditEventEntity> listAuditEvents(UUID runId) {
        getRunEntity(runId);
        return auditEventRepository.findByRunIdOrderBySequenceNoAsc(runId);
    }

    @Transactional(readOnly = true)
    public List<RunEntity> listRunsByProject(UUID projectId, int limit) {
        List<RunEntity> runs = runRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        if (limit <= 0 || runs.size() <= limit) {
            return runs;
        }
        return runs.subList(0, limit);
    }

    @Transactional(readOnly = true)
    public List<RunEntity> listRuns(int limit) {
        List<RunEntity> runs = runRepository.findAllByOrderByCreatedAtDesc();
        if (limit <= 0 || runs.size() <= limit) {
            return runs;
        }
        return runs.subList(0, limit);
    }

    @Transactional(readOnly = true)
    public AuditEventEntity getAuditEvent(UUID runId, UUID eventId) {
        getRunEntity(runId);
        return auditEventRepository.findByIdAndRunId(eventId, runId)
                .orElseThrow(() -> new NotFoundException("Audit event not found: " + eventId));
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

        for (SubmittedArtifact artifact : command.artifacts()) {
            validateSubmittedArtifact(artifact);
            byte[] content = decodeBase64(artifact.contentBase64());
            Path path = resolvePath(run, artifact.scope(), artifact.path());
            writeFile(path, content);
            recordArtifactVersion(
                    run,
                    gate.getNodeId(),
                    artifact.artifactKey(),
                    path,
                    toArtifactScope(artifact.scope()),
                    ArtifactKind.HUMAN_INPUT,
                    content.length
            );
        }

        List<String> validationErrors = validateHumanInputOutputs(run, node, command.artifacts());
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
                resolveActorId(user),
                mapOf("comment", trimToNull(command.comment()))
        );
        GateInstanceEntity updatedGate = runtimeStepTxService.submitGateInput(
                run.getId(),
                gate.getId(),
                gate.getNodeExecutionId(),
                trimToNull(command.comment())
        );
        runtimeStepTxService.markNodeExecutionSucceeded(run.getId(), gate.getNodeExecutionId(), gate.getNodeId());
        applyTransition(run, null, updatedGate, node.getOnSubmit(), "on_submit");
        tick(run.getId());
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
                resolveActorId(user),
                trimToNull(command.comment()),
                command.reviewedArtifactVersionIds()
        );
        runtimeStepTxService.markNodeExecutionSucceeded(run.getId(), gate.getNodeExecutionId(), gate.getNodeId());
        applyTransition(run, null, updatedGate, node.getOnApprove(), "on_approve");
        tick(run.getId());
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

        String transitionTarget = resolveReworkTarget(node, command.mode());
        GateInstanceEntity updatedGate = runtimeStepTxService.requestRework(
                run.getId(),
                gate.getId(),
                gate.getNodeExecutionId(),
                resolveActorId(user),
                trimToNull(command.mode()),
                trimToNull(command.comment()),
                command.reviewedArtifactVersionIds()
        );
        runtimeStepTxService.markNodeExecutionSucceeded(run.getId(), gate.getNodeExecutionId(), gate.getNodeId());
        applyTransition(run, null, updatedGate, transitionTarget, "on_rework");
        tick(run.getId());
        return new GateActionResult(updatedGate, getRunEntity(run.getId()));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void recoverActiveRuns() {
        List<RunEntity> activeRuns = runRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(RunStatus.RUNNING, RunStatus.WAITING_GATE)
        );
        for (RunEntity run : activeRuns) {
            runtimeStepTxService.appendAudit(run.getId(), null, null, "run_recovered", ActorType.SYSTEM, "runtime", Map.of());
            if (run.getStatus() == RunStatus.RUNNING) {
                tick(run.getId());
            }
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void tick(UUID runId) {
        int iterations = 0;
        while (iterations < maxTickIterations) {
            iterations++;
            RunEntity run = getRunEntity(runId);
            if (run.getStatus() != RunStatus.RUNNING) {
                return;
            }
            boolean continueLoop = executeCurrentNode(run);
            if (!continueLoop) {
                return;
            }
        }
        RunEntity run = getRunEntity(runId);
        failRun(run, "RUNTIME_TICK_OVERFLOW", "Max tick iterations exceeded");
    }

    private void runTickSafely(UUID runId) {
        try {
            tick(runId);
        } catch (RuntimeException ex) {
            log.error("runtime tick failed after commit for run_id={}", runId, ex);
            throw ex;
        }
    }

    private boolean executeCurrentNode(RunEntity run) {
        FlowModel flowModel = parseFlowSnapshot(run);
        NodeModel node = requireNode(flowModel, run.getCurrentNodeId());
        String nodeKind = normalizeNodeKind(node);

        NodeExecutionEntity execution = createNodeExecution(run, node, nodeKind);

        try {
            return switch (nodeKind) {
                case "ai" -> executeAiNode(run, node, execution);
                case "command" -> executeCommandNode(run, node, execution);
                case "human_input" -> openGate(run, node, execution, GateKind.HUMAN_INPUT, GateStatus.AWAITING_INPUT);
                case "human_approval" -> openGate(run, node, execution, GateKind.HUMAN_APPROVAL, GateStatus.AWAITING_DECISION);
                case "terminal" -> completeTerminalNode(run, node, execution);
                default -> throw new NodeFailureException(
                        "UNSUPPORTED_NODE_KIND",
                        "Unsupported node kind: " + nodeKind,
                        true
                );
            };
        } catch (NodeFailureException ex) {
            runtimeStepTxService.markNodeExecutionFailed(run.getId(), execution.getId(), ex.errorCode, ex.getMessage(), ex.auditEventType);
            if ("ai".equals(nodeKind) && node.getOnFailure() != null && !node.getOnFailure().isBlank()) {
                applyTransition(run, execution, null, node.getOnFailure(), "on_failure");
                return true;
            }
            failRun(run, ex.errorCode, ex.getMessage());
            return false;
        }
    }

    private boolean executeAiNode(RunEntity run, NodeModel node, NodeExecutionEntity execution) {
        FlowModel flowModel = parseFlowSnapshot(run);
        List<Map<String, Object>> resolvedContext = resolveExecutionContext(run, node);
        AgentInvocationContext agentInvocationContext = materializeAgentWorkspace(run, flowModel, node, execution, resolvedContext);
        runtimeStepTxService.appendAudit(
                run.getId(),
                execution.getId(),
                null,
                "prompt_package_built",
                ActorType.SYSTEM,
                "runtime",
                executionTraceBuilder.promptPackageBuiltPayload(
                        run.getFlowCanonicalName(),
                        node,
                        execution.getAttemptNo(),
                        agentInvocationContext.promptPackage(),
                        resolvedContext,
                        flowModel,
                        agentInvocationContext.promptPath().toString(),
                        agentInvocationContext.rulePath().toString(),
                        agentInvocationContext.skillsRoot().toString()
                )
        );
        runtimeStepTxService.appendAudit(
                run.getId(),
                execution.getId(),
                null,
                "agent_invocation_started",
                ActorType.SYSTEM,
                "runtime",
                executionTraceBuilder.agentInvocationStartedPayload(
                        node,
                        agentInvocationContext.promptPackage().promptChecksum(),
                        agentInvocationContext.workingDirectory().toString(),
                        agentInvocationContext.command()
                )
        );

        Map<String, String> beforeMutations = snapshotMutations(run, node.getExpectedMutations());
        CommandResult agentResult;
        try {
            agentResult = runProcess(
                    agentInvocationContext.command(),
                    agentInvocationContext.workingDirectory(),
                    aiTimeoutSeconds,
                    agentInvocationContext.stdoutPath(),
                    agentInvocationContext.stderrPath()
            );
        } catch (IOException ex) {
            throw new NodeFailureException("AGENT_EXECUTION_FAILED", ex.getMessage(), false);
        }
        if (agentResult.exitCode() != 0) {
            throw new NodeFailureException(
                    "AGENT_EXECUTION_FAILED",
                    "Qwen execution failed with exit code " + agentResult.exitCode(),
                    false
            );
        }
        validateNodeOutputs(run, node, execution, beforeMutations);

        runtimeStepTxService.appendAudit(
                run.getId(),
                execution.getId(),
                null,
                "agent_invocation_finished",
                ActorType.AGENT,
                "qwen",
                executionTraceBuilder.agentInvocationFinishedPayload(
                        node,
                        agentInvocationContext.promptPackage().promptChecksum(),
                        agentResult
                )
        );

        runtimeStepTxService.markNodeExecutionSucceeded(run.getId(), execution.getId(), node.getId());
        applyTransition(run, execution, null, node.getOnSuccess(), "on_success");
        return true;
    }

    private boolean executeCommandNode(RunEntity run, NodeModel node, NodeExecutionEntity execution) {
        List<Map<String, Object>> resolvedContext = resolveExecutionContext(run, node);
        Path nodeExecutionDir = resolveNodeExecutionRoot(run, execution);
        Path stdoutPath = nodeExecutionDir.resolve("command.stdout.log");
        Path stderrPath = nodeExecutionDir.resolve("command.stderr.log");
        runtimeStepTxService.appendAudit(
                run.getId(),
                execution.getId(),
                null,
                "command_invocation_started",
                ActorType.SYSTEM,
                "runtime",
                mapOf("node_id", node.getId(), "execution_context", resolvedContext)
        );

        Map<String, String> beforeMutations = snapshotMutations(run, node.getExpectedMutations());
        CommandResult commandResult = executeCommand(run, node, stdoutPath, stderrPath);
        materializeDeclaredArtifacts(run, node, execution, "command", commandResult);
        validateNodeOutputs(run, node, execution, beforeMutations);

        runtimeStepTxService.appendAudit(
                run.getId(),
                execution.getId(),
                null,
                "command_invocation_finished",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "exit_code", commandResult.exitCode(),
                        "stdout_path", commandResult.stdoutPath(),
                        "stderr_path", commandResult.stderrPath(),
                        "stdout", truncate(commandResult.stdout(), 12000),
                        "stderr", truncate(commandResult.stderr(), 12000)
                )
        );

        runtimeStepTxService.markNodeExecutionSucceeded(run.getId(), execution.getId(), node.getId());
        applyTransition(run, execution, null, node.getOnSuccess(), "on_success");
        return true;
    }

    private boolean openGate(
            RunEntity run,
            NodeModel node,
            NodeExecutionEntity execution,
            GateKind gateKind,
            GateStatus initialStatus
    ) {
        runtimeStepTxService.openGate(
                run.getId(),
                execution.getId(),
                node.getId(),
                gateKind,
                initialStatus,
                firstAllowedRole(node.getAllowedRoles())
        );
        return false;
    }

    private boolean completeTerminalNode(RunEntity run, NodeModel node, NodeExecutionEntity execution) {
        runtimeStepTxService.completeRun(run.getId(), execution.getId(), node.getId());
        return false;
    }

    private CommandResult executeCommand(RunEntity run, NodeModel node, Path stdoutPath, Path stderrPath) {
        String instruction = trimToNull(node.getInstruction());
        if (instruction == null) {
            writeFile(stdoutPath, new byte[0]);
            writeFile(stderrPath, new byte[0]);
            return new CommandResult(0, "", "", stdoutPath.toString(), stderrPath.toString());
        }
        Path workingDirectory = resolveProjectScopeRoot(resolveRunWorkspaceRoot(run));
        try {
            CommandResult commandResult = runProcess(
                    List.of("zsh", "-lc", instruction),
                    workingDirectory,
                    aiTimeoutSeconds,
                    stdoutPath,
                    stderrPath
            );
            int exitCode = commandResult.exitCode();
            Set<Integer> successExitCodes = (node.getSuccessExitCodes() == null || node.getSuccessExitCodes().isEmpty())
                    ? Set.of(0)
                    : Set.copyOf(node.getSuccessExitCodes());
            if (!successExitCodes.contains(exitCode)) {
                throw new NodeFailureException(
                        "COMMAND_EXECUTION_FAILED",
                        "Command node failed with exit code " + exitCode,
                        false
                );
            }
            return commandResult;
        } catch (IOException ex) {
            throw new NodeFailureException("COMMAND_EXECUTION_FAILED", ex.getMessage(), false);
        }
    }

    private AgentInvocationContext materializeAgentWorkspace(
            RunEntity run,
            FlowModel flowModel,
            NodeModel node,
            NodeExecutionEntity execution,
            List<Map<String, Object>> resolvedContext
    ) {
        String codingAgent = normalize(trimToNull(flowModel.getCodingAgent()));
        if (!"qwen".equals(codingAgent)) {
            throw new NodeFailureException(
                    "UNSUPPORTED_CODING_AGENT",
                    "Only qwen coding_agent is supported in runtime",
                    false
            );
        }

        Path projectRoot = resolveProjectRoot(run);
        Path qwenRoot = projectRoot.resolve(".qwen");
        Path rulesPath = qwenRoot.resolve("QWEN.md");
        Path skillsRoot = qwenRoot.resolve("skills");
        Path nodeExecutionRoot = resolveNodeExecutionRoot(run, execution);
        Path promptPath = nodeExecutionRoot.resolve("prompt.md");
        Path stdoutPath = nodeExecutionRoot.resolve("agent.stdout.log");
        Path stderrPath = nodeExecutionRoot.resolve("agent.stderr.log");

        createDirectories(qwenRoot);
        createDirectories(skillsRoot);
        deleteDirectoryContents(skillsRoot);

        List<RuleVersion> rules = resolveFlowRules(flowModel);
        List<SkillVersion> skills = resolveNodeSkills(node);

        String renderedRules = renderQwenRules(flowModel, rules);
        writeFile(rulesPath, renderedRules.getBytes(StandardCharsets.UTF_8));
        runtimeStepTxService.appendAudit(
                run.getId(),
                execution.getId(),
                null,
                "rules_materialized",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "path", rulesPath.toString(),
                        "rule_refs", flowModel.getRuleRefs() == null ? List.of() : flowModel.getRuleRefs()
                )
        );

        for (SkillVersion skill : skills) {
            Path skillDir = skillsRoot.resolve(skill.getCanonicalName());
            Path skillFile = skillDir.resolve("SKILL.md");
            createDirectories(skillDir);
            writeFile(skillFile, skill.getSkillMarkdown().getBytes(StandardCharsets.UTF_8));
        }
        runtimeStepTxService.appendAudit(
                run.getId(),
                execution.getId(),
                null,
                "skills_materialized",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "skills_root", skillsRoot.toString(),
                        "skill_refs", node.getSkillRefs() == null ? List.of() : node.getSkillRefs()
                )
        );

        AgentPromptBuilder.AgentPromptPackage promptPackage = agentPromptBuilder.build(run, flowModel, node, resolvedContext);
        writeFile(promptPath, promptPackage.prompt().getBytes(StandardCharsets.UTF_8));

        List<String> command = List.of(
                "qwen",
                "--approval-mode",
                "yolo",
                "--output-format",
                "json",
                "--channel",
                "CI",
                promptPackage.prompt()
        );
        return new AgentInvocationContext(
                projectRoot,
                command,
                promptPath,
                rulesPath,
                skillsRoot,
                stdoutPath,
                stderrPath,
                promptPackage
        );
    }

    private List<RuleVersion> resolveFlowRules(FlowModel flowModel) {
        List<String> refs = flowModel.getRuleRefs() == null ? List.of() : flowModel.getRuleRefs();
        List<RuleVersion> rules = new ArrayList<>();
        for (String ref : refs) {
            if (ref == null || ref.isBlank()) {
                continue;
            }
            RuleVersion rule = ruleVersionRepository.findFirstByCanonicalNameAndStatus(ref, RuleStatus.PUBLISHED)
                    .orElseThrow(() -> new NodeFailureException(
                            "RULE_NOT_FOUND",
                            "Published rule not found: " + ref,
                            false
                    ));
            if (!"QWEN".equals(rule.getCodingAgent().name())) {
                throw new NodeFailureException(
                        "RULE_PROVIDER_MISMATCH",
                        "Rule provider must be qwen for qwen flow: " + ref,
                        false
                );
            }
            rules.add(rule);
        }
        return rules;
    }

    private List<SkillVersion> resolveNodeSkills(NodeModel node) {
        List<String> refs = node.getSkillRefs() == null ? List.of() : node.getSkillRefs();
        List<SkillVersion> skills = new ArrayList<>();
        for (String ref : refs) {
            if (ref == null || ref.isBlank()) {
                continue;
            }
            SkillVersion skill = skillVersionRepository.findFirstByCanonicalNameAndStatus(ref, SkillStatus.PUBLISHED)
                    .orElseThrow(() -> new NodeFailureException(
                            "SKILL_NOT_FOUND",
                            "Published skill not found: " + ref,
                            false
                    ));
            if (!"QWEN".equals(skill.getCodingAgent().name())) {
                throw new NodeFailureException(
                        "SKILL_PROVIDER_MISMATCH",
                        "Skill provider must be qwen for qwen flow: " + ref,
                        false
                );
            }
            skills.add(skill);
        }
        return skills;
    }

    private String renderQwenRules(FlowModel flowModel, List<RuleVersion> rules) {
        StringBuilder sb = new StringBuilder();
        sb.append("# HGSDLC Runtime Rules\n\n");
        sb.append("flow: ").append(flowModel.getCanonicalName() == null ? flowModel.getId() : flowModel.getCanonicalName()).append("\n");
        sb.append("coding_agent: ").append(flowModel.getCodingAgent()).append("\n\n");
        if (rules.isEmpty()) {
            sb.append("No flow-level rules provided.\n");
            return sb.toString();
        }
        for (RuleVersion rule : rules) {
            sb.append("## ").append(rule.getCanonicalName()).append("\n\n");
            sb.append(rule.getRuleMarkdown()).append("\n\n");
        }
        return sb.toString();
    }

    private CommandResult runProcess(
            List<String> command,
            Path workingDirectory,
            int timeoutSeconds,
            Path stdoutPath,
            Path stderrPath
    ) throws IOException {
        createDirectories(stdoutPath.getParent());
        createDirectories(stderrPath.getParent());
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDirectory != null) {
            pb.directory(workingDirectory.toFile());
        }
        pb.redirectOutput(stdoutPath.toFile());
        pb.redirectError(stderrPath.toFile());
        Process process = pb.start();
        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Process interrupted", ex);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Process timeout after " + timeoutSeconds + "s");
        }
        String stdout = readFile(stdoutPath);
        String stderr = readFile(stderrPath);
        return new CommandResult(
                process.exitValue(),
                stdout,
                stderr,
                stdoutPath.toString(),
                stderrPath.toString()
        );
    }

    private String readFile(Path path) throws IOException {
        if (!Files.exists(path)) {
            return "";
        }
        return Files.readString(path, StandardCharsets.UTF_8);
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

    private void deleteDirectoryContents(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder())
                    .filter((path) -> !path.equals(directory))
                    .forEach((path) -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new ValidationException("Failed to clean directory: " + directory);
                        }
                    });
        } catch (IOException ex) {
            throw new ValidationException("Failed to clean directory: " + directory);
        }
    }

    private void materializeDeclaredArtifacts(
            RunEntity run,
            NodeModel node,
            NodeExecutionEntity execution,
            String nodeKind,
            CommandResult commandResult
    ) {
        List<PathRequirement> declared = node.getProducedArtifacts() == null ? List.of() : node.getProducedArtifacts();
        for (PathRequirement requirement : declared) {
            if (requirement == null || requirement.getPath() == null || requirement.getPath().isBlank()) {
                continue;
            }
            String scope = defaultScope(requirement.getScope());
            Path path = resolvePath(run, scope, requirement.getPath());
            String content = renderArtifactContent(run, node, nodeKind, commandResult, requirement.getPath());
            writeFile(path, content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String renderArtifactContent(
            RunEntity run,
            NodeModel node,
            String nodeKind,
            CommandResult commandResult,
            String path
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(node.getTitle() == null ? node.getId() : node.getTitle()).append("\n\n");
        sb.append("- run_id: ").append(run.getId()).append("\n");
        sb.append("- flow: ").append(run.getFlowCanonicalName()).append("\n");
        sb.append("- node_id: ").append(node.getId()).append("\n");
        sb.append("- node_kind: ").append(nodeKind).append("\n");
        sb.append("- artifact_path: ").append(path).append("\n");
        sb.append("- generated_at: ").append(Instant.now()).append("\n\n");
        sb.append("## Feature request\n\n");
        sb.append(run.getFeatureRequest()).append("\n\n");
        if (node.getInstruction() != null && !node.getInstruction().isBlank()) {
            sb.append("## Node instruction\n\n");
            sb.append(node.getInstruction()).append("\n\n");
        }
        if (commandResult != null) {
            sb.append("## Command output\n\n");
            sb.append("```text\n");
            if (commandResult.stdout() != null && !commandResult.stdout().isBlank()) {
                sb.append(commandResult.stdout()).append("\n");
            }
            if (commandResult.stderr() != null && !commandResult.stderr().isBlank()) {
                sb.append("STDERR:\n").append(commandResult.stderr()).append("\n");
            }
            sb.append("```\n");
        }
        return sb.toString();
    }

    private List<String> validateHumanInputOutputs(
            RunEntity run,
            NodeModel node,
            List<SubmittedArtifact> submittedArtifacts
    ) {
        List<String> errors = new ArrayList<>();
        String outputArtifact = trimToNull(node.getOutputArtifact());
        if (outputArtifact != null) {
            boolean present = submittedArtifacts.stream().anyMatch((artifact) -> outputArtifact.equals(artifact.artifactKey()));
            if (!present) {
                errors.add("Missing required output_artifact: " + outputArtifact);
            }
        }
        if (node.getProducedArtifacts() != null) {
            for (PathRequirement requirement : node.getProducedArtifacts()) {
                if (requirement == null || !Boolean.TRUE.equals(requirement.getRequired())) {
                    continue;
                }
                if (requirement.getPath() == null || requirement.getPath().isBlank()) {
                    errors.add("produced_artifacts path is required");
                    continue;
                }
                Path path = resolvePath(run, requirement.getScope(), requirement.getPath());
                if (!Files.exists(path)) {
                    errors.add("Required produced artifact missing: " + requirement.getPath());
                }
            }
        }
        return errors;
    }

    private void validateNodeOutputs(
            RunEntity run,
            NodeModel node,
            NodeExecutionEntity execution,
            Map<String, String> beforeMutations
    ) {
        List<PathRequirement> produced = node.getProducedArtifacts() == null ? List.of() : node.getProducedArtifacts();
        for (PathRequirement requirement : produced) {
            if (requirement == null || requirement.getPath() == null || requirement.getPath().isBlank()) {
                continue;
            }
            Path path = resolvePath(run, requirement.getScope(), requirement.getPath());
            boolean exists = Files.exists(path);
            if (Boolean.TRUE.equals(requirement.getRequired()) && !exists) {
                throw new NodeFailureException(
                        "NODE_VALIDATION_FAILED",
                        "Required produced artifact missing: " + requirement.getPath(),
                        true
                );
            }
            if (exists) {
                recordArtifactVersion(
                        run,
                        execution.getNodeId(),
                        artifactKeyForPath(requirement.getPath()),
                        path,
                        toArtifactScope(requirement.getScope()),
                        ArtifactKind.PRODUCED,
                        null
                );
            }
        }

        List<PathRequirement> mutations = node.getExpectedMutations() == null ? List.of() : node.getExpectedMutations();
        for (PathRequirement mutation : mutations) {
            if (mutation == null || mutation.getPath() == null || mutation.getPath().isBlank()) {
                continue;
            }
            Path path = resolvePath(run, mutation.getScope(), mutation.getPath());
            String beforeChecksum = beforeMutations.get(path.toString());
            String afterChecksum = fileChecksumOrNull(path);
            boolean changed = beforeChecksum == null ? afterChecksum != null : !beforeChecksum.equals(afterChecksum);
            if (Boolean.TRUE.equals(mutation.getRequired()) && !changed) {
                throw new NodeFailureException(
                        "NODE_VALIDATION_FAILED",
                        "Required expected_mutation not detected: " + mutation.getPath(),
                        true
                );
            }
            if (changed && Files.exists(path)) {
                recordArtifactVersion(
                        run,
                        execution.getNodeId(),
                        artifactKeyForPath(mutation.getPath()),
                        path,
                        toArtifactScope(mutation.getScope()),
                        ArtifactKind.MUTATION,
                        null
                );
            }
        }
    }

    private Map<String, String> snapshotMutations(RunEntity run, List<PathRequirement> mutations) {
        Map<String, String> snapshot = new HashMap<>();
        if (mutations == null) {
            return snapshot;
        }
        for (PathRequirement mutation : mutations) {
            if (mutation == null || mutation.getPath() == null || mutation.getPath().isBlank()) {
                continue;
            }
            Path path = resolvePath(run, mutation.getScope(), mutation.getPath());
            snapshot.put(path.toString(), fileChecksumOrNull(path));
        }
        return snapshot;
    }

    private List<Map<String, Object>> resolveExecutionContext(RunEntity run, NodeModel node) {
        List<Map<String, Object>> resolved = new ArrayList<>();
        List<ExecutionContextEntry> entries = node.getExecutionContext() == null ? List.of() : node.getExecutionContext();
        for (ExecutionContextEntry entry : entries) {
            if (entry == null || entry.getType() == null || entry.getType().isBlank()) {
                continue;
            }
            String type = normalize(entry.getType());
            boolean required = Boolean.TRUE.equals(entry.getRequired());
            switch (type) {
                case "user_request" -> resolved.add(Map.of("type", "user_request", "value", run.getFeatureRequest()));
                case "file_ref" -> {
                    Path path = resolvePath(run, entry.getScope(), entry.getPath());
                    if (!Files.exists(path) || Files.isDirectory(path)) {
                        if (required) {
                            throw new NodeFailureException(
                                    "MISSING_EXECUTION_CONTEXT",
                                    "Missing required file_ref: " + entry.getPath(),
                                    false
                            );
                        }
                        continue;
                    }
                    resolved.add(Map.of("type", "file_ref", "path", path.toString()));
                }
                case "directory_ref" -> {
                    Path path = resolvePath(run, entry.getScope(), entry.getPath());
                    if (!Files.exists(path) || !Files.isDirectory(path)) {
                        if (required) {
                            throw new NodeFailureException(
                                    "MISSING_EXECUTION_CONTEXT",
                                    "Missing required directory_ref: " + entry.getPath(),
                                    false
                            );
                        }
                        continue;
                    }
                    resolved.add(Map.of("type", "directory_ref", "path", path.toString()));
                }
                case "artifact_ref" -> {
                    ArtifactVersionEntity artifact = artifactVersionRepository.findFirstByRunIdAndArtifactKeyOrderByCreatedAtDesc(
                                    run.getId(),
                                    entry.getPath()
                            )
                            .orElse(null);
                    if (artifact == null) {
                        if (required) {
                            throw new NodeFailureException(
                                    "MISSING_EXECUTION_CONTEXT",
                                    "Missing required artifact_ref: " + entry.getPath(),
                                    false
                            );
                        }
                        continue;
                    }
                    resolved.add(Map.of(
                            "type", "artifact_ref",
                            "artifact_key", artifact.getArtifactKey(),
                            "path", artifact.getPath(),
                            "artifact_version_id", artifact.getId()
                    ));
                }
                default -> {
                }
            }
        }
        return resolved;
    }

    private void applyTransition(
            RunEntity run,
            NodeExecutionEntity execution,
            GateInstanceEntity gate,
            String targetNodeId,
            String transitionLabel
    ) {
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new NodeFailureException(
                    "INVALID_TRANSITION",
                    "Transition target is missing for " + transitionLabel,
                    false
            );
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

    private void failRun(RunEntity run, String errorCode, String message) {
        runtimeStepTxService.failRun(run.getId(), errorCode, message);
    }

    private NodeExecutionEntity createNodeExecution(RunEntity run, NodeModel node, String nodeKind) {
        int attempt = nodeExecutionRepository.findFirstByRunIdAndNodeIdOrderByAttemptNoDesc(run.getId(), node.getId())
                .map(NodeExecutionEntity::getAttemptNo)
                .orElse(0) + 1;
        return runtimeStepTxService.createNodeExecution(run.getId(), node.getId(), nodeKind, attempt);
    }

    private void enforceGateRole(NodeModel node, User user) {
        String actorRole = user == null || user.getRole() == null ? null : user.getRole().name();
        List<String> allowedRoles = node.getAllowedRoles() == null ? List.of() : node.getAllowedRoles();
        if (allowedRoles.isEmpty()) {
            return;
        }
        if (actorRole == null || allowedRoles.stream().noneMatch((role) -> role.equals(actorRole))) {
            throw new ForbiddenException("Actor role is not allowed for this gate");
        }
    }

    private String resolveReworkTarget(NodeModel node, String mode) {
        if (node.getOnReworkRoutes() != null && !node.getOnReworkRoutes().isEmpty()) {
            if (mode == null || mode.isBlank()) {
                if (node.getOnReworkRoutes().size() == 1) {
                    return node.getOnReworkRoutes().values().iterator().next();
                }
                throw new ValidationException("mode is required for on_rework_routes");
            }
            String target = node.getOnReworkRoutes().get(mode);
            if (target == null || target.isBlank()) {
                throw new ValidationException("Unknown rework mode: " + mode);
            }
            return target;
        }
        if (node.getOnRework() != null && node.getOnRework().getNextNode() != null && !node.getOnRework().getNextNode().isBlank()) {
            return node.getOnRework().getNextNode();
        }
        throw new ValidationException("on_rework target is missing");
    }

    private void validateCreateRunCommand(CreateRunCommand command) {
        if (command == null) {
            throw new ValidationException("Request body is required");
        }
        if (command.projectId() == null) {
            throw new ValidationException("project_id is required");
        }
        if (command.flowCanonicalName() == null || command.flowCanonicalName().isBlank()) {
            throw new ValidationException("flow_canonical_name is required");
        }
        if (command.featureRequest() == null || command.featureRequest().isBlank()) {
            throw new ValidationException("feature_request is required");
        }
        if (command.targetBranch() == null || command.targetBranch().isBlank()) {
            throw new ValidationException("target_branch is required");
        }
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

    private void writeContextManifest(
            Path runScopeRoot,
            String contextRootDir,
            String featureRequest,
            List<String> contextFileManifest
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("context_root_dir", contextRootDir);
        manifest.put("context_file_manifest", contextFileManifest);
        manifest.put("feature_request", featureRequest);
        writeFile(runScopeRoot.resolve("context").resolve("context-manifest.json"), toJson(manifest).getBytes(StandardCharsets.UTF_8));
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
        throw new NodeFailureException("INVALID_TRANSITION", "Node not found in flow: " + nodeId, false);
    }

    private GateInstanceEntity getGateEntity(UUID gateId) {
        return gateInstanceRepository.findById(gateId)
                .orElseThrow(() -> new NotFoundException("Gate not found: " + gateId));
    }

    private RunEntity getRunEntity(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Run not found: " + runId));
    }

    private Project resolveProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
    }

    private CommandResult runGitCheckout(RunEntity run, Path projectRoot) {
        Project project = resolveProject(run.getProjectId());
        Path runWorkspaceRoot = resolveRunWorkspaceRoot(run);
        Path checkoutStdout = runWorkspaceRoot.resolve("checkout.stdout.log");
        Path checkoutStderr = runWorkspaceRoot.resolve("checkout.stderr.log");
        String repoUrl = trimToNull(project.getRepoUrl());
        if (repoUrl == null) {
            throw new NodeFailureException(
                    "CHECKOUT_FAILED",
                    "Project repo_url is empty",
                    false,
                    mapOf(
                            "target_branch", run.getTargetBranch(),
                            "project_root", projectRoot.toString()
                    )
            );
        }
        if (Files.exists(projectRoot)) {
            deleteDirectoryContents(projectRoot);
            try {
                Files.deleteIfExists(projectRoot);
            } catch (IOException ex) {
                throw new NodeFailureException("CHECKOUT_FAILED", "Failed to clean project root before checkout", false);
            }
        }
        try {
            CommandResult cloneResult = runProcess(
                    List.of(
                            "git",
                            "clone",
                            "--branch",
                            run.getTargetBranch(),
                            "--single-branch",
                            repoUrl,
                            projectRoot.toString()
                    ),
                    runWorkspaceRoot,
                    aiTimeoutSeconds,
                    checkoutStdout,
                    checkoutStderr
            );
            if (cloneResult.exitCode() != 0) {
                String stderr = trimToNull(cloneResult.stderr());
                String reason = stderr == null ? "unknown git error" : truncate(stderr, 2000);
                throw new NodeFailureException(
                        "CHECKOUT_FAILED",
                        "git clone failed with exit code " + cloneResult.exitCode()
                                + "; reason: " + reason
                                + "; stderr_log=" + cloneResult.stderrPath()
                                + "; stdout_log=" + cloneResult.stdoutPath(),
                        false,
                        mapOf(
                                "repo_url", redactRepoUrl(repoUrl),
                                "target_branch", run.getTargetBranch(),
                                "project_root", projectRoot.toString(),
                                "exit_code", cloneResult.exitCode(),
                                "stdout_path", cloneResult.stdoutPath(),
                                "stderr_path", cloneResult.stderrPath(),
                                "stdout", truncate(cloneResult.stdout(), 12000),
                                "stderr", truncate(cloneResult.stderr(), 12000)
                        )
                );
            }
            return cloneResult;
        } catch (IOException ex) {
            throw new NodeFailureException(
                    "CHECKOUT_FAILED",
                    "git clone I/O failure: " + ex.getMessage()
                            + "; stderr_log=" + checkoutStderr
                            + "; stdout_log=" + checkoutStdout,
                    false,
                    mapOf(
                            "repo_url", redactRepoUrl(repoUrl),
                            "target_branch", run.getTargetBranch(),
                            "project_root", projectRoot.toString(),
                            "stdout_path", checkoutStdout.toString(),
                            "stderr_path", checkoutStderr.toString()
                    )
            );
        }
    }

    private String readGitHead(RunEntity run, Path projectRoot) {
        Path runScopeRoot = resolveRunScopeRoot(resolveRunWorkspaceRoot(run));
        Path stdoutPath = runScopeRoot.resolve("logs").resolve("git-head.stdout.log");
        Path stderrPath = runScopeRoot.resolve("logs").resolve("git-head.stderr.log");
        try {
            CommandResult result = runProcess(
                    List.of("git", "-C", projectRoot.toString(), "rev-parse", "HEAD"),
                    projectRoot,
                    30,
                    stdoutPath,
                    stderrPath
            );
            if (result.exitCode() != 0) {
                return null;
            }
            return trimToNull(result.stdout());
        } catch (IOException ex) {
            return null;
        }
    }

    private Path resolveRunWorkspaceRoot(String workspaceRoot, UUID runId) {
        return Path.of(workspaceRoot).resolve(runId.toString()).toAbsolutePath().normalize();
    }

    private Path resolveProjectScopeRoot(Path runWorkspaceRoot) {
        return runWorkspaceRoot.resolve("repo").toAbsolutePath().normalize();
    }

    private Path resolveRunScopeRoot(Path runWorkspaceRoot) {
        return runWorkspaceRoot.resolve("runtime").toAbsolutePath().normalize();
    }

    private Path resolveRunWorkspaceRoot(RunEntity run) {
        String storedRoot = trimToNull(run.getWorkspaceRoot());
        if (storedRoot != null) {
            return Path.of(storedRoot).toAbsolutePath().normalize();
        }
        return resolveRunWorkspaceRoot(settingsService.getWorkspaceRoot(), run.getId());
    }

    private Path resolveProjectRoot(RunEntity run) {
        return resolveProjectScopeRoot(resolveRunWorkspaceRoot(run));
    }

    private Path resolveNodeExecutionRoot(RunEntity run, NodeExecutionEntity execution) {
        String dirName = execution.getNodeId() + "-attempt-" + execution.getAttemptNo();
        Path path = resolveRunScopeRoot(resolveRunWorkspaceRoot(run)).resolve("nodes").resolve(dirName);
        createDirectories(path);
        return path;
    }

    private Path resolvePath(RunEntity run, String scopeRaw, String value) {
        if (value == null || value.isBlank()) {
            throw new NodeFailureException("PATH_POLICY_VIOLATION", "Path is required", false);
        }
        if (Path.of(value).isAbsolute()) {
            throw new NodeFailureException("PATH_POLICY_VIOLATION", "Absolute path is forbidden: " + value, false);
        }
        Path runWorkspaceRoot = resolveRunWorkspaceRoot(run);
        Path root = "project".equals(defaultScope(scopeRaw))
                ? resolveProjectScopeRoot(runWorkspaceRoot)
                : resolveRunScopeRoot(runWorkspaceRoot);
        Path resolved = root.resolve(value).normalize();
        if (!resolved.startsWith(root)) {
            throw new NodeFailureException("PATH_POLICY_VIOLATION", "Path escapes root: " + value, false);
        }
        return resolved;
    }

    private byte[] decodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid content_base64");
        }
    }

    private String fileChecksumOrNull(Path path) {
        if (!Files.exists(path) || Files.isDirectory(path)) {
            return null;
        }
        try {
            return ChecksumUtil.sha256(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new ValidationException("Failed to read file checksum: " + path);
        }
    }

    private long fileSize(Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : 0L;
        } catch (IOException ex) {
            throw new ValidationException("Failed to read file size: " + path);
        }
    }

    private void writeFile(Path path, byte[] bytes) {
        createDirectories(path.getParent());
        try {
            Files.write(path, bytes);
        } catch (IOException ex) {
            throw new ValidationException("Failed to write file: " + path);
        }
    }

    private void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
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

    private String firstAllowedRole(List<String> allowedRoles) {
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return null;
        }
        for (String role : allowedRoles) {
            if (role != null && !role.isBlank()) {
                return role;
            }
        }
        return null;
    }

    private boolean isRunTerminal(RunStatus status) {
        return status == RunStatus.COMPLETED || status == RunStatus.FAILED || status == RunStatus.CANCELLED;
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

    private String resolveActorId(User user) {
        return user == null ? "system" : user.getUsername();
    }

    private String normalizeBranch(String branch) {
        String normalized = trimToNull(branch);
        if (normalized == null) {
            return "main";
        }
        return normalized;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ValidationException("Failed to serialize JSON payload");
        }
    }

    private List<String> parseContextManifestEntries(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        try {
            List<?> values = objectMapper.readValue(rawJson, List.class);
            List<String> result = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof String stringValue && !stringValue.isBlank()) {
                    result.add(stringValue);
                }
            }
            return result;
        } catch (Exception ex) {
            return List.of();
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

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String redactRepoUrl(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return repoUrl;
        }
        return repoUrl.replaceFirst("://([^/@]+)@", "://***@");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record CreateRunCommand(
            UUID projectId,
            String targetBranch,
            String flowCanonicalName,
            String contextRootDir,
            String featureRequest
    ) {}

    public record SubmittedArtifact(
            String artifactKey,
            String path,
            String scope,
            String contentBase64
    ) {}

    public record SubmitInputCommand(
            Long expectedGateVersion,
            List<SubmittedArtifact> artifacts,
            String comment
    ) {}

    public record ApproveGateCommand(
            Long expectedGateVersion,
            String comment,
            List<UUID> reviewedArtifactVersionIds
    ) {}

    public record ReworkGateCommand(
            Long expectedGateVersion,
            String mode,
            String comment,
            List<UUID> reviewedArtifactVersionIds
    ) {}

    public record GateActionResult(
            GateInstanceEntity gate,
            RunEntity run
    ) {}

    static record CommandResult(
            int exitCode,
            String stdout,
            String stderr,
            String stdoutPath,
            String stderrPath
    ) {}

    private record AgentInvocationContext(
            Path workingDirectory,
            List<String> command,
            Path promptPath,
            Path rulePath,
            Path skillsRoot,
            Path stdoutPath,
            Path stderrPath,
            AgentPromptBuilder.AgentPromptPackage promptPackage
    ) {}

    private static class NodeFailureException extends RuntimeException {
        private final String errorCode;
        private final String auditEventType;
        private final Map<String, Object> details;

        private NodeFailureException(String errorCode, String message, boolean validationFailure) {
            this(errorCode, message, validationFailure, Map.of());
        }

        private NodeFailureException(String errorCode, String message, boolean validationFailure, Map<String, Object> details) {
            super(message);
            this.errorCode = errorCode;
            this.auditEventType = validationFailure ? "node_validation_failed" : "node_execution_failed";
            this.details = details == null ? Map.of() : details;
        }
    }
}

package ru.hgd.sdlc.runtime.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.flow.domain.ExecutionContextEntry;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.flow.domain.PathRequirement;
import ru.hgd.sdlc.runtime.application.AgentInvocationContext;
import ru.hgd.sdlc.runtime.application.AgentSessionCommandMode;
import ru.hgd.sdlc.runtime.application.AgentPromptBuilder;
import ru.hgd.sdlc.runtime.application.CodingAgentException;
import ru.hgd.sdlc.runtime.application.CodingAgentStrategy;
import ru.hgd.sdlc.runtime.application.ExecutionTraceBuilder;
import ru.hgd.sdlc.runtime.application.dto.GitChangeEntry;
import ru.hgd.sdlc.runtime.application.port.ClockPort;
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
import ru.hgd.sdlc.runtime.domain.RunPublishMode;
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.infrastructure.ArtifactVersionRepository;
import ru.hgd.sdlc.runtime.infrastructure.AuditEventRepository;
import ru.hgd.sdlc.runtime.infrastructure.NodeExecutionRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;
import ru.hgd.sdlc.settings.application.SettingsService;

@Service
public class RunStepService {
    private static final Logger log = LoggerFactory.getLogger(RunStepService.class);
    private static final int DEFAULT_MAX_TICK_ITERATIONS = 128;
    private static final int DEFAULT_MAX_INLINE_ARTIFACT_TOKENS = 16_384;
    private static final int APPROX_BYTES_PER_TOKEN = 4;
    private static final Set<String> FAILURE_TRANSITIONS = Set.of("on_failure");
    private static final String REWORK_REASON_TARGET_MISSING = "rework_target_missing";
    private static final String REWORK_REASON_TARGET_KIND_UNSUPPORTED = "rework_target_kind_unsupported";
    private static final String REWORK_REASON_TARGET_CHECKPOINT_DISABLED = "rework_target_checkpoint_disabled";
    private static final String REWORK_REASON_TARGET_CHECKPOINT_NOT_FOUND = "target_checkpoint_not_found";
    private static final String STEP_SUMMARY_FILE_NAME = "step-summary.json";
    private static final String DEFAULT_GATE_ASSIGNEE_ROLE = Role.TECH_APPROVER.name();

    private final RunRepository runRepository;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final AuditEventRepository auditEventRepository;
    private final RuntimeStepTxService runtimeStepTxService;
    private final ExecutionTraceBuilder executionTraceBuilder;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final NodeExecutionRouter nodeExecutionRouter;
    private final ProcessExecutionPort processExecutionPort;
    private final WorkspacePort workspacePort;
    private final ClockPort clockPort;
    private final RunPublishService runPublishService;
    private final Map<String, CodingAgentStrategy> codingAgentStrategiesByAgentId;
    private final int maxTickIterations;
    private final int maxInlineArtifactTokens;
    private final long maxInlineArtifactBytes;

    public RunStepService(
            RunRepository runRepository,
            NodeExecutionRepository nodeExecutionRepository,
            ArtifactVersionRepository artifactVersionRepository,
            AuditEventRepository auditEventRepository,
            RuntimeStepTxService runtimeStepTxService,
            ExecutionTraceBuilder executionTraceBuilder,
            ObjectMapper objectMapper,
            SettingsService settingsService,
            NodeExecutionRouter nodeExecutionRouter,
            ProcessExecutionPort processExecutionPort,
            WorkspacePort workspacePort,
            ClockPort clockPort,
            RunPublishService runPublishService,
            List<CodingAgentStrategy> codingAgentStrategies,
            @Value("${runtime.max-tick-iterations:128}") Integer maxTickIterations,
            @Value("${runtime.max-inline-artifact-tokens:16384}") Integer maxInlineArtifactTokens) {
        this.runRepository = runRepository;
        this.nodeExecutionRepository = nodeExecutionRepository;
        this.artifactVersionRepository = artifactVersionRepository;
        this.auditEventRepository = auditEventRepository;
        this.runtimeStepTxService = runtimeStepTxService;
        this.executionTraceBuilder = executionTraceBuilder;
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.nodeExecutionRouter = nodeExecutionRouter;
        this.processExecutionPort = processExecutionPort;
        this.workspacePort = workspacePort;
        this.clockPort = clockPort;
        this.runPublishService = runPublishService;
        this.codingAgentStrategiesByAgentId = (codingAgentStrategies == null ? List.<CodingAgentStrategy>of()
                : codingAgentStrategies)
                .stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        (strategy) -> normalize(strategy.codingAgent()),
                        Function.identity(),
                        (left, right) -> left));
        this.maxTickIterations = maxTickIterations == null ? DEFAULT_MAX_TICK_ITERATIONS : maxTickIterations;
        this.maxInlineArtifactTokens = normalizeMaxInlineArtifactTokens(maxInlineArtifactTokens);
        this.maxInlineArtifactBytes = toApproxBytes(this.maxInlineArtifactTokens);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processRunStep(UUID runId) {
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

    private boolean executeCurrentNode(RunEntity run) {
        if (isRunCancelled(run.getId())) {
            return false;
        }
        FlowModel flowModel = parseFlowSnapshot(run);
        NodeModel node;
        try {
            node = requireNode(flowModel, run.getCurrentNodeId());
        } catch (ValidationException | NodeFailureException ex) {
            failRun(run, "INVALID_FLOW_SNAPSHOT", ex.getMessage());
            return false;
        }
        String nodeKind = normalizeNodeKind(node);

        NodeExecutionEntity execution = createNodeExecution(run, node, nodeKind);

        try {
            createCheckpointBeforeExecution(run, node, execution, nodeKind);
            return nodeExecutionRouter.execute(this, run, node, execution, nodeKind);
        } catch (NodeFailureException ex) {
            runtimeStepTxService.markNodeExecutionFailed(run.getId(), execution.getId(), ex.errorCode, ex.getMessage(),
                    ex.auditEventType, ex.getDetails());
            if (isRunCancelled(run.getId())) {
                return false;
            }
            if (("ai".equals(nodeKind) || "command".equals(nodeKind))
                    && node.getOnFailure() != null && !node.getOnFailure().isBlank()) {
                if ("ai".equals(nodeKind) && Boolean.TRUE.equals(node.getAllowRetry()) && "NODE_VALIDATION_FAILED".equals(ex.errorCode)) {
                    failRun(run, ex.errorCode, ex.getMessage());
                    return false;
                }
                Integer maxTransitions = node.getMaxFailureTransitions();
                if (maxTransitions != null) {
                    int failedCount = nodeExecutionRepository
                            .countByRunIdAndNodeIdAndStatus(run.getId(), node.getId(), NodeExecutionStatus.FAILED);
                    if (failedCount >= maxTransitions) {
                        failRun(run, ex.errorCode,
                                "Max failure transitions (" + maxTransitions + ") exceeded for node " + node.getId()
                                        + ": " + ex.getMessage());
                        return false;
                    }
                }
                applyTransition(run, execution, null, node.getOnFailure(), "on_failure");
                return true;
            }
            failRun(run, ex.errorCode, ex.getMessage());
            return false;
        }
    }

    public boolean processCurrentNode(RunEntity run) {
        return executeCurrentNode(run);
    }

    boolean executeAiNode(RunEntity run, NodeModel node, NodeExecutionEntity execution) {
        FlowModel flowModel = parseFlowSnapshot(run);
        String pendingReworkInstruction = trimToNull(run.getPendingReworkInstruction());
        List<Map<String, Object>> resolvedContext = resolveExecutionContext(run, node);
        List<AgentPromptBuilder.WorkflowProgressEntry> workflowProgress = loadWorkflowProgress(run.getId());
        CodingAgentStrategy strategy = resolveCodingAgentStrategy(flowModel);
        AgentSessionSelection agentSessionSelection = selectAgentSession(run, node);
        runtimeStepTxService.assignAgentSession(
                run.getId(),
                execution.getId(),
                agentSessionSelection.sessionMode(),
                agentSessionSelection.sessionId()
        );
        AgentInvocationContext agentInvocationContext;
        try {
            agentInvocationContext = strategy.materializeWorkspace(new CodingAgentStrategy.MaterializationRequest(
                    run,
                    flowModel,
                    node,
                    execution,
                    resolvedContext,
                    resolveProjectRoot(run),
                    resolveNodeExecutionRoot(run, execution),
                    workflowProgress,
                    agentSessionSelection.commandMode(),
                    agentSessionSelection.sessionId()));
        } catch (CodingAgentException ex) {
            throw new NodeFailureException(ex.getErrorCode(), ex.getMessage(), false, ex.getDetails());
        }
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
                        agentInvocationContext.skillsRoot().toString()));
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
                        agentInvocationContext.command(),
                        agentSessionSelection.sessionMode().apiValue(),
                        agentSessionSelection.sessionId()));

        Map<String, String> beforeMutations = snapshotMutations(run, node.getExpectedMutations());
        CommandResult agentResult;
        try {
            agentResult = runProcess(
                    run.getId(),
                    agentInvocationContext.command(),
                    agentInvocationContext.workingDirectory(),
                    settingsService.getAiTimeoutSeconds(),
                    agentInvocationContext.stdoutPath(),
                    agentInvocationContext.stderrPath());
        } catch (ProcessExecutionPort.ProcessCancelledException ex) {
            runtimeStepTxService.markNodeExecutionCancelled(run.getId(), execution.getId(), ex.getMessage());
            return false;
        } catch (IOException ex) {
            if (agentSessionSelection.commandMode() == AgentSessionCommandMode.RESUME_PREVIOUS_SESSION) {
                runtimeStepTxService.appendAudit(
                        run.getId(),
                        execution.getId(),
                        null,
                        "agent_session_resume_failed",
                        ActorType.SYSTEM,
                        "runtime",
                        mapOf(
                                "session_id", agentSessionSelection.sessionId(),
                                "node_execution_id", execution.getId(),
                                "reason", ex.getMessage()
                        )
                );
                throw new NodeFailureException("AGENT_SESSION_RESUME_FAILED", ex.getMessage(), false);
            }
            throw new NodeFailureException("AGENT_EXECUTION_FAILED", ex.getMessage(), false);
        }
        if (isRunCancelled(run.getId())) {
            runtimeStepTxService.markNodeExecutionCancelled(run.getId(), execution.getId(), "Run cancelled by user");
            return false;
        }
        if (agentResult.exitCode() != 0) {
            if (agentSessionSelection.commandMode() == AgentSessionCommandMode.RESUME_PREVIOUS_SESSION) {
                runtimeStepTxService.appendAudit(
                        run.getId(),
                        execution.getId(),
                        null,
                        "agent_session_resume_failed",
                        ActorType.SYSTEM,
                        "runtime",
                        mapOf(
                                "session_id", agentSessionSelection.sessionId(),
                                "node_execution_id", execution.getId(),
                                "reason", "exit_code=" + agentResult.exitCode(),
                                "stdout", truncate(agentResult.stdout(), 4000),
                                "stderr", truncate(agentResult.stderr(), 4000)
                        )
                );
                throw new NodeFailureException(
                        "AGENT_SESSION_RESUME_FAILED",
                        capitalize(strategy.codingAgent()) + " session resume failed with exit code " + agentResult.exitCode(),
                        false,
                        mapOf(
                                "exit_code", agentResult.exitCode(),
                                "session_id", agentSessionSelection.sessionId(),
                                "stdout", truncate(agentResult.stdout(), 4000),
                                "stderr", truncate(agentResult.stderr(), 4000))
                );
            }
            throw new NodeFailureException(
                    "AGENT_EXECUTION_FAILED",
                    capitalize(strategy.codingAgent()) + " execution failed with exit code " + agentResult.exitCode(),
                    false,
                    mapOf(
                            "exit_code", agentResult.exitCode(),
                            "stdout", truncate(agentResult.stdout(), 4000),
                            "stderr", truncate(agentResult.stderr(), 4000)));
        }
        validateNodeOutputs(run, node, execution, beforeMutations);

        runtimeStepTxService.appendAudit(
                run.getId(),
                execution.getId(),
                null,
                "agent_invocation_finished",
                ActorType.AGENT,
                strategy.codingAgent(),
                mapOf(
                        "node_id", node.getId(),
                        "prompt_checksum", agentInvocationContext.promptPackage().promptChecksum(),
                        "status", "ok",
                        "exit_code", agentResult.exitCode(),
                        "stdout_path", agentResult.stdoutPath(),
                        "stderr_path", agentResult.stderrPath(),
                        "stdout", truncate(agentResult.stdout(), 12000),
                        "stderr", truncate(agentResult.stderr(), 12000)));

        String stepSummaryJson = extractStepSummary(run, execution, agentResult.stdout());
        if (stepSummaryJson == null) {
            runtimeStepTxService.appendAudit(
                    run.getId(), execution.getId(), null,
                    "step_summary_missing", ActorType.SYSTEM, "runtime",
                    Map.of(
                            "node_id", node.getId(),
                            "step_summary_path", resolveStepSummaryPath(run, execution).toString()));
        }
        runtimeStepTxService.markNodeExecutionSucceeded(run.getId(), execution.getId(), node.getId(), stepSummaryJson);
        if (pendingReworkInstruction != null) {
            runtimeStepTxService.consumePendingReworkInstruction(run.getId(), execution.getId(), node.getId());
        }
        applyTransition(run, execution, null, node.getOnSuccess(), "on_success");
        return true;
    }

    boolean executeCommandNode(RunEntity run, NodeModel node, NodeExecutionEntity execution) {
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
                mapOf("node_id", node.getId(), "execution_context", resolvedContext));

        Map<String, String> beforeMutations = snapshotMutations(run, node.getExpectedMutations());
        CommandResult commandResult;
        try {
            commandResult = executeCommand(run, node, stdoutPath, stderrPath);
        } catch (RunCancelledException ex) {
            runtimeStepTxService.markNodeExecutionCancelled(run.getId(), execution.getId(), ex.getMessage());
            return false;
        }
        if (isRunCancelled(run.getId())) {
            runtimeStepTxService.markNodeExecutionCancelled(run.getId(), execution.getId(), "Run cancelled by user");
            return false;
        }
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
                        "stderr", truncate(commandResult.stderr(), 12000)));

        runtimeStepTxService.markNodeExecutionSucceeded(run.getId(), execution.getId(), node.getId(), null);
        applyTransition(run, execution, null, node.getOnSuccess(), "on_success");
        return true;
    }

    boolean openGate(
            RunEntity run,
            NodeModel node,
            NodeExecutionEntity execution,
            GateKind gateKind,
            GateStatus initialStatus) {
        if (gateKind == GateKind.HUMAN_INPUT) {
            createHumanInputOutputFiles(run, node, execution);
        }
        String payloadJson = buildGatePayload(run, node, execution);
        runtimeStepTxService.openGate(
                run.getId(),
                execution.getId(),
                node.getId(),
                gateKind,
                initialStatus,
                firstAllowedRole(node.getAllowedRoles()),
                payloadJson);
        return false;
    }

    boolean skipOrOpenGate(
            RunEntity run,
            NodeModel node,
            NodeExecutionEntity execution,
            GateKind gateKind,
            GateStatus initialStatus) {
        if (shouldSkipGate(run, node)) {
            runtimeStepTxService.appendAudit(
                    run.getId(),
                    execution.getId(),
                    null,
                    "gate_auto_skipped",
                    ActorType.SYSTEM,
                    "runtime",
                    mapOf(
                            "node_id", node.getId(),
                            "gate_kind", gateKind.name().toLowerCase(Locale.ROOT),
                            "allowed_roles", node.getAllowedRoles() == null ? List.of() : node.getAllowedRoles()
                    ));
            if (gateKind == GateKind.HUMAN_INPUT) {
                createHumanInputOutputFiles(run, node, execution);
                runtimeStepTxService.markNodeExecutionSucceeded(run.getId(), execution.getId(), node.getId(), null);
                applyTransition(run, execution, null, node.getOnSubmit(), "auto_on_submit");
            } else {
                runtimeStepTxService.markNodeExecutionSucceeded(run.getId(), execution.getId(), node.getId(), null);
                applyTransition(run, execution, null, node.getOnApprove(), "auto_on_approve");
            }
            return true;
        }
        return openGate(run, node, execution, gateKind, initialStatus);
    }

    private boolean shouldSkipGate(RunEntity run, NodeModel node) {
        if (!run.isSkipGates()) {
            return false;
        }
        List<String> allowedRoles = node.getAllowedRoles();
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return true;
        }
        return allowedRoles.stream()
                .filter(r -> r != null && !r.isBlank())
                .noneMatch(r -> "PRODUCT_OWNER".equalsIgnoreCase(r.trim()));
    }

    boolean completeTerminalNode(RunEntity run, NodeModel node, NodeExecutionEntity execution) {
        RunStatus terminalStatus = resolveTerminalStatus(run.getId());
        if (terminalStatus == RunStatus.FAILED) {
            runtimeStepTxService.completeRun(run.getId(), execution.getId(), node.getId(), RunStatus.FAILED);
            return false;
        }
        runtimeStepTxService.markRunWaitingPublish(run.getId(), execution.getId(), node.getId());
        runPublishService.dispatchPublish(run.getId());
        return false;
    }

    private CodingAgentStrategy resolveCodingAgentStrategy(FlowModel flowModel) {
        String flowCodingAgent = normalize(trimToNull(flowModel.getCodingAgent()));
        String runtimeCodingAgent = normalize(trimToNull(settingsService.getRuntimeCodingAgent()));
        if (!runtimeCodingAgent.equals(flowCodingAgent)) {
            throw new NodeFailureException(
                    "CODING_AGENT_MISMATCH",
                    "Flow coding_agent does not match runtime settings: flow=" + flowCodingAgent + ", runtime="
                            + runtimeCodingAgent,
                    false);
        }
        CodingAgentStrategy strategy = codingAgentStrategiesByAgentId.get(runtimeCodingAgent);
        if (strategy == null) {
            throw new NodeFailureException(
                    "UNSUPPORTED_CODING_AGENT",
                    "Runtime coding_agent is not implemented: " + runtimeCodingAgent,
                    false);
        }
        return strategy;
    }

    private String buildGatePayload(RunEntity run, NodeModel node, NodeExecutionEntity execution) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String nodeKind = normalizeNodeKind(node);
        if ("human_approval".equals(nodeKind)) {
            ReworkDiscardAvailability reworkAvailability = resolveReworkDiscardAvailability(run, node);
            payload.put("rework_mode", reworkAvailability.mode());
            payload.put("rework_keep_changes", reworkAvailability.keepChanges());
            payload.put("rework_keep_changes_selectable", reworkAvailability.keepChangesSelectable());
            payload.put("rework_discard_available", reworkAvailability.discardAvailable());
            if (reworkAvailability.unavailableReason() != null) {
                payload.put("rework_discard_unavailable_reason", reworkAvailability.unavailableReason());
            }
        }
        appendGitSummaryPayload(run, payload, nodeKind);
        if ("human_input".equals(nodeKind)) {
            List<Map<String, Object>> humanInputArtifacts = resolveHumanInputOutputArtifacts(run, node, execution);
            if (!humanInputArtifacts.isEmpty()) {
                payload.put("human_input_artifacts", humanInputArtifacts);
            }
        } else {
            List<Map<String, Object>> contextArtifacts = resolveGateContextArtifacts(run, node);
            if (!contextArtifacts.isEmpty()) {
                payload.put("execution_context_artifacts", contextArtifacts);
            }
        }
        String inputArtifactKey = trimToNull(node.getInputArtifact());
        if (inputArtifactKey != null) {
            ArtifactVersionEntity inputArtifact = artifactVersionRepository
                    .findFirstByRunIdAndArtifactKeyOrderByCreatedAtDesc(run.getId(), inputArtifactKey)
                    .orElse(null);
            if (inputArtifact != null) {
                String content = readFileContent(Path.of(inputArtifact.getPath()));
                payload.put("input_artifact_key", inputArtifactKey);
                payload.put("input_artifact_version_id", inputArtifact.getId().toString());
                payload.put("input_artifact_path", inputArtifact.getPath());
                if (content != null) {
                    payload.put("input_artifact_content", content);
                }
            }
        }
        String outputArtifactKey = trimToNull(node.getOutputArtifact());
        if (outputArtifactKey != null) {
            payload.put("output_artifact_key", outputArtifactKey);
        }
        String userInstructions = trimToNull(node.getInstruction());
        if (userInstructions != null) {
            payload.put("user_instructions", userInstructions);
        }
        if (payload.isEmpty()) {
            return null;
        }
        return toJson(payload);
    }

    private void appendGitSummaryPayload(RunEntity run, Map<String, Object> payload, String nodeKind) {
        List<GitChangeEntry> changes = listGitChanges(run);
        int addedLines = changes.stream().mapToInt(GitChangeEntry::added).sum();
        int removedLines = changes.stream().mapToInt(GitChangeEntry::removed).sum();
        payload.put("git_changes", changes.stream().map((entry) -> mapOf(
                "path", entry.path(),
                "status", entry.status(),
                "added", entry.added(),
                "removed", entry.removed(),
                "is_binary", entry.binary())).toList());
        payload.put("git_summary", mapOf(
                "files_changed", changes.size(),
                "added_lines", addedLines,
                "removed_lines", removedLines,
                "status_label", "human_input".equals(nodeKind) ? "Awaiting input" : "Ready for review"));
    }

    private ReworkDiscardAvailability resolveReworkDiscardAvailability(RunEntity run, NodeModel approvalNode) {
        if (approvalNode == null || approvalNode.getOnRework() == null) {
            return new ReworkDiscardAvailability("discard", false, false, false, REWORK_REASON_TARGET_MISSING);
        }
        String targetNodeId = trimToNull(approvalNode.getOnRework().getNextNode());
        if (targetNodeId == null) {
            return new ReworkDiscardAvailability("discard", false, false, false, REWORK_REASON_TARGET_MISSING);
        }
        FlowModel flowModel = parseFlowSnapshot(run);
        NodeModel targetNode = flowModel.getNodes().stream()
                .filter((candidate) -> targetNodeId.equals(candidate.getId()))
                .findFirst()
                .orElse(null);
        if (targetNode == null) {
            return new ReworkDiscardAvailability("discard", false, false, false, REWORK_REASON_TARGET_MISSING);
        }
        String targetKind = normalizeNodeKind(targetNode);
        if (!"ai".equals(targetKind) && !"command".equals(targetKind)) {
            return new ReworkDiscardAvailability("discard", false, false, false, REWORK_REASON_TARGET_KIND_UNSUPPORTED);
        }
        if (!Boolean.TRUE.equals(targetNode.getCheckpointBeforeRun())) {
            return new ReworkDiscardAvailability("discard", false, false, false,
                    REWORK_REASON_TARGET_CHECKPOINT_DISABLED);
        }
        NodeExecutionEntity targetExecution = nodeExecutionRepository
                .findFirstByRunIdAndNodeIdOrderByAttemptNoDesc(run.getId(), targetNodeId)
                .orElse(null);
        String checkpointCommitSha = targetExecution == null ? null
                : trimToNull(targetExecution.getCheckpointCommitSha());
        if (targetExecution == null || !targetExecution.isCheckpointEnabled() || checkpointCommitSha == null) {
            return new ReworkDiscardAvailability("keep", true, true, false, REWORK_REASON_TARGET_CHECKPOINT_NOT_FOUND);
        }
        return new ReworkDiscardAvailability("keep", true, true, true, null);
    }

    private List<Map<String, Object>> resolveGateContextArtifacts(RunEntity run, NodeModel node) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<ExecutionContextEntry> entries = node.getExecutionContext() == null ? List.of()
                : node.getExecutionContext();
        for (ExecutionContextEntry entry : entries) {
            if (entry == null || entry.getType() == null) {
                continue;
            }
            String type = normalize(entry.getType());
            if (!"artifact_ref".equals(type)) {
                continue;
            }
            String fileName = trimToNull(entry.getPath());
            if (fileName == null) {
                continue;
            }
            Path path = resolveArtifactRefPath(run, entry.getNodeId(), entry.getScope(), fileName);
            if (path == null || !workspacePort.exists(path)) {
                continue;
            }
            String content = readFileContent(path);
            Map<String, Object> artifactInfo = new LinkedHashMap<>();
            artifactInfo.put("artifact_key", artifactKeyForPath(fileName));
            artifactInfo.put("source_node_id", entry.getNodeId() == null ? "" : entry.getNodeId());
            artifactInfo.put("path", path.toString());
            if (content != null) {
                artifactInfo.put("content", content);
            }
            result.add(artifactInfo);
        }
        return result;
    }

    private List<Map<String, Object>> resolveHumanInputOutputArtifacts(
            RunEntity run,
            NodeModel node,
            NodeExecutionEntity execution) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (HumanInputEditableArtifact artifact : resolveHumanInputEditableArtifacts(run, node, execution)) {
            Path outputPath = resolveProducedArtifactPath(run, execution, "run", artifact.path());
            String content = readFileContent(outputPath);
            result.add(mapOf(
                    "artifact_key", artifact.artifactKey(),
                    "path", artifact.path(),
                    "workspace_path", relativizePath(resolveProjectRoot(run), outputPath),
                    "scope", artifact.scope(),
                    "required", artifact.required(),
                    "source_node_id", artifact.sourceNodeId(),
                    "source_artifact_version_id", artifact.sourceArtifactVersionId(),
                    "source_path", artifact.sourcePath(),
                    "content", content));
        }
        return result;
    }

    private void createHumanInputOutputFiles(RunEntity run, NodeModel node, NodeExecutionEntity execution) {
        List<HumanInputEditableArtifact> editableArtifacts = resolveHumanInputEditableArtifacts(run, node, execution);
        for (HumanInputEditableArtifact artifact : editableArtifacts) {
            Path sourcePath = Path.of(artifact.sourcePath());
            Path outputPath = resolveProducedArtifactPath(run, execution, "run", artifact.path());
            createDirectories(outputPath.getParent());
            if (workspacePort.exists(sourcePath) && !workspacePort.isDirectory(sourcePath)) {
                try {
                    workspacePort.copy(sourcePath, outputPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    throw new ValidationException("Failed to copy human_input artifact: " + artifact.path());
                }
            } else if (!workspacePort.exists(outputPath)) {
                writeFile(outputPath, new byte[0]);
            }
            recordArtifactVersion(
                    run,
                    execution.getNodeId(),
                    artifact.artifactKey(),
                    outputPath,
                    ArtifactScope.RUN,
                    ArtifactKind.HUMAN_INPUT,
                    null);
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
            return workspacePort.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Failed to read artifact content: path={}", path, ex);
            return null;
        }
    }

    private String readInlineArtifactContent(Path path) {
        if (path == null || !workspacePort.exists(path) || workspacePort.isDirectory(path)) {
            return "";
        }
        try {
            String content = workspacePort.readString(path, StandardCharsets.UTF_8);
            return content == null ? "" : content;
        } catch (IOException ex) {
            log.warn("Failed to read inline artifact content: path={}", path, ex);
            return "";
        }
    }

    private Map<String, Object> byRefArtifactContext(Path path, String artifactKey, String sourceNodeId) {
        return Map.of(
                "type", "artifact_ref",
                "artifact_key", artifactKey,
                "path", path.toString(),
                "source_node_id", sourceNodeId,
                "transfer_mode", "by_ref");
    }

    private RunStatus resolveTerminalStatus(UUID runId) {
        if (auditEventRepository.existsByRunIdAndEventType(runId, "run_give_up_requested")) {
            return RunStatus.FAILED;
        }
        return auditEventRepository.findFirstByRunIdAndEventTypeOrderBySequenceNoDesc(runId, "transition_applied")
                .map(event -> {
                    try {
                        Map<String, Object> payload = objectMapper.readValue(event.getPayloadJson(),
                                new TypeReference<>() {
                                });
                        String transition = String.valueOf(payload.getOrDefault("transition", ""));
                        return FAILURE_TRANSITIONS.contains(transition) ? RunStatus.FAILED : RunStatus.COMPLETED;
                    } catch (Exception ex) {
                        log.warn("Failed to parse transition_applied payload for run_id={}", runId, ex);
                        return RunStatus.COMPLETED;
                    }
                })
                .orElse(RunStatus.COMPLETED);
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
                    run.getId(),
                    List.of("bash", "-lc", instruction),
                    workingDirectory,
                    settingsService.getAiTimeoutSeconds(),
                    stdoutPath,
                    stderrPath);
            int exitCode = commandResult.exitCode();
            Set<Integer> successExitCodes = (node.getSuccessExitCodes() == null || node.getSuccessExitCodes().isEmpty())
                    ? Set.of(0)
                    : Set.copyOf(node.getSuccessExitCodes());
            if (!successExitCodes.contains(exitCode)) {
                throw new NodeFailureException(
                        "COMMAND_EXECUTION_FAILED",
                        "Command node failed with exit code " + exitCode,
                        false,
                        mapOf(
                                "exit_code", exitCode,
                                "stdout", truncate(commandResult.stdout(), 4000),
                                "stderr", truncate(commandResult.stderr(), 4000),
                                "instruction", truncate(instruction, 1000)));
            }
            return commandResult;
        } catch (ProcessExecutionPort.ProcessCancelledException ex) {
            throw new RunCancelledException(ex.getMessage());
        } catch (IOException ex) {
            throw new NodeFailureException("COMMAND_EXECUTION_FAILED", ex.getMessage(), false);
        }
    }

    private CommandResult runProcess(
            UUID runId,
            List<String> command,
            Path workingDirectory,
            int timeoutSeconds,
            Path stdoutPath,
            Path stderrPath) throws IOException {
        ProcessExecutionPort.ProcessExecutionResult result = processExecutionPort.execute(
                new ProcessExecutionPort.ProcessExecutionRequest(
                        runId,
                        command,
                        workingDirectory,
                        timeoutSeconds,
                        stdoutPath,
                        stderrPath,
                        true));
        return new CommandResult(
                result.exitCode(),
                result.stdout(),
                result.stderr(),
                result.stdoutPath(),
                result.stderrPath());
    }

    private String readFile(Path path) throws IOException {
        if (!workspacePort.exists(path)) {
            return "";
        }
        return workspacePort.readString(path, StandardCharsets.UTF_8);
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

    private void materializeDeclaredArtifacts(
            RunEntity run,
            NodeModel node,
            NodeExecutionEntity execution,
            String nodeKind,
            CommandResult commandResult) {
        List<PathRequirement> declared = node.getProducedArtifacts() == null ? List.of() : node.getProducedArtifacts();
        for (PathRequirement requirement : declared) {
            if (requirement == null || requirement.getPath() == null || requirement.getPath().isBlank()) {
                continue;
            }
            Path path = resolveProducedArtifactPath(run, execution, requirement.getScope(), requirement.getPath());
            String content = renderArtifactContent(run, node, nodeKind, commandResult, requirement.getPath());
            writeFile(path, content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String renderArtifactContent(
            RunEntity run,
            NodeModel node,
            String nodeKind,
            CommandResult commandResult,
            String path) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(node.getTitle() == null ? node.getId() : node.getTitle()).append("\n\n");
        sb.append("- run_id: ").append(run.getId()).append("\n");
        sb.append("- flow: ").append(run.getFlowCanonicalName()).append("\n");
        sb.append("- node_id: ").append(node.getId()).append("\n");
        sb.append("- node_kind: ").append(nodeKind).append("\n");
        sb.append("- artifact_path: ").append(path).append("\n");
        sb.append("- generated_at: ").append(clockPort.now()).append("\n\n");
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

    private void validateNodeOutputs(
            RunEntity run,
            NodeModel node,
            NodeExecutionEntity execution,
            Map<String, String> beforeMutations) {
        List<PathRequirement> produced = node.getProducedArtifacts() == null ? List.of() : node.getProducedArtifacts();
        for (PathRequirement requirement : produced) {
            if (requirement == null || requirement.getPath() == null || requirement.getPath().isBlank()) {
                continue;
            }
            Path path = resolveProducedArtifactPath(run, execution, requirement.getScope(), requirement.getPath());
            boolean exists = workspacePort.exists(path);
            if (Boolean.TRUE.equals(requirement.getRequired()) && !exists) {
                throw new NodeFailureException(
                        "NODE_VALIDATION_FAILED",
                        "Required produced artifact missing: " + requirement.getPath(),
                        true);
            }
            if (exists) {
                recordArtifactVersion(
                        run,
                        execution.getNodeId(),
                        artifactKeyForPath(requirement.getPath()),
                        path,
                        toArtifactScope(requirement.getScope()),
                        ArtifactKind.PRODUCED,
                        null);
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
                        true);
            }
            if (changed && workspacePort.exists(path)) {
                recordArtifactVersion(
                        run,
                        execution.getNodeId(),
                        artifactKeyForPath(mutation.getPath()),
                        path,
                        toArtifactScope(mutation.getScope()),
                        ArtifactKind.MUTATION,
                        null);
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
        List<InlineArtifactContext> inlineCandidates = new ArrayList<>();
        List<ExecutionContextEntry> entries = node.getExecutionContext() == null ? List.of()
                : node.getExecutionContext();
        for (ExecutionContextEntry entry : entries) {
            if (entry == null || entry.getType() == null || entry.getType().isBlank()) {
                continue;
            }
            String type = normalize(entry.getType());
            boolean required = Boolean.TRUE.equals(entry.getRequired());
            switch (type) {
                case "user_request" -> resolved.add(Map.of("type", "user_request", "value", run.getFeatureRequest()));
                case "artifact_ref" -> {
                    Path path = resolveArtifactRefPath(run, entry.getNodeId(), entry.getScope(), entry.getPath());
                    if (path == null || !workspacePort.exists(path)) {
                        if (required) {
                            throw new NodeFailureException(
                                    "MISSING_EXECUTION_CONTEXT",
                                    "Missing required artifact_ref: node_id=" + entry.getNodeId() + ", path="
                                            + entry.getPath(),
                                    false);
                        }
                        continue;
                    }
                    String transferMode = normalizeTransferMode(entry.getTransferMode());
                    if ("by_value".equals(transferMode)) {
                        long sizeBytes = fileSize(path);
                        inlineCandidates.add(new InlineArtifactContext(
                                artifactKeyForPath(entry.getPath()),
                                path,
                                entry.getNodeId() == null ? "" : entry.getNodeId(),
                                sizeBytes));
                    } else {
                        resolved.add(byRefArtifactContext(path, artifactKeyForPath(entry.getPath()),
                                entry.getNodeId() == null ? "" : entry.getNodeId()));
                    }
                }
                default -> {
                }
            }
        }

        long totalInlineBytes = inlineCandidates.stream().mapToLong(InlineArtifactContext::sizeBytes).sum();
        boolean inlineOverflow = totalInlineBytes > maxInlineArtifactBytes;
        if (inlineOverflow) {
            log.warn(
                    "artifact_ref by_value total exceeds limit: requested={} bytes (~{} tokens), limit={} tokens (~{} bytes). Fallback to by_ref for {} artifact(s)",
                    totalInlineBytes,
                    approxTokens(totalInlineBytes),
                    maxInlineArtifactTokens,
                    maxInlineArtifactBytes,
                    inlineCandidates.size());
        }
        for (InlineArtifactContext candidate : inlineCandidates) {
            if (inlineOverflow) {
                resolved.add(byRefArtifactContext(candidate.path(), candidate.artifactKey(), candidate.sourceNodeId()));
                continue;
            }
            String content = readInlineArtifactContent(candidate.path());
            resolved.add(Map.of(
                    "type", "artifact_ref",
                    "artifact_key", candidate.artifactKey(),
                    "path", candidate.path().toString(),
                    "source_node_id", candidate.sourceNodeId(),
                    "transfer_mode", "by_value",
                    "content", content,
                    "content_checksum", ChecksumUtil.sha256(content),
                    "size_bytes", candidate.sizeBytes(),
                    "size_tokens_approx", approxTokens(candidate.sizeBytes())));
        }
        return resolved;
    }

    private void applyTransition(
            RunEntity run,
            NodeExecutionEntity execution,
            GateInstanceEntity gate,
            String targetNodeId,
            String transitionLabel) {
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new NodeFailureException(
                    "INVALID_TRANSITION",
                    "Transition target is missing for " + transitionLabel,
                    false);
        }
        FlowModel flowModel = parseFlowSnapshot(run);
        requireNode(flowModel, targetNodeId);
        runtimeStepTxService.applyTransition(
                run.getId(),
                execution == null ? null : execution.getId(),
                gate == null ? null : gate.getId(),
                targetNodeId,
                transitionLabel);
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

    private AgentSessionSelection selectAgentSession(RunEntity run, NodeModel node) {
        AiSessionMode runSessionMode = run.getAiSessionMode() == null
                ? AiSessionMode.ISOLATED_ATTEMPT_SESSIONS
                : run.getAiSessionMode();

        if (runSessionMode == AiSessionMode.SHARED_RUN_SESSION) {
            String runSessionId = trimToNull(run.getRunSessionId());
            if (runSessionId == null) {
                throw new NodeFailureException(
                        "RUN_SESSION_NOT_INITIALIZED",
                        "shared_run_session requires run_session_id",
                        false
                );
            }
            AgentSessionCommandMode commandMode = nodeExecutionRepository.existsByRunIdAndAgentSessionId(run.getId(), runSessionId)
                    ? AgentSessionCommandMode.RESUME_PREVIOUS_SESSION
                    : AgentSessionCommandMode.NEW_SESSION;
            return new AgentSessionSelection(runSessionMode, commandMode, runSessionId);
        }

        ReworkSessionPolicy pendingPolicy = runtimeStepTxService.consumePendingReworkSessionPolicy(run.getId());
        if (pendingPolicy == ReworkSessionPolicy.RESUME_PREVIOUS_SESSION) {
            String previousSessionId = nodeExecutionRepository
                    .findFirstByRunIdAndNodeIdAndAgentSessionIdIsNotNullOrderByAttemptNoDesc(run.getId(), node.getId())
                    .map(NodeExecutionEntity::getAgentSessionId)
                    .map(this::trimToNull)
                    .orElse(null);
            if (previousSessionId == null) {
                throw new NodeFailureException(
                        "AGENT_SESSION_RESUME_FAILED",
                        "Requested resume_previous_session, but previous agent session was not found",
                        false
                );
            }
            return new AgentSessionSelection(
                    runSessionMode,
                    AgentSessionCommandMode.RESUME_PREVIOUS_SESSION,
                    previousSessionId
            );
        }

        return new AgentSessionSelection(
                runSessionMode,
                AgentSessionCommandMode.NEW_SESSION,
                UUID.randomUUID().toString()
        );
    }

    private void createCheckpointBeforeExecution(
            RunEntity run,
            NodeModel node,
            NodeExecutionEntity execution,
            String nodeKind) {
        if (!Boolean.TRUE.equals(node.getCheckpointBeforeRun())) {
            return;
        }
        if (!Set.of("ai", "command").contains(nodeKind)) {
            return;
        }

        Path operationRoot = resolveNodeExecutionRoot(run, execution);
        Path workingDirectory = resolveProjectScopeRoot(resolveRunWorkspaceRoot(run));
        String checkpointCommitMessage = "checkpoint:" + node.getId() + ":" + execution.getId();
        configureCheckpointGitIdentity(run, execution, workingDirectory, operationRoot);

        CommandResult addResult = runGitCommand(
                run,
                execution,
                "checkpoint_add",
                List.of("git", "add", "-A"),
                workingDirectory,
                operationRoot);
        ensureGitCommandSuccess("checkpoint add", addResult);

        CommandResult commitResult = runGitCommand(
                run,
                execution,
                "checkpoint_commit",
                List.of("git", "commit", "--allow-empty", "-m", checkpointCommitMessage),
                workingDirectory,
                operationRoot);
        ensureGitCommandSuccess("checkpoint commit", commitResult);

        CommandResult shaResult = runGitCommand(
                run,
                execution,
                "checkpoint_rev_parse",
                List.of("git", "rev-parse", "HEAD"),
                workingDirectory,
                operationRoot);
        ensureGitCommandSuccess("checkpoint rev-parse", shaResult);

        String checkpointSha = parseCheckpointSha(shaResult.stdout());
        if (checkpointSha == null) {
            runtimeStepTxService.appendAudit(
                    run.getId(),
                    execution.getId(),
                    null,
                    "checkpoint_creation_failed",
                    ActorType.SYSTEM,
                    "runtime",
                    mapOf(
                            "phase", "rev_parse",
                            "reason", "empty_or_invalid_sha",
                            "stdout", truncate(shaResult.stdout(), 4000),
                            "stderr", truncate(shaResult.stderr(), 4000)));
            throw new NodeFailureException(
                    "CHECKPOINT_CREATION_FAILED",
                    "Failed to resolve checkpoint commit SHA",
                    false);
        }

        runtimeStepTxService.markNodeExecutionCheckpoint(run.getId(), execution.getId(), checkpointSha,
                clockPort.now());
        runtimeStepTxService.appendAudit(
                run.getId(),
                execution.getId(),
                null,
                "checkpoint_created",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "checkpoint_commit_sha", checkpointSha,
                        "stdout", truncate(commitResult.stdout(), 4000),
                        "stderr", truncate(commitResult.stderr(), 4000)));
    }

    private void configureCheckpointGitIdentity(
            RunEntity run,
            NodeExecutionEntity execution,
            Path workingDirectory,
            Path operationRoot) {
        SettingsService.RuntimeSettings settings = settingsService.getRuntimeSettings();
        String localUser = trimToNull(settings.localGitUsername());
        String localEmail = trimToNull(settings.localGitEmail());
        if (localUser == null || localEmail == null) {
            throw new NodeFailureException(
                    "CHECKPOINT_CREATION_FAILED",
                    "Runtime local git identity is empty",
                    false);
        }

        CommandResult configNameResult = runGitCommand(
                run,
                execution,
                "checkpoint_config_user_name",
                List.of("git", "config", "user.name", localUser),
                workingDirectory,
                operationRoot);
        ensureGitCommandSuccess("checkpoint config user.name", configNameResult);

        CommandResult configEmailResult = runGitCommand(
                run,
                execution,
                "checkpoint_config_user_email",
                List.of("git", "config", "user.email", localEmail),
                workingDirectory,
                operationRoot);
        ensureGitCommandSuccess("checkpoint config user.email", configEmailResult);
    }

    private CommandResult runGitCommand(
            RunEntity run,
            NodeExecutionEntity execution,
            String operationName,
            List<String> command,
            Path workingDirectory,
            Path operationRoot) {
        Path stdoutPath = operationRoot.resolve(operationName + ".stdout.log");
        Path stderrPath = operationRoot.resolve(operationName + ".stderr.log");
        try {
            ProcessExecutionPort.ProcessExecutionResult result = processExecutionPort.execute(
                    new ProcessExecutionPort.ProcessExecutionRequest(
                            run.getId(),
                            command,
                            workingDirectory,
                            settingsService.getAiTimeoutSeconds(),
                            stdoutPath,
                            stderrPath,
                            true));
            return new CommandResult(
                    result.exitCode(),
                    result.stdout(),
                    result.stderr(),
                    result.stdoutPath(),
                    result.stderrPath());
        } catch (IOException ex) {
            runtimeStepTxService.appendAudit(
                    run.getId(),
                    execution == null ? null : execution.getId(),
                    null,
                    "checkpoint_command_failed",
                    ActorType.SYSTEM,
                    "runtime",
                    mapOf(
                            "operation", operationName,
                            "command", command,
                            "error", ex.getMessage()));
            throw new NodeFailureException(
                    "CHECKPOINT_CREATION_FAILED",
                    "Checkpoint command failed: " + operationName,
                    false);
        }
    }

    private void ensureGitCommandSuccess(String operation, CommandResult result) {
        if (result.exitCode() == 0) {
            return;
        }
        throw new NodeFailureException(
                "CHECKPOINT_CREATION_FAILED",
                operation + " failed with exit code " + result.exitCode(),
                false,
                mapOf(
                        "stdout", truncate(result.stdout(), 4000),
                        "stderr", truncate(result.stderr(), 4000)));
    }

    private String parseCheckpointSha(String stdout) {
        String normalized = trimToNull(stdout);
        if (normalized == null) {
            return null;
        }
        String firstLine = normalized.split("\\R", 2)[0].trim();
        if (firstLine.matches("^[0-9a-fA-F]{40}$")) {
            return firstLine.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private List<HumanInputEditableArtifact> resolveHumanInputEditableArtifacts(
            RunEntity run,
            NodeModel humanInputNode,
            NodeExecutionEntity execution) {
        FlowModel flowModel = parseFlowSnapshot(run);
        Map<String, NodeModel> nodesById = flowModel.getNodes() == null
                ? Map.of()
                : flowModel.getNodes().stream()
                        .filter((candidate) -> candidate != null && candidate.getId() != null
                                && !candidate.getId().isBlank())
                        .collect(java.util.stream.Collectors.toMap(NodeModel::getId, Function.identity(), (a, b) -> a));
        List<HumanInputEditableArtifact> result = new ArrayList<>();
        List<ExecutionContextEntry> entries = humanInputNode.getExecutionContext() == null ? List.of()
                : humanInputNode.getExecutionContext();
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
                    sourceVersion == null ? null : sourceVersion.getId().toString()));
        }
        return result;
    }

    private boolean isProducedArtifactDeclared(NodeModel sourceNode, String artifactPath, String scope) {
        if (sourceNode == null || sourceNode.getProducedArtifacts() == null || artifactPath == null
                || artifactPath.isBlank()) {
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

    private RunEntity getRunEntity(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new ValidationException("Run not found: " + runId));
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

    private Path resolveProjectRoot(RunEntity run) {
        return resolveProjectScopeRoot(resolveRunWorkspaceRoot(run));
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

    private Path resolveProducedArtifactPath(RunEntity run, NodeExecutionEntity execution, String scopeRaw,
            String fileName) {
        if ("project".equals(defaultScope(scopeRaw))) {
            return resolvePath(run, scopeRaw, fileName);
        }
        Path nodeDir = resolveNodeExecutionRoot(run, execution);
        Path resolved = nodeDir.resolve(fileName).normalize();
        if (!resolved.startsWith(nodeDir)) {
            throw new NodeFailureException("PATH_POLICY_VIOLATION", "Path escapes node dir: " + fileName, false);
        }
        return resolved;
    }

    private Path resolveArtifactRefPath(RunEntity run, String sourceNodeId, String scopeRaw, String fileName) {
        if ("project".equals(defaultScope(scopeRaw))) {
            return resolvePath(run, "project", fileName);
        }
        if (sourceNodeId == null || sourceNodeId.isBlank()) {
            throw new NodeFailureException("MISSING_EXECUTION_CONTEXT",
                    "node_id is required for run-scoped artifact_ref", false);
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

    private String relativizePath(Path root, Path path) {
        try {
            return root.relativize(path).toString().replace('\\', '/');
        } catch (Exception ex) {
            return path.toAbsolutePath().normalize().toString().replace('\\', '/');
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

    private void recordArtifactVersion(
            RunEntity run,
            String nodeId,
            String artifactKey,
            Path path,
            ArtifactScope scope,
            ArtifactKind kind,
            Integer explicitSizeBytes) {
        long size = explicitSizeBytes == null ? fileSize(path) : explicitSizeBytes.longValue();
        runtimeStepTxService.recordArtifactVersion(
                run.getId(),
                nodeId,
                artifactKey,
                path.toString(),
                scope,
                kind,
                fileChecksumOrNull(path),
                size);
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
            return DEFAULT_GATE_ASSIGNEE_ROLE;
        }
        for (String role : allowedRoles) {
            if (role != null && !role.isBlank()) {
                return role;
            }
        }
        return DEFAULT_GATE_ASSIGNEE_ROLE;
    }

    private boolean isRunCancelled(UUID runId) {
        return runRepository.findById(runId)
                .map((runEntity) -> runEntity.getStatus() == RunStatus.CANCELLED)
                .orElse(false);
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

    private List<GitChangeEntry> listGitChanges(RunEntity run) {
        CommandResult statusResult = runGitQuery(run, List.of("git", "status", "--porcelain", "--untracked-files=all"));
        if (statusResult.exitCode() != 0) {
            throw new ValidationException("Failed to read git status for run: " + run.getId());
        }
        List<String> changedPaths = expandGitPaths(run, parseGitStatusPaths(statusResult.stdout()));
        CommandResult numstatResult = runGitQuery(run, List.of("git", "diff", "--numstat"));
        if (numstatResult.exitCode() != 0) {
            throw new ValidationException("Failed to read git numstat for run: " + run.getId());
        }
        Map<String, GitNumstat> numstatByPath = parseGitNumstat(numstatResult.stdout());
        List<GitChangeEntry> result = new ArrayList<>();
        for (String path : changedPaths) {
            String status = inferGitStatus(path, statusResult.stdout());
            GitNumstat stat = numstatByPath.get(path);
            if (stat == null && "untracked".equals(status)) {
                stat = readUntrackedNumstat(run, path);
            }
            int added = stat == null ? 0 : stat.added();
            int removed = stat == null ? 0 : stat.removed();
            boolean binary = stat != null && stat.binary();
            result.add(new GitChangeEntry(path, status, added, removed, binary));
        }
        return result;
    }

    private GitNumstat readUntrackedNumstat(RunEntity run, String path) {
        CommandResult result = runGitQuery(run, List.of(
                "git", "diff", "--numstat", "--no-index", "--", "/dev/null", path));
        if (result.exitCode() != 0 && result.exitCode() != 1) {
            return null;
        }
        Map<String, GitNumstat> parsed = parseGitNumstat(result.stdout());
        if (parsed.containsKey(path)) {
            return parsed.get(path);
        }
        if (!parsed.isEmpty()) {
            return parsed.values().iterator().next();
        }
        return null;
    }

    private List<String> expandGitPaths(RunEntity run, List<String> rawPaths) {
        if (rawPaths == null || rawPaths.isEmpty()) {
            return List.of();
        }
        Path projectRoot = resolveProjectRoot(run);
        List<String> expanded = new ArrayList<>();
        for (String raw : rawPaths) {
            String path = trimToNull(raw);
            if (path == null) {
                continue;
            }
            Path resolved = projectRoot.resolve(path).normalize();
            if (!resolved.startsWith(projectRoot)) {
                continue;
            }
            if (workspacePort.isDirectory(resolved)) {
                try {
                    for (Path file : workspacePort.listRegularFilesRecursively(resolved)) {
                        expanded.add(projectRoot.relativize(file).toString().replace('\\', '/'));
                    }
                } catch (IOException ex) {
                    expanded.add(path.replace('\\', '/'));
                }
            } else {
                expanded.add(path.replace('\\', '/'));
            }
        }
        return expanded.stream().distinct().toList();
    }

    private CommandResult runGitQuery(RunEntity run, List<String> command) {
        Path operationRoot = resolveRunScopeRoot(resolveRunWorkspaceRoot(run)).resolve(".runtime")
                .resolve("gate-review");
        String suffix = String.valueOf(System.nanoTime());
        Path stdoutPath = operationRoot.resolve("git-" + suffix + ".stdout.log");
        Path stderrPath = operationRoot.resolve("git-" + suffix + ".stderr.log");
        try {
            ProcessExecutionPort.ProcessExecutionResult result = processExecutionPort.execute(
                    new ProcessExecutionPort.ProcessExecutionRequest(
                            run.getId(),
                            command,
                            resolveProjectRoot(run),
                            Math.max(10, settingsService.getAiTimeoutSeconds()),
                            stdoutPath,
                            stderrPath,
                            true));
            return new CommandResult(
                    result.exitCode(),
                    result.stdout(),
                    result.stderr(),
                    result.stdoutPath(),
                    result.stderrPath());
        } catch (IOException ex) {
            throw new ValidationException("Failed to execute git command for run: " + run.getId());
        }
    }

    private List<String> parseGitStatusPaths(String rawStatus) {
        List<String> paths = new ArrayList<>();
        if (rawStatus == null || rawStatus.isBlank()) {
            return paths;
        }
        for (String line : rawStatus.split("\n")) {
            if (line == null || line.isBlank() || line.length() < 4) {
                continue;
            }
            String pathPart = line.substring(3).trim();
            if (pathPart.contains(" -> ")) {
                String[] parts = pathPart.split(" -> ");
                pathPart = parts[parts.length - 1].trim();
            }
            if (!pathPart.isBlank()) {
                paths.add(pathPart);
            }
        }
        return paths.stream().distinct().toList();
    }

    private Map<String, GitNumstat> parseGitNumstat(String rawNumstat) {
        Map<String, GitNumstat> stats = new LinkedHashMap<>();
        if (rawNumstat == null || rawNumstat.isBlank()) {
            return stats;
        }
        for (String line : rawNumstat.split("\n")) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t");
            if (parts.length < 3) {
                continue;
            }
            String addedRaw = parts[0].trim();
            String removedRaw = parts[1].trim();
            String path = normalizeNumstatPath(parts[2].trim());
            boolean binary = "-".equals(addedRaw) || "-".equals(removedRaw);
            int added = binary ? 0 : parseIntSafe(addedRaw);
            int removed = binary ? 0 : parseIntSafe(removedRaw);
            stats.put(path, new GitNumstat(added, removed, binary));
        }
        return stats;
    }

    private String normalizeNumstatPath(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.contains(" => ")) {
            String[] parts = path.split(" => ");
            path = parts[parts.length - 1].trim();
        }
        if (path.startsWith("b/")) {
            path = path.substring(2);
        }
        return path;
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String inferGitStatus(String path, String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return "modified";
        }
        for (String line : rawStatus.split("\n")) {
            if (line == null || line.isBlank() || line.length() < 4) {
                continue;
            }
            String candidatePath = line.substring(3).trim();
            if (candidatePath.contains(" -> ")) {
                String[] parts = candidatePath.split(" -> ");
                candidatePath = parts[parts.length - 1].trim();
            }
            if (!path.equals(candidatePath)) {
                continue;
            }
            String code = line.substring(0, 2).trim();
            if (code.isBlank()) {
                return "modified";
            }
            if ("??".equals(code))
                return "untracked";
            if (code.contains("A"))
                return "added";
            if (code.contains("D"))
                return "deleted";
            if (code.contains("R"))
                return "renamed";
            if (code.contains("M"))
                return "modified";
            return code.toLowerCase(Locale.ROOT);
        }
        return "modified";
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

    private String normalizeTransferMode(String value) {
        String normalized = normalize(trimToNull(value));
        if ("by_value".equals(normalized)) {
            return "by_value";
        }
        return "by_ref";
    }

    private int normalizeMaxInlineArtifactTokens(Integer configuredValue) {
        if (configuredValue == null || configuredValue <= 0) {
            return DEFAULT_MAX_INLINE_ARTIFACT_TOKENS;
        }
        return configuredValue;
    }

    private long toApproxBytes(int tokenCount) {
        return (long) tokenCount * APPROX_BYTES_PER_TOKEN;
    }

    private long approxTokens(long bytes) {
        if (bytes <= 0) {
            return 0;
        }
        return Math.max(1, Math.round((double) bytes / APPROX_BYTES_PER_TOKEN));
    }

    private String capitalize(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "Agent";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ValidationException("Failed to serialize JSON payload");
        }
    }

    private record GitNumstat(
            int added,
            int removed,
            boolean binary) {
    }

    private record InlineArtifactContext(
            String artifactKey,
            Path path,
            String sourceNodeId,
            long sizeBytes) {
    }

    private record HumanInputEditableArtifact(
            String artifactKey,
            String path,
            String scope,
            boolean required,
            String sourceNodeId,
            String sourcePath,
            String sourceArtifactVersionId) {
    }

    private record CommandResult(
            int exitCode,
            String stdout,
            String stderr,
            String stdoutPath,
            String stderrPath) {
    }

    private record ReworkDiscardAvailability(
            String mode,
            boolean keepChanges,
            boolean keepChangesSelectable,
            boolean discardAvailable,
            String unavailableReason) {
    }

    public static class NodeFailureException extends RuntimeException {
        private final String errorCode;
        private final String auditEventType;
        private final Map<String, Object> details;

        public NodeFailureException(String errorCode, String message, boolean validationFailure) {
            this(errorCode, message, validationFailure, Map.of());
        }

        public NodeFailureException(String errorCode, String message, boolean validationFailure,
                Map<String, Object> details) {
            super(message);
            this.errorCode = errorCode;
            this.auditEventType = validationFailure ? "node_validation_failed" : "node_execution_failed";
            this.details = details == null ? Map.of() : details;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getAuditEventType() {
            return auditEventType;
        }

        public Map<String, Object> getDetails() {
            return details;
        }
    }

    private List<AgentPromptBuilder.WorkflowProgressEntry> loadWorkflowProgress(UUID runId) {
        List<NodeExecutionEntity> executions = nodeExecutionRepository
                .findByRunIdAndNodeKindAndStatusAndStepSummaryJsonIsNotNullOrderByStartedAtAsc(
                        runId, "ai", NodeExecutionStatus.SUCCEEDED);
        List<AgentPromptBuilder.WorkflowProgressEntry> result = new ArrayList<>();
        for (int i = 0; i < executions.size(); i++) {
            NodeExecutionEntity exec = executions.get(i);
            String summary = extractFirstAction(exec.getStepSummaryJson());
            if (summary != null) {
                result.add(new AgentPromptBuilder.WorkflowProgressEntry(i + 1, exec.getNodeId(), summary));
            }
        }
        return result;
    }

    private String extractFirstAction(String stepSummaryJson) {
        if (stepSummaryJson == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(stepSummaryJson);
            JsonNode actions = root.path("actions");
            if (actions.isArray() && !actions.isEmpty()) {
                String action = actions.get(0).asText("").trim();
                return action.isEmpty() ? null : action;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String extractStepSummary(RunEntity run, NodeExecutionEntity execution, String stdout) {
        String fileSummary = extractStepSummaryFromFile(run, execution);
        if (fileSummary != null) {
            return fileSummary;
        }
        return extractStepSummaryFromStdout(stdout);
    }

    private String extractStepSummaryFromFile(RunEntity run, NodeExecutionEntity execution) {
        Path summaryPath = resolveStepSummaryPath(run, execution);
        if (!workspacePort.exists(summaryPath) || workspacePort.isDirectory(summaryPath)) {
            return null;
        }
        try {
            String raw = workspacePort.readString(summaryPath, StandardCharsets.UTF_8);
            return parseAndValidateStepSummary(raw);
        } catch (IOException ex) {
            log.warn("Failed to read step summary file at {}: {}", summaryPath, ex.getMessage());
            return null;
        }
    }

    private String extractStepSummaryFromStdout(String stdout) {
        if (stdout == null) {
            return null;
        }
        int markerIdx = stdout.lastIndexOf("STEP_SUMMARY:");
        if (markerIdx < 0) {
            return null;
        }
        String after = stdout.substring(markerIdx + "STEP_SUMMARY:".length());
        int jsonBlockStart = after.indexOf("```json");
        if (jsonBlockStart < 0) {
            return null;
        }
        int contentStart = after.indexOf('\n', jsonBlockStart);
        if (contentStart < 0) {
            return null;
        }
        contentStart++;
        int jsonEnd = after.indexOf("```", contentStart);
        if (jsonEnd < 0) {
            return null;
        }
        String json = after.substring(contentStart, jsonEnd).trim();
        return parseAndValidateStepSummary(json);
    }

    private Path resolveStepSummaryPath(RunEntity run, NodeExecutionEntity execution) {
        return resolveNodeExecutionRoot(run, execution).resolve(STEP_SUMMARY_FILE_NAME);
    }

    private String parseAndValidateStepSummary(String rawJson) {
        String json = trimToNull(rawJson);
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.path("step_id").isMissingNode()
                    || root.path("attempt").isMissingNode()
                    || root.path("status").isMissingNode()
                    || root.path("actions").isMissingNode()) {
                return null;
            }
            return json;
        } catch (Exception ignored) {
            return null;
        }
    }

    private record AgentSessionSelection(
            AiSessionMode sessionMode,
            AgentSessionCommandMode commandMode,
            String sessionId
    ) {}

    private static class RunCancelledException extends RuntimeException {
        private RunCancelledException(String message) {
            super(message);
        }
    }
}

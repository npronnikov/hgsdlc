package ru.hgd.sdlc.runtime.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.flow.application.FlowYamlParser;
import ru.hgd.sdlc.flow.domain.FlowLifecycleStatus;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.FlowStatus;
import ru.hgd.sdlc.flow.domain.FlowVersion;
import ru.hgd.sdlc.flow.infrastructure.FlowVersionRepository;
import ru.hgd.sdlc.project.domain.Project;
import ru.hgd.sdlc.project.domain.ProjectStatus;
import ru.hgd.sdlc.project.infrastructure.ProjectRepository;
import ru.hgd.sdlc.runtime.application.CatalogContentResolver;
import ru.hgd.sdlc.runtime.application.RuntimeStepTxService;
import ru.hgd.sdlc.runtime.application.command.CreateRunCommand;
import ru.hgd.sdlc.runtime.application.port.ClockPort;
import ru.hgd.sdlc.runtime.application.port.IdentityPort;
import ru.hgd.sdlc.runtime.application.port.ProcessExecutionPort;
import ru.hgd.sdlc.runtime.application.port.WorkspacePort;
import ru.hgd.sdlc.runtime.domain.AiSessionMode;
import ru.hgd.sdlc.runtime.domain.ActorType;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.NodeExecutionStatus;
import ru.hgd.sdlc.runtime.domain.PrCommitStrategy;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.domain.RunPublishMode;
import ru.hgd.sdlc.runtime.domain.RunPublishStatus;
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.infrastructure.NodeExecutionRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;
import ru.hgd.sdlc.settings.application.SettingsService;

@Service
public class RunLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(RunLifecycleService.class);
    private static final List<RunStatus> ACTIVE_RUN_STATUSES = List.of(
            RunStatus.CREATED,
            RunStatus.RUNNING,
            RunStatus.WAITING_GATE,
            RunStatus.WAITING_PUBLISH,
            RunStatus.PUBLISH_FAILED
    );

    private final RunRepository runRepository;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final ProjectRepository projectRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final RuntimeStepTxService runtimeStepTxService;
    private final RunStepService runStepService;
    private final FlowYamlParser flowYamlParser;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final CatalogContentResolver catalogContentResolver;
    private final ProcessExecutionPort processExecutionPort;
    private final WorkspacePort workspacePort;
    private final ClockPort clockPort;
    private final IdentityPort identityPort;
    private final RunPublishService runPublishService;

    public RunLifecycleService(
            RunRepository runRepository,
            NodeExecutionRepository nodeExecutionRepository,
            ProjectRepository projectRepository,
            FlowVersionRepository flowVersionRepository,
            RuntimeStepTxService runtimeStepTxService,
            RunStepService runStepService,
            FlowYamlParser flowYamlParser,
            ObjectMapper objectMapper,
            SettingsService settingsService,
            CatalogContentResolver catalogContentResolver,
            ProcessExecutionPort processExecutionPort,
            WorkspacePort workspacePort,
            ClockPort clockPort,
            IdentityPort identityPort,
            RunPublishService runPublishService
    ) {
        this.runRepository = runRepository;
        this.nodeExecutionRepository = nodeExecutionRepository;
        this.projectRepository = projectRepository;
        this.flowVersionRepository = flowVersionRepository;
        this.runtimeStepTxService = runtimeStepTxService;
        this.runStepService = runStepService;
        this.flowYamlParser = flowYamlParser;
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.catalogContentResolver = catalogContentResolver;
        this.processExecutionPort = processExecutionPort;
        this.workspacePort = workspacePort;
        this.clockPort = clockPort;
        this.identityPort = identityPort;
        this.runPublishService = runPublishService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RunEntity createRun(CreateRunCommand command, User user) {
        validateCreateRunCommand(command);
        Project project = projectRepository.findById(command.projectId())
                .orElseThrow(() -> new NotFoundException("Project not found: " + command.projectId()));
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new ValidationException("Project is archived and cannot be launched: " + command.projectId());
        }
        FlowVersion flowVersion = flowVersionRepository.findFirstByCanonicalName(command.flowCanonicalName())
                .orElseThrow(() -> new NotFoundException("Flow not found: " + command.flowCanonicalName()));
        if (flowVersion.getStatus() != FlowStatus.PUBLISHED) {
            throw new ValidationException("Flow is not published: " + command.flowCanonicalName());
        }
        if (flowVersion.getLifecycleStatus() != null && flowVersion.getLifecycleStatus() != FlowLifecycleStatus.ACTIVE) {
            throw new ValidationException("Flow is deprecated and cannot be launched: " + command.flowCanonicalName());
        }
        FlowModel flowModel = flowYamlParser.parse(catalogContentResolver.resolveFlowYaml(flowVersion));
        if (flowModel.getNodes() == null || flowModel.getNodes().isEmpty()) {
            throw new ValidationException("Flow has no nodes");
        }
        if (flowModel.getStartNodeId() == null || flowModel.getStartNodeId().isBlank()) {
            throw new ValidationException("Flow start_node_id is required");
        }
        flowModel.setCodingAgent(flowVersion.getCodingAgent());

        boolean activeRunExists = runRepository.existsByProjectIdAndTargetBranchAndStatusIn(
                project.getId(),
                normalizeBranch(command.targetBranch()),
                ACTIVE_RUN_STATUSES
        );
        if (activeRunExists) {
            throw new ConflictException("Active run already exists for project and target branch");
        }

        UUID runId = UUID.randomUUID();
        AiSessionMode aiSessionMode = resolveAiSessionMode(command.aiSessionMode());
        String runSessionId = aiSessionMode == AiSessionMode.SHARED_RUN_SESSION ? UUID.randomUUID().toString() : null;
        RunPublishMode publishMode = resolvePublishMode(command.publishMode());
        PrCommitStrategy prCommitStrategy = resolvePrCommitStrategy(publishMode, command.prCommitStrategy());
        String workBranch = resolveWorkBranch(runId, command.workBranch(), command.targetBranch());
        RunPublishStatus initialPublishStatus = RunPublishStatus.PENDING;
        RunPublishStatus initialPushStatus = switch (publishMode) {
            case PR, BRANCH -> RunPublishStatus.PENDING;
            case LOCAL -> RunPublishStatus.SKIPPED;
        };
        RunPublishStatus initialPrStatus = publishMode == RunPublishMode.PR
                ? RunPublishStatus.PENDING
                : RunPublishStatus.SKIPPED;

        Path runWorkspaceRoot = resolveRunWorkspaceRoot(settingsService.getWorkspaceRoot(), runId);
        createDirectories(runWorkspaceRoot);

        List<String> manifestEntries = List.of();
        return runtimeStepTxService.createRun(
                runId,
                project.getId(),
                normalizeBranch(command.targetBranch()),
                flowVersion.getCanonicalName(),
                toJson(flowModel),
                aiSessionMode,
                runSessionId,
                publishMode,
                workBranch,
                prCommitStrategy,
                initialPublishStatus,
                initialPushStatus,
                initialPrStatus,
                flowModel.getStartNodeId(),
                command.featureRequest().trim(),
                toJson(manifestEntries),
                runWorkspaceRoot.toString(),
                identityPort.resolveActorId(user),
                clockPort.now(),
                resolveSkipGates(user)
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void startRun(UUID runId) {
        RunEntity run = getRunEntity(runId);
        if (run.getStatus() == RunStatus.CREATED) {
            run = runtimeStepTxService.markRunStarted(runId, clockPort.now());
        }
        if (run.getStatus() == RunStatus.RUNNING) {
            ensureWorkspacePrepared(run);
            runTickSafely(runId);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RunEntity resumeRun(UUID runId) {
        RunEntity run = getRunEntity(runId);
        if (run.getStatus() == RunStatus.CREATED || run.getStatus() == RunStatus.RUNNING) {
            startRun(runId);
        } else if (run.getStatus() == RunStatus.WAITING_PUBLISH) {
            runPublishService.dispatchPublish(runId);
        }
        return getRunEntity(runId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RunEntity cancelRun(UUID runId, User user) {
        return runtimeStepTxService.cancelRun(runId, identityPort.resolveActorId(user));
    }

    private static final Set<String> RETRYABLE_AGENT_ERROR_CODES = Set.of(
            "AGENT_EXECUTION_FAILED", "AGENT_SESSION_RESUME_FAILED"
    );

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RunEntity retryRun(UUID runId, User user) {
        RunEntity run = getRunEntity(runId);
        if (run.getStatus() != RunStatus.FAILED) {
            throw new ConflictException("Retry is allowed only for failed runs");
        }
        String actorId = identityPort.resolveActorId(user);
        if ("NODE_VALIDATION_FAILED".equals(run.getErrorCode())) {
            NodeExecutionEntity failedAi = nodeExecutionRepository
                    .findFirstByRunIdAndNodeIdAndNodeKindAndStatusAndErrorCodeOrderByAttemptNoDesc(
                            run.getId(),
                            run.getCurrentNodeId(),
                            "ai",
                            NodeExecutionStatus.FAILED,
                            "NODE_VALIDATION_FAILED"
                    )
                    .orElseThrow(() -> new ConflictException("No failed AI validation execution found for retry"));
            runtimeStepTxService.resetRunForValidationRetry(run.getId(), failedAi.getNodeId(), actorId, failedAi.getAttemptNo());
        } else if (RETRYABLE_AGENT_ERROR_CODES.contains(run.getErrorCode())) {
            NodeExecutionEntity failedExecution = nodeExecutionRepository
                    .findFirstByRunIdAndNodeIdAndStatusOrderByAttemptNoDesc(
                            run.getId(),
                            run.getCurrentNodeId(),
                            NodeExecutionStatus.FAILED
                    )
                    .orElseThrow(() -> new ConflictException("No failed execution found for retry"));
            runtimeStepTxService.resetRunForAgentRetry(run.getId(), failedExecution.getNodeId(), actorId,
                    failedExecution.getAttemptNo(), run.getErrorCode());
        } else {
            throw new ConflictException("Retry is not supported for error code: " + run.getErrorCode());
        }
        return getRunEntity(runId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RunEntity giveUpRun(UUID runId, User user) {
        RunEntity run = getRunEntity(runId);
        if (run.getStatus() != RunStatus.FAILED || !"NODE_VALIDATION_FAILED".equals(run.getErrorCode())) {
            throw new ConflictException("Give up is allowed only for failed AI node validation");
        }
        FlowModel flowModel = parseFlowSnapshot(run);
        NodeModel node = flowModel.getNodes() == null ? null
                : flowModel.getNodes().stream()
                        .filter(n -> run.getCurrentNodeId().equals(n.getId()))
                        .findFirst()
                        .orElse(null);
        if (node == null || !Boolean.TRUE.equals(node.getAllowRetry())) {
            throw new ConflictException("Current node does not support retry/give-up");
        }
        String onFailureTarget = node.getOnFailure();
        if (onFailureTarget == null || onFailureTarget.isBlank()) {
            throw new ConflictException("Current AI node has no on_failure transition defined");
        }
        boolean targetExists = flowModel.getNodes().stream()
                .anyMatch(n -> onFailureTarget.equals(n.getId()));
        if (!targetExists) {
            throw new ConflictException("on_failure target node not found in flow: " + onFailureTarget);
        }
        String actorId = identityPort.resolveActorId(user);
        runtimeStepTxService.giveUpRun(run.getId(), onFailureTarget, run.getCurrentNodeId(), actorId);
        return getRunEntity(runId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void recoverActiveRuns() {
        List<RunEntity> activeRuns = runRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(RunStatus.RUNNING, RunStatus.WAITING_GATE, RunStatus.WAITING_PUBLISH)
        );
        for (RunEntity run : activeRuns) {
            runtimeStepTxService.appendAudit(run.getId(), null, null, "run_recovered", ActorType.SYSTEM, "runtime", Map.of());
            if (run.getStatus() == RunStatus.RUNNING) {
                runTickSafely(run.getId());
            } else if (run.getStatus() == RunStatus.WAITING_PUBLISH) {
                runPublishService.dispatchPublish(run.getId());
            }
        }
    }

    private void runTickSafely(UUID runId) {
        try {
            runStepService.processRunStep(runId);
        } catch (RuntimeException ex) {
            log.error("runtime tick failed after commit for run_id={}", runId, ex);
            throw ex;
        }
    }

    private void ensureWorkspacePrepared(RunEntity run) {
        Path runWorkspaceRoot = resolveRunWorkspaceRoot(run);
        Path projectRoot = resolveProjectScopeRoot(runWorkspaceRoot);
        Path runScopeRoot = resolveRunScopeRoot(runWorkspaceRoot);
        createDirectories(runWorkspaceRoot);

        boolean checkoutCompleted = workspacePort.exists(projectRoot.resolve(".git"));
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
                            "work_branch", run.getWorkBranch(),
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
                                "work_branch", run.getWorkBranch(),
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
                runtimeStepTxService.failRun(run.getId(), ex.errorCode, ex.getMessage());
                throw ex;
            }
        }

        createDirectories(runScopeRoot.resolve("context"));
        createDirectories(runScopeRoot.resolve("nodes"));
        createDirectories(runScopeRoot.resolve("logs"));

        writeContextManifest(
                runScopeRoot,
                run.getFeatureRequest(),
                parseContextManifestEntries(run.getContextFileManifestJson())
        );
        materializeAgentSettingsJson(run, projectRoot);

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

    private void materializeAgentSettingsJson(RunEntity run, Path projectRoot) {
        String codingAgent = resolveRunCodingAgent(run);
        if (!"qwen".equals(codingAgent) && !"claude".equals(codingAgent) && !"gigacode".equals(codingAgent)) {
            return;
        }
        if (!settingsService.isRuntimeAgentSettingsJsonEnabled(codingAgent)) {
            return;
        }
        String settingsJson = settingsService.getRuntimeAgentSettingsJson(codingAgent);
        Path agentConfigRoot = projectRoot.resolve("." + codingAgent);
        Path settingsJsonPath = agentConfigRoot.resolve("settings.json");
        createDirectories(agentConfigRoot);
        writeFile(settingsJsonPath, settingsJson.getBytes(StandardCharsets.UTF_8));
        runtimeStepTxService.appendAudit(
                run.getId(),
                null,
                null,
                "agent_settings_json_materialized",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "coding_agent", codingAgent,
                        "path", settingsJsonPath.toString()
                )
        );
    }

    private CommandResult runGitCheckout(RunEntity run, Path projectRoot) {
        Project project = resolveProject(run.getProjectId());
        Path runWorkspaceRoot = resolveRunWorkspaceRoot(run);
        Path workspaceParent = runWorkspaceRoot.getParent() == null
                ? runWorkspaceRoot
                : runWorkspaceRoot.getParent();
        Path checkoutStdout = workspaceParent.resolve(run.getId() + ".checkout.stdout.log");
        Path checkoutStderr = workspaceParent.resolve(run.getId() + ".checkout.stderr.log");
        String repoUrl = trimToNull(project.getRepoUrl());
        if (repoUrl == null) {
            throw new NodeFailureException(
                    "CHECKOUT_FAILED",
                    "Project repo_url is empty",
                    mapOf(
                            "target_branch", run.getTargetBranch(),
                            "work_branch", run.getWorkBranch(),
                            "project_root", projectRoot.toString()
                    )
            );
        }
        if (workspacePort.exists(projectRoot)) {
            deleteDirectoryContents(projectRoot);
            try {
                workspacePort.deleteIfExists(projectRoot);
            } catch (IOException ex) {
                throw new NodeFailureException("CHECKOUT_FAILED", "Failed to clean project root before checkout");
            }
        }
        createDirectories(runWorkspaceRoot);
        try {
            CommandResult cloneResult = executeGitCommand(
                    run.getId(),
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
                    settingsService.getAiTimeoutSeconds(),
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
                        mapOf(
                                "repo_url", redactRepoUrl(repoUrl),
                                "target_branch", run.getTargetBranch(),
                                "work_branch", run.getWorkBranch(),
                                "project_root", projectRoot.toString(),
                                "exit_code", cloneResult.exitCode(),
                                "stdout_path", cloneResult.stdoutPath(),
                                "stderr_path", cloneResult.stderrPath(),
                                "stdout", truncate(cloneResult.stdout(), 12000),
                                "stderr", truncate(cloneResult.stderr(), 12000)
                        )
                );
            }
            ensureRuntimeMetadataIgnored(projectRoot);
            prepareWorkBranch(run, projectRoot, runWorkspaceRoot);
            return cloneResult;
        } catch (IOException ex) {
            throw new NodeFailureException(
                    "CHECKOUT_FAILED",
                    "git clone I/O failure: " + ex.getMessage()
                            + "; stderr_log=" + checkoutStderr
                            + "; stdout_log=" + checkoutStdout,
                    mapOf(
                            "repo_url", redactRepoUrl(repoUrl),
                            "target_branch", run.getTargetBranch(),
                            "work_branch", run.getWorkBranch(),
                            "project_root", projectRoot.toString(),
                            "stdout_path", checkoutStdout.toString(),
                            "stderr_path", checkoutStderr.toString()
                    )
            );
        }
    }

    private void prepareWorkBranch(RunEntity run, Path projectRoot, Path runWorkspaceRoot) {
        Path workspaceParent = runWorkspaceRoot.getParent() == null
                ? runWorkspaceRoot
                : runWorkspaceRoot.getParent();
        String suffix = run.getId() + ".work-branch";
        Path stdoutPath = workspaceParent.resolve(suffix + ".stdout.log");
        Path stderrPath = workspaceParent.resolve(suffix + ".stderr.log");

        CommandResult existsResult;
        try {
            existsResult = executeGitCommand(
                    run.getId(),
                    List.of(
                            "git",
                            "-C",
                            projectRoot.toString(),
                            "show-ref",
                            "--verify",
                            "--quiet",
                            "refs/heads/" + run.getWorkBranch()
                    ),
                    projectRoot,
                    Math.max(10, settingsService.getAiTimeoutSeconds()),
                    stdoutPath,
                    stderrPath
            );
        } catch (IOException ex) {
            throw new NodeFailureException("CHECKOUT_FAILED", "Failed to check local work_branch: " + ex.getMessage());
        }

        boolean workBranchExists = existsResult.exitCode() == 0;
        if (existsResult.exitCode() != 0 && existsResult.exitCode() != 1) {
            throw new NodeFailureException(
                    "CHECKOUT_FAILED",
                    "Failed to check local work_branch (exit=" + existsResult.exitCode() + ")",
                    mapOf(
                            "work_branch", run.getWorkBranch(),
                            "target_branch", run.getTargetBranch(),
                            "stdout_path", stdoutPath.toString(),
                            "stderr_path", stderrPath.toString(),
                            "stdout", truncate(existsResult.stdout(), 4000),
                            "stderr", truncate(existsResult.stderr(), 4000)
                    )
            );
        }

        if (workBranchExists) {
            CommandResult checkoutTargetResult = runGitOrThrow(
                    run,
                    projectRoot,
                    List.of("git", "-C", projectRoot.toString(), "checkout", run.getTargetBranch()),
                    "checkout_target_before_recreate",
                    stdoutPath,
                    stderrPath
            );
            CommandResult deleteBranchResult = runGitOrThrow(
                    run,
                    projectRoot,
                    List.of("git", "-C", projectRoot.toString(), "branch", "-D", run.getWorkBranch()),
                    "delete_existing_work_branch",
                    stdoutPath,
                    stderrPath
            );
            runtimeStepTxService.appendAudit(
                    run.getId(),
                    null,
                    null,
                    "work_branch_recreated",
                    ActorType.SYSTEM,
                    "runtime",
                    mapOf(
                            "work_branch", run.getWorkBranch(),
                            "target_branch", run.getTargetBranch(),
                            "checkout_stdout", truncate(checkoutTargetResult.stdout(), 2000),
                            "checkout_stderr", truncate(checkoutTargetResult.stderr(), 2000),
                            "delete_stdout", truncate(deleteBranchResult.stdout(), 2000),
                            "delete_stderr", truncate(deleteBranchResult.stderr(), 2000),
                            "stdout_path", stdoutPath.toString(),
                            "stderr_path", stderrPath.toString()
                    )
            );
        }

        CommandResult createBranchResult = runGitOrThrow(
                run,
                projectRoot,
                List.of(
                        "git",
                        "-C",
                        projectRoot.toString(),
                        "checkout",
                        "-b",
                        run.getWorkBranch(),
                        run.getTargetBranch()
                ),
                "create_work_branch",
                stdoutPath,
                stderrPath
        );
        runtimeStepTxService.appendAudit(
                run.getId(),
                null,
                null,
                "work_branch_prepared",
                ActorType.SYSTEM,
                "runtime",
                mapOf(
                        "work_branch", run.getWorkBranch(),
                        "target_branch", run.getTargetBranch(),
                        "stdout_path", stdoutPath.toString(),
                        "stderr_path", stderrPath.toString(),
                        "stdout", truncate(createBranchResult.stdout(), 4000),
                        "stderr", truncate(createBranchResult.stderr(), 4000)
                )
        );
    }

    private CommandResult runGitOrThrow(
            RunEntity run,
            Path projectRoot,
            List<String> command,
            String step,
            Path stdoutPath,
            Path stderrPath
    ) {
        CommandResult result;
        try {
            result = executeGitCommand(
                    run.getId(),
                    command,
                    projectRoot,
                    Math.max(10, settingsService.getAiTimeoutSeconds()),
                    stdoutPath,
                    stderrPath
            );
        } catch (IOException ex) {
            throw new NodeFailureException(
                    "CHECKOUT_FAILED",
                    "Failed to prepare work_branch at step " + step + ": " + ex.getMessage()
            );
        }
        if (result.exitCode() == 0) {
            return result;
        }
        throw new NodeFailureException(
                "CHECKOUT_FAILED",
                "Failed to prepare work_branch at step " + step + " (exit=" + result.exitCode() + ")",
                mapOf(
                        "step", step,
                        "work_branch", run.getWorkBranch(),
                        "target_branch", run.getTargetBranch(),
                        "stdout_path", stdoutPath.toString(),
                        "stderr_path", stderrPath.toString(),
                        "stdout", truncate(result.stdout(), 4000),
                        "stderr", truncate(result.stderr(), 4000)
                )
        );
    }

    private String readGitHead(RunEntity run, Path projectRoot) {
        Path runScopeRoot = resolveRunScopeRoot(resolveRunWorkspaceRoot(run));
        Path stdoutPath = runScopeRoot.resolve("logs").resolve("git-head.stdout.log");
        Path stderrPath = runScopeRoot.resolve("logs").resolve("git-head.stderr.log");
        try {
            CommandResult result = executeGitCommand(
                    null,
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

    private CommandResult executeGitCommand(
            UUID runId,
            List<String> command,
            Path workingDirectory,
            int timeoutSeconds,
            Path stdoutPath,
            Path stderrPath
    ) throws IOException {
        ProcessExecutionPort.ProcessExecutionResult result = processExecutionPort.execute(
                new ProcessExecutionPort.ProcessExecutionRequest(
                        runId,
                        command,
                        workingDirectory,
                        timeoutSeconds,
                        stdoutPath,
                        stderrPath,
                        true
                )
        );
        return new CommandResult(
                result.exitCode(),
                result.stdout(),
                result.stderr(),
                result.stdoutPath(),
                result.stderrPath()
        );
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
        if (command.publishMode() == null || command.publishMode().isBlank()) {
            throw new ValidationException("publish_mode is required");
        }
        if (command.aiSessionMode() == null || command.aiSessionMode().isBlank()) {
            throw new ValidationException("ai_session_mode is required");
        }
    }

    private AiSessionMode resolveAiSessionMode(String aiSessionModeRaw) {
        try {
            return AiSessionMode.fromApiValue(aiSessionModeRaw == null ? null : aiSessionModeRaw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(ex.getMessage());
        }
    }

    private RunEntity getRunEntity(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Run not found: " + runId));
    }

    private Project resolveProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
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

    private void writeContextManifest(
            Path runScopeRoot,
            String featureRequest,
            List<String> contextFileManifest
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("context_file_manifest", contextFileManifest);
        manifest.put("feature_request", featureRequest);
        writeFile(runScopeRoot.resolve("context").resolve("context-manifest.json"), toJson(manifest).getBytes(StandardCharsets.UTF_8));
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

    private void deleteDirectoryContents(Path directory) {
        if (directory == null || !workspacePort.exists(directory)) {
            return;
        }
        try {
            for (Path path : workspacePort.listDescendantsReverse(directory)) {
                workspacePort.deleteIfExists(path);
            }
        } catch (IOException ex) {
            throw new ValidationException("Failed to clean directory: " + directory);
        }
    }

    private void ensureRuntimeMetadataIgnored(Path projectRoot) {
        Path excludePath = projectRoot.resolve(".git").resolve("info").resolve("exclude");
        if (!workspacePort.exists(excludePath)) {
            return;
        }
        try {
            String content = workspacePort.readString(excludePath, StandardCharsets.UTF_8);
            List<String> managedPatterns = new ArrayList<>(List.of(
                    ".hgsdlc",
                    ".hgsdlc/",
                    ".hgsdlc/*",
                    "!.hgsdlc/nodes/",
                    "!.hgsdlc/nodes/**",
                    ".hgsdlc/nodes/**/*.log",
                    ".hgsdlc/nodes/**/prompt.md",
                    ".hgsdlc/nodes/**/step-summary.json",
                    ".qwen/",
                    ".gigacode/",
                    ".claude/",
                    ".cursor/"
            ));
            String runtimeAgent = normalize(trimToNull(settingsService.getRuntimeCodingAgent()));
            if (runtimeAgent != null && runtimeAgent.matches("[a-z0-9_-]+")) {
                managedPatterns.add("." + runtimeAgent + "/");
            }

            List<String> preservedLines = content.lines()
                    .filter((line) -> !managedPatterns.contains(line.trim()))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

            List<String> desiredManagedPatterns = new ArrayList<>();
            desiredManagedPatterns.add(".hgsdlc/*");
            desiredManagedPatterns.add("!.hgsdlc/nodes/");
            desiredManagedPatterns.add("!.hgsdlc/nodes/**");
            desiredManagedPatterns.add(".hgsdlc/nodes/**/*.log");
            desiredManagedPatterns.add(".hgsdlc/nodes/**/prompt.md");
            desiredManagedPatterns.add(".hgsdlc/nodes/**/step-summary.json");
            if (runtimeAgent != null && runtimeAgent.matches("[a-z0-9_-]+")) {
                desiredManagedPatterns.add("." + runtimeAgent + "/");
            }

            StringBuilder updated = new StringBuilder();
            for (String line : preservedLines) {
                updated.append(line).append('\n');
            }
            if (updated.length() > 0) {
                updated.append('\n');
            }
            for (String pattern : desiredManagedPatterns) {
                updated.append(pattern).append('\n');
            }
            workspacePort.writeString(
                    excludePath,
                    updated.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE
            );
        } catch (IOException ex) {
            log.warn("Failed to update git exclude for runtime metadata at {}", excludePath, ex);
        }
    }

    private boolean resolveSkipGates(User user) {
        if (user == null) {
            return false;
        }
        var effectiveRoles = user.getEffectiveRoles();
        return effectiveRoles.size() == 1 && effectiveRoles.contains(Role.PRODUCT_OWNER);
    }

    private String normalizeBranch(String branch) {
        String normalized = trimToNull(branch);
        if (normalized == null) {
            return "main";
        }
        return normalized;
    }

    private RunPublishMode resolvePublishMode(String rawPublishMode) {
        String normalized = normalize(trimToNull(rawPublishMode));
        if ("branch".equals(normalized)) {
            return RunPublishMode.BRANCH;
        }
        if ("local".equals(normalized)) {
            return RunPublishMode.LOCAL;
        }
        if ("pr".equals(normalized)) {
            return RunPublishMode.PR;
        }
        throw new ValidationException("publish_mode must be branch or pr");
    }

    private PrCommitStrategy resolvePrCommitStrategy(RunPublishMode publishMode, String rawStrategy) {
        if (publishMode != RunPublishMode.PR) {
            return null;
        }
        String normalized = normalize(trimToNull(rawStrategy));
        if (normalized == null || normalized.isBlank() || "squash".equals(normalized)) {
            return PrCommitStrategy.SQUASH;
        }
        throw new ValidationException("pr_commit_strategy must be squash");
    }

    private String resolveWorkBranch(UUID runId, String rawWorkBranch, String rawTargetBranch) {
        String targetBranch = normalizeBranch(rawTargetBranch);
        String resolved = trimToNull(rawWorkBranch);
        if (resolved == null) {
            resolved = "run/" + runId;
        }
        if (resolved.equals(targetBranch)) {
            throw new ValidationException("work_branch must differ from target_branch");
        }
        return resolved;
    }

    private String resolveRunCodingAgent(RunEntity run) {
        String flowJson = trimToNull(run.getFlowSnapshotJson());
        if (flowJson != null) {
            try {
                Map<?, ?> flow = objectMapper.readValue(flowJson, Map.class);
                Object value = flow.get("coding_agent");
                if (value instanceof String codingAgentValue) {
                    String normalized = normalize(codingAgentValue);
                    if (!normalized.isBlank()) {
                        return normalized;
                    }
                }
            } catch (Exception ignored) {
                // Fallback to current runtime setting.
            }
        }
        String runtimeCodingAgent = normalize(settingsService.getRuntimeCodingAgent());
        if (!runtimeCodingAgent.isBlank()) {
            return runtimeCodingAgent;
        }
        return "qwen";
    }

    private FlowModel parseFlowSnapshot(RunEntity run) {
        String json = run.getFlowSnapshotJson();
        if (json == null || json.isBlank()) {
            throw new ConflictException("Run has no flow snapshot");
        }
        try {
            return objectMapper.readValue(json, FlowModel.class);
        } catch (JsonProcessingException ex) {
            throw new ConflictException("Failed to parse flow snapshot: " + ex.getMessage());
        }
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

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record CommandResult(
            int exitCode,
            String stdout,
            String stderr,
            String stdoutPath,
            String stderrPath
    ) {}

    private static class NodeFailureException extends RuntimeException {
        private final String errorCode;
        private final Map<String, Object> details;

        private NodeFailureException(String errorCode, String message) {
            this(errorCode, message, Map.of());
        }

        private NodeFailureException(String errorCode, String message, Map<String, Object> details) {
            super(message);
            this.errorCode = errorCode;
            this.details = details == null ? Map.of() : details;
        }
    }
}

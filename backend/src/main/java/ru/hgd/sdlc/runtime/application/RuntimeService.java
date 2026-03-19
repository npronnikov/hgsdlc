package ru.hgd.sdlc.runtime.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.RandomAccessFile;
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
import java.util.function.Function;
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
    private static final int NODE_LOG_CHUNK_SIZE = 256 * 1024;

    private final RunRepository runRepository;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final GateInstanceRepository gateInstanceRepository;
    private final ArtifactVersionRepository artifactVersionRepository;
    private final AuditEventRepository auditEventRepository;
    private final ProjectRepository projectRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final RuntimeStepTxService runtimeStepTxService;
    private final ExecutionTraceBuilder executionTraceBuilder;
    private final FlowYamlParser flowYamlParser;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final Map<String, CodingAgentStrategy> codingAgentStrategiesByAgentId;
    private final int maxTickIterations;

    public RuntimeService(
            RunRepository runRepository,
            NodeExecutionRepository nodeExecutionRepository,
            GateInstanceRepository gateInstanceRepository,
            ArtifactVersionRepository artifactVersionRepository,
            AuditEventRepository auditEventRepository,
            ProjectRepository projectRepository,
            FlowVersionRepository flowVersionRepository,
            RuntimeStepTxService runtimeStepTxService,
            ExecutionTraceBuilder executionTraceBuilder,
            FlowYamlParser flowYamlParser,
            ObjectMapper objectMapper,
            SettingsService settingsService,
            List<CodingAgentStrategy> codingAgentStrategies,
            @Value("${runtime.max-tick-iterations:128}") Integer maxTickIterations
    ) {
        this.runRepository = runRepository;
        this.nodeExecutionRepository = nodeExecutionRepository;
        this.gateInstanceRepository = gateInstanceRepository;
        this.artifactVersionRepository = artifactVersionRepository;
        this.auditEventRepository = auditEventRepository;
        this.projectRepository = projectRepository;
        this.flowVersionRepository = flowVersionRepository;
        this.runtimeStepTxService = runtimeStepTxService;
        this.executionTraceBuilder = executionTraceBuilder;
        this.flowYamlParser = flowYamlParser;
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.codingAgentStrategiesByAgentId = (codingAgentStrategies == null ? List.<CodingAgentStrategy>of() : codingAgentStrategies)
                .stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        (strategy) -> normalize(strategy.codingAgent()),
                        Function.identity(),
                        (left, right) -> left
                ));
        this.maxTickIterations = maxTickIterations == null ? DEFAULT_MAX_TICK_ITERATIONS : maxTickIterations;
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
    public ArtifactContentResult getArtifactContent(UUID runId, UUID artifactVersionId) {
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

    public record ArtifactContentResult(
            ArtifactVersionEntity artifact,
            String content
    ) {}

    public NodeLogResult getNodeLog(UUID runId, UUID nodeExecutionId, long offset) {
        RunEntity run = getRunEntity(runId);
        NodeExecutionEntity execution = nodeExecutionRepository.findByIdAndRunId(nodeExecutionId, runId)
                .orElseThrow(() -> new NotFoundException("Node execution not found: " + nodeExecutionId));
        String dirName = execution.getNodeId() + "-attempt-" + execution.getAttemptNo();
        Path nodeDir = resolveRunScopeRoot(resolveRunWorkspaceRoot(run)).resolve("nodes").resolve(dirName);
        String logFileName = "ai".equals(execution.getNodeKind()) ? "agent.stdout.log" : "command.stdout.log";
        Path logPath = nodeDir.resolve(logFileName);
        boolean running = execution.getStatus() == NodeExecutionStatus.RUNNING
                || execution.getStatus() == NodeExecutionStatus.CREATED;
        if (!Files.exists(logPath)) {
            return new NodeLogResult("", offset, running);
        }
        if ("ai".equals(execution.getNodeKind())) {
            return readAiNodeLog(logPath, offset, running);
        }
        return readRawNodeLog(logPath, offset, running);
    }

    private NodeLogResult readRawNodeLog(Path logPath, long offset, boolean running) {
        try (RandomAccessFile raf = new RandomAccessFile(logPath.toFile(), "r")) {
            long fileLength = raf.length();
            long normalizedOffset = Math.max(offset, 0L);
            if (normalizedOffset >= fileLength) {
                return new NodeLogResult("", fileLength, running);
            }
            raf.seek(normalizedOffset);
            int chunkSize = (int) Math.min(fileLength - normalizedOffset, NODE_LOG_CHUNK_SIZE);
            byte[] buffer = new byte[chunkSize];
            int read = raf.read(buffer);
            if (read <= 0) {
                return new NodeLogResult("", normalizedOffset, running);
            }
            String content = new String(buffer, 0, read, StandardCharsets.UTF_8);
            return new NodeLogResult(content, normalizedOffset + read, running);
        } catch (IOException ex) {
            log.warn("Failed to read node log at {}: {}", logPath, ex.getMessage());
            return new NodeLogResult("", offset, running);
        }
    }

    private NodeLogResult readAiNodeLog(Path logPath, long offset, boolean running) {
        try (RandomAccessFile raf = new RandomAccessFile(logPath.toFile(), "r")) {
            long fileLength = raf.length();
            long normalizedOffset = Math.max(offset, 0L);
            if (normalizedOffset >= fileLength) {
                return new NodeLogResult("", fileLength, running);
            }
            raf.seek(normalizedOffset);
            int chunkSize = (int) Math.min(fileLength - normalizedOffset, NODE_LOG_CHUNK_SIZE);
            byte[] buffer = new byte[chunkSize];
            int read = raf.read(buffer);
            if (read <= 0) {
                return new NodeLogResult("", normalizedOffset, running);
            }
            int consumedBytes = consumedLineDelimitedBytes(buffer, read, normalizedOffset + read >= fileLength);
            if (consumedBytes <= 0) {
                return new NodeLogResult("", normalizedOffset, running);
            }
            String rawChunk = new String(buffer, 0, consumedBytes, StandardCharsets.UTF_8);
            String content = parseAiStreamChunk(rawChunk);
            return new NodeLogResult(content, normalizedOffset + consumedBytes, running);
        } catch (IOException ex) {
            log.warn("Failed to read AI node log at {}: {}", logPath, ex.getMessage());
            return new NodeLogResult("", offset, running);
        }
    }

    private int consumedLineDelimitedBytes(byte[] buffer, int readBytes, boolean eof) {
        for (int i = readBytes - 1; i >= 0; i--) {
            if (buffer[i] == '\n') {
                return i + 1;
            }
        }
        return eof ? readBytes : 0;
    }

    private String parseAiStreamChunk(String rawChunk) {
        StringBuilder output = new StringBuilder();
        int start = 0;
        while (start < rawChunk.length()) {
            int newline = rawChunk.indexOf('\n', start);
            int end = newline >= 0 ? newline : rawChunk.length();
            String line = rawChunk.substring(start, end);
            appendAiStreamLine(output, line);
            if (newline < 0) {
                break;
            }
            start = newline + 1;
        }
        return output.toString();
    }

    private void appendAiStreamLine(StringBuilder output, String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            String extracted = extractAiStreamText(root);
            if (extracted != null && !extracted.isEmpty()) {
                output.append(extracted);
            }
        } catch (JsonProcessingException ex) {
            output.append(line).append('\n');
        }
    }

    private String extractAiStreamText(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "";
        }
        String type = root.path("type").asText("");
        if ("stream_event".equals(type)) {
            return extractStreamEventText(root.path("event"));
        }
        if ("result".equals(type)) {
            return extractFirstText(root.path("result"), root.path("output_text"), root.path("text"));
        }
        if ("error".equals(type)) {
            return extractFirstText(root.path("message"), root.path("error"));
        }
        return "";
    }

    private String extractStreamEventText(JsonNode event) {
        if (event == null || event.isMissingNode() || event.isNull()) {
            return "";
        }
        String eventType = event.path("type").asText("");
        return switch (eventType) {
            case "content_block_delta", "message_delta" -> extractDeltaText(event.path("delta"));
            case "content_block_start" -> extractContentBlockText(event.path("content_block"));
            default -> "";
        };
    }

    private String extractDeltaText(JsonNode delta) {
        if (delta == null || delta.isMissingNode() || delta.isNull()) {
            return "";
        }
        String deltaType = delta.path("type").asText("");
        if ("thinking_delta".equals(deltaType)) {
            return extractFirstText(delta.path("thinking"));
        }
        if ("text_delta".equals(deltaType)) {
            return extractFirstText(delta.path("text"));
        }
        return extractFirstText(
                delta.path("text"),
                delta.path("thinking"),
                delta.path("content"),
                delta.path("output_text"),
                delta.path("result")
        );
    }

    private String extractContentBlockText(JsonNode contentBlock) {
        if (contentBlock == null || contentBlock.isMissingNode() || contentBlock.isNull()) {
            return "";
        }
        String blockType = contentBlock.path("type").asText("");
        if ("thinking".equals(blockType)) {
            return extractFirstText(contentBlock.path("thinking"));
        }
        if ("text".equals(blockType)) {
            return extractFirstText(contentBlock.path("text"));
        }
        return extractFirstText(
                contentBlock.path("text"),
                contentBlock.path("thinking"),
                contentBlock.path("content")
        );
    }

    private String extractFirstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            String value = extractText(node);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String extractText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : node) {
                sb.append(extractText(item));
            }
            return sb.toString();
        }
        if (node.isObject()) {
            return extractFirstText(node.path("text"), node.path("content"), node.path("message"), node.path("output_text"));
        }
        return "";
    }

    public record NodeLogResult(
            String content,
            long offset,
            boolean running
    ) {}

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
    public AuditQueryResult queryAuditEvents(
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
                org.springframework.data.domain.PageRequest.of(0, effectiveLimit + 1)
        );
        boolean hasMore = events.size() > effectiveLimit;
        List<AuditEventEntity> page = hasMore ? events.subList(0, effectiveLimit) : events;
        Long nextCursor = hasMore ? page.get(page.size() - 1).getSequenceNo() : null;
        return new AuditQueryResult(page, nextCursor, hasMore);
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

    public record AuditQueryResult(
            List<AuditEventEntity> events,
            Long nextCursor,
            boolean hasMore
    ) {}

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
        boolean keepChanges = shouldKeepChangesOnRework(node, command.mode());
        if (!keepChanges) {
            rollbackWorkspaceToCheckpoint(run, transitionTarget, gate.getId());
        }
        String reworkInstruction = trimToNull(command.instruction());
        GateInstanceEntity updatedGate = runtimeStepTxService.requestRework(
                run.getId(),
                gate.getId(),
                gate.getNodeExecutionId(),
                resolveActorId(user),
                trimToNull(command.mode()),
                trimToNull(command.comment()),
                reworkInstruction,
                command.reviewedArtifactVersionIds()
        );
        if (transitionTarget.equals(flowModel.getStartNodeId())) {
            runtimeStepTxService.appendFeatureRequestClarification(run.getId(), gate.getId(), reworkInstruction);
            runtimeStepTxService.replacePendingReworkInstruction(run.getId(), gate.getId(), null);
        } else {
            runtimeStepTxService.replacePendingReworkInstruction(run.getId(), gate.getId(), reworkInstruction);
        }
        runtimeStepTxService.markNodeExecutionSucceeded(run.getId(), gate.getNodeExecutionId(), gate.getNodeId());
        applyTransition(run, null, updatedGate, transitionTarget, "on_rework");
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
        if (isRunCancelled(run.getId())) {
            return false;
        }
        FlowModel flowModel = parseFlowSnapshot(run);
        NodeModel node = requireNode(flowModel, run.getCurrentNodeId());
        String nodeKind = normalizeNodeKind(node);

        NodeExecutionEntity execution = createNodeExecution(run, node, nodeKind);

        try {
            createCheckpointBeforeExecution(run, node, execution, nodeKind);
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
            if (isRunCancelled(run.getId())) {
                return false;
            }
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
        String pendingReworkInstruction = trimToNull(run.getPendingReworkInstruction());
        List<Map<String, Object>> resolvedContext = resolveExecutionContext(run, node);
        CodingAgentStrategy strategy = resolveCodingAgentStrategy(flowModel);
        AgentInvocationContext agentInvocationContext;
        try {
            agentInvocationContext = strategy.materializeWorkspace(new CodingAgentStrategy.MaterializationRequest(
                    run,
                    flowModel,
                    node,
                    execution,
                    resolvedContext,
                    resolveProjectRoot(run),
                    resolveNodeExecutionRoot(run, execution)
            ));
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
                    run.getId(),
                    agentInvocationContext.command(),
                    agentInvocationContext.workingDirectory(),
                    settingsService.getAiTimeoutSeconds(),
                    agentInvocationContext.stdoutPath(),
                    agentInvocationContext.stderrPath()
            );
        } catch (ProcessCancelledException ex) {
            runtimeStepTxService.markNodeExecutionCancelled(run.getId(), execution.getId(), ex.getMessage());
            return false;
        } catch (IOException ex) {
            throw new NodeFailureException("AGENT_EXECUTION_FAILED", ex.getMessage(), false);
        }
        if (isRunCancelled(run.getId())) {
            runtimeStepTxService.markNodeExecutionCancelled(run.getId(), execution.getId(), "Run cancelled by user");
            return false;
        }
        if (agentResult.exitCode() != 0) {
            throw new NodeFailureException(
                    "AGENT_EXECUTION_FAILED",
                    capitalize(strategy.codingAgent()) + " execution failed with exit code " + agentResult.exitCode(),
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
                strategy.codingAgent(),
                executionTraceBuilder.agentInvocationFinishedPayload(
                        node,
                        agentInvocationContext.promptPackage().promptChecksum(),
                        agentResult
                )
        );

        runtimeStepTxService.markNodeExecutionSucceeded(run.getId(), execution.getId(), node.getId());
        if (pendingReworkInstruction != null) {
            runtimeStepTxService.consumePendingReworkInstruction(run.getId(), execution.getId(), node.getId());
        }
        applyTransition(run, execution, null, node.getOnSuccess(), "on_success");
        return true;
    }

    private CodingAgentStrategy resolveCodingAgentStrategy(FlowModel flowModel) {
        String flowCodingAgent = normalize(trimToNull(flowModel.getCodingAgent()));
        String runtimeCodingAgent = normalize(trimToNull(settingsService.getRuntimeCodingAgent()));
        if (!runtimeCodingAgent.equals(flowCodingAgent)) {
            throw new NodeFailureException(
                    "CODING_AGENT_MISMATCH",
                    "Flow coding_agent does not match runtime settings: flow=" + flowCodingAgent + ", runtime=" + runtimeCodingAgent,
                    false
            );
        }
        CodingAgentStrategy strategy = codingAgentStrategiesByAgentId.get(runtimeCodingAgent);
        if (strategy == null) {
            throw new NodeFailureException(
                    "UNSUPPORTED_CODING_AGENT",
                    "Runtime coding_agent is not implemented: " + runtimeCodingAgent,
                    false
            );
        }
        return strategy;
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
        String payloadJson = buildGatePayload(run, node);
        runtimeStepTxService.openGate(
                run.getId(),
                execution.getId(),
                node.getId(),
                gateKind,
                initialStatus,
                firstAllowedRole(node.getAllowedRoles()),
                payloadJson
        );
        return false;
    }

    private String buildGatePayload(RunEntity run, NodeModel node) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if ("human_approval".equals(normalizeNodeKind(node))) {
            payload.put("rework_discard_available", isReworkDiscardAvailable(run, node));
        }
        List<Map<String, Object>> contextArtifacts = resolveGateContextArtifacts(run, node);
        if (!contextArtifacts.isEmpty()) {
            payload.put("execution_context_artifacts", contextArtifacts);
        }
        String inputArtifactKey = trimToNull(node.getInputArtifact());
        if (inputArtifactKey != null) {
            ArtifactVersionEntity inputArtifact = artifactVersionRepository
                    .findByRunIdAndArtifactKey(run.getId(), inputArtifactKey)
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
        String userInstructions = trimToNull(node.getUserInstructions());
        if (userInstructions != null) {
            payload.put("user_instructions", userInstructions);
        }
        if (payload.isEmpty()) {
            return null;
        }
        return toJson(payload);
    }

    private boolean isReworkDiscardAvailable(RunEntity run, NodeModel approvalNode) {
        if (approvalNode == null || approvalNode.getOnRework() == null) {
            return false;
        }
        String targetNodeId = trimToNull(approvalNode.getOnRework().getNextNode());
        if (targetNodeId == null) {
            return false;
        }
        FlowModel flowModel = parseFlowSnapshot(run);
        NodeModel targetNode = flowModel.getNodes().stream()
                .filter((candidate) -> targetNodeId.equals(candidate.getId()))
                .findFirst()
                .orElse(null);
        if (targetNode == null) {
            return false;
        }
        String targetKind = normalizeNodeKind(targetNode);
        if (!"ai".equals(targetKind) && !"command".equals(targetKind)) {
            return false;
        }
        return Boolean.TRUE.equals(targetNode.getCheckpointBeforeRun());
    }

    private List<Map<String, Object>> resolveGateContextArtifacts(RunEntity run, NodeModel node) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<ExecutionContextEntry> entries = node.getExecutionContext() == null ? List.of() : node.getExecutionContext();
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
            if (path == null || !Files.exists(path)) {
                if (Boolean.TRUE.equals(entry.getRequired())) {
                    log.warn("Required artifact_ref not found for gate context: node_id={}, path={}, run_id={}",
                            entry.getNodeId(), fileName, run.getId());
                }
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

    private String readFileContent(Path path) {
        if (path == null || !Files.exists(path) || Files.isDirectory(path)) {
            return null;
        }
        try {
            long size = Files.size(path);
            if (size > 512_000) {
                return null;
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Failed to read artifact content: path={}", path, ex);
            return null;
        }
    }

    private static final Set<String> FAILURE_TRANSITIONS = Set.of("on_failure");

    private boolean completeTerminalNode(RunEntity run, NodeModel node, NodeExecutionEntity execution) {
        RunStatus terminalStatus = resolveTerminalStatus(run.getId());
        runtimeStepTxService.completeRun(run.getId(), execution.getId(), node.getId(), terminalStatus);
        return false;
    }

    private RunStatus resolveTerminalStatus(UUID runId) {
        return auditEventRepository.findFirstByRunIdAndEventTypeOrderBySequenceNoDesc(runId, "transition_applied")
                .map(event -> {
                    try {
                        Map<String, Object> payload = objectMapper.readValue(event.getPayloadJson(), new TypeReference<>() {});
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
                    List.of("zsh", "-lc", instruction),
                    workingDirectory,
                    settingsService.getAiTimeoutSeconds(),
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
        } catch (ProcessCancelledException ex) {
            throw new RunCancelledException(ex.getMessage());
        } catch (IOException ex) {
            throw new NodeFailureException("COMMAND_EXECUTION_FAILED", ex.getMessage(), false);
        }
    }

    private static final int CANCEL_POLL_INTERVAL_SECONDS = 5;

    private CommandResult runProcess(
            UUID runId,
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
        try {
            long deadlineMs = System.currentTimeMillis() + (long) timeoutSeconds * 1000;
            while (process.isAlive()) {
                long remainingMs = deadlineMs - System.currentTimeMillis();
                if (remainingMs <= 0) {
                    process.destroyForcibly();
                    throw new IOException("Process timeout after " + timeoutSeconds + "s");
                }
                long pollMs = Math.min(CANCEL_POLL_INTERVAL_SECONDS * 1000L, remainingMs);
                boolean finished = process.waitFor(pollMs, TimeUnit.MILLISECONDS);
                if (finished) {
                    break;
                }
                if (runId != null) {
                    RunStatus currentStatus = runRepository.findById(runId)
                            .map(RunEntity::getStatus)
                            .orElse(null);
                    if (currentStatus == RunStatus.CANCELLED) {
                        log.info("Run {} cancelled, destroying process", runId);
                        process.destroyForcibly();
                        throw new ProcessCancelledException("Run cancelled by user");
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Process interrupted", ex);
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
            Path path = resolveProducedArtifactPath(run, execution, requirement.getScope(), requirement.getPath());
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
                case "artifact_ref" -> {
                    Path path = resolveArtifactRefPath(run, entry.getNodeId(), entry.getScope(), entry.getPath());
                    if (path == null || !Files.exists(path)) {
                        if (required) {
                            throw new NodeFailureException(
                                    "MISSING_EXECUTION_CONTEXT",
                                    "Missing required artifact_ref: node_id=" + entry.getNodeId() + ", path=" + entry.getPath(),
                                    false
                            );
                        }
                        continue;
                    }
                    resolved.add(Map.of(
                            "type", "artifact_ref",
                            "artifact_key", artifactKeyForPath(entry.getPath()),
                            "path", path.toString(),
                            "source_node_id", entry.getNodeId() == null ? "" : entry.getNodeId()
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

    private void createCheckpointBeforeExecution(
            RunEntity run,
            NodeModel node,
            NodeExecutionEntity execution,
            String nodeKind
    ) {
        if (!Boolean.TRUE.equals(node.getCheckpointBeforeRun())) {
            return;
        }
        if (!Set.of("ai", "command").contains(nodeKind)) {
            return;
        }

        Path operationRoot = resolveNodeExecutionRoot(run, execution);
        Path workingDirectory = resolveProjectScopeRoot(resolveRunWorkspaceRoot(run));
        String checkpointCommitMessage = "checkpoint:" + node.getId() + ":" + execution.getId();

        CommandResult addResult = runGitCommand(
                run,
                execution,
                "checkpoint_add",
                List.of("git", "add", "-A"),
                workingDirectory,
                operationRoot
        );
        ensureGitCommandSuccess("checkpoint add", addResult);

        CommandResult commitResult = runGitCommand(
                run,
                execution,
                "checkpoint_commit",
                List.of("git", "commit", "--allow-empty", "-m", checkpointCommitMessage),
                workingDirectory,
                operationRoot
        );
        ensureGitCommandSuccess("checkpoint commit", commitResult);

        CommandResult shaResult = runGitCommand(
                run,
                execution,
                "checkpoint_rev_parse",
                List.of("git", "rev-parse", "HEAD"),
                workingDirectory,
                operationRoot
        );
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
                            "stderr", truncate(shaResult.stderr(), 4000)
                    )
            );
            throw new NodeFailureException(
                    "CHECKPOINT_CREATION_FAILED",
                    "Failed to resolve checkpoint commit SHA",
                    false
            );
        }

        runtimeStepTxService.markNodeExecutionCheckpoint(run.getId(), execution.getId(), checkpointSha, Instant.now());
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
                        "stderr", truncate(commitResult.stderr(), 4000)
                )
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
        try {
            resetResult = runProcess(
                    run.getId(),
                    List.of("git", "reset", "--hard", checkpointCommitSha),
                    workingDirectory,
                    settingsService.getAiTimeoutSeconds(),
                    operationRoot.resolve("checkpoint_reset.stdout.log"),
                    operationRoot.resolve("checkpoint_reset.stderr.log")
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
                        "stdout", truncate(resetResult.stdout(), 4000),
                        "stderr", truncate(resetResult.stderr(), 4000)
                )
        );
    }

    private CommandResult runGitCommand(
            RunEntity run,
            NodeExecutionEntity execution,
            String operationName,
            List<String> command,
            Path workingDirectory,
            Path operationRoot
    ) {
        Path stdoutPath = operationRoot.resolve(operationName + ".stdout.log");
        Path stderrPath = operationRoot.resolve(operationName + ".stderr.log");
        try {
            return runProcess(
                    run.getId(),
                    command,
                    workingDirectory,
                    settingsService.getAiTimeoutSeconds(),
                    stdoutPath,
                    stderrPath
            );
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
                            "error", ex.getMessage()
                    )
            );
            throw new NodeFailureException(
                    "CHECKPOINT_CREATION_FAILED",
                    "Checkpoint command failed: " + operationName,
                    false
            );
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
                        "stderr", truncate(result.stderr(), 4000)
                )
        );
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

    private String resolveReworkTarget(NodeModel node) {
        if (node.getOnRework() != null && node.getOnRework().getNextNode() != null && !node.getOnRework().getNextNode().isBlank()) {
            return node.getOnRework().getNextNode();
        }
        throw new ValidationException("on_rework target is missing");
    }

    private boolean shouldKeepChangesOnRework(NodeModel node, String modeRaw) {
        String mode = normalize(trimToNull(modeRaw));
        if ("keep".equals(mode)) {
            return true;
        }
        if ("discard".equals(mode)) {
            return false;
        }
        return node.getOnRework() != null && Boolean.TRUE.equals(node.getOnRework().getKeepChanges());
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
            String featureRequest,
            List<String> contextFileManifest
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();
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

    private Path resolveProducedArtifactPath(RunEntity run, NodeExecutionEntity execution, String scopeRaw, String fileName) {
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
        String dirName = sourceExecution.getNodeId() + "-attempt-" + sourceExecution.getAttemptNo();
        Path nodeDir = resolveRunScopeRoot(resolveRunWorkspaceRoot(run)).resolve("nodes").resolve(dirName);
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

    private String capitalize(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "Agent";
        }
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
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
            String instruction,
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

    private static class ProcessCancelledException extends IOException {
        private ProcessCancelledException(String message) {
            super(message);
        }
    }

    private static class RunCancelledException extends RuntimeException {
        private RunCancelledException(String message) {
            super(message);
        }
    }
}

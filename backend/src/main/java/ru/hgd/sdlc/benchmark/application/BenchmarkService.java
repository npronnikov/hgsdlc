package ru.hgd.sdlc.benchmark.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.benchmark.domain.ArtifactType;
import ru.hgd.sdlc.benchmark.domain.BenchmarkCaseEntity;
import ru.hgd.sdlc.benchmark.domain.BenchmarkRunEntity;
import ru.hgd.sdlc.benchmark.domain.BenchmarkStatus;
import ru.hgd.sdlc.benchmark.domain.BenchmarkVerdict;
import ru.hgd.sdlc.benchmark.infrastructure.BenchmarkCaseRepository;
import ru.hgd.sdlc.benchmark.infrastructure.BenchmarkRunRepository;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.project.domain.Project;
import ru.hgd.sdlc.project.infrastructure.ProjectRepository;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;
import ru.hgd.sdlc.runtime.application.RuntimeStepTxService;
import ru.hgd.sdlc.runtime.application.command.ApproveGateCommand;
import ru.hgd.sdlc.runtime.application.service.GateDecisionService;
import ru.hgd.sdlc.runtime.application.service.RunLifecycleService;
import ru.hgd.sdlc.runtime.application.service.RuntimeCommandService;
import ru.hgd.sdlc.runtime.domain.AiSessionMode;
import ru.hgd.sdlc.runtime.domain.GateStatus;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.domain.RunPublishMode;
import ru.hgd.sdlc.runtime.domain.RunPublishStatus;
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.infrastructure.GateInstanceRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;
import ru.hgd.sdlc.settings.application.SettingsService;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

@Service
public class BenchmarkService {
    private static final Logger log = LoggerFactory.getLogger(BenchmarkService.class);
    private static final List<GateStatus> OPEN_GATE_STATUSES = List.of(
            GateStatus.AWAITING_INPUT,
            GateStatus.AWAITING_DECISION,
            GateStatus.FAILED_VALIDATION
    );
    private static final User BENCHMARK_SYSTEM_USER = User.builder()
            .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .username("benchmark-system")
            .displayName("Benchmark System")
            .role(Role.ADMIN)
            .roles(Set.of(Role.ADMIN))
            .passwordHash("")
            .enabled(true)
            .createdAt(Instant.EPOCH)
            .build();

    private final BenchmarkCaseRepository caseRepository;
    private final BenchmarkRunRepository runRepository;
    private final ProjectRepository projectRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final RunRepository runtimeRunRepository;
    private final GateInstanceRepository gateInstanceRepository;
    private final RuntimeStepTxService runtimeStepTxService;
    private final RunLifecycleService runLifecycleService;
    private final GateDecisionService gateDecisionService;
    private final RuntimeCommandService runtimeCommandService;
    private final BenchmarkDiffService diffService;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;

    public BenchmarkService(
            BenchmarkCaseRepository caseRepository,
            BenchmarkRunRepository runRepository,
            ProjectRepository projectRepository,
            SkillVersionRepository skillVersionRepository,
            RuleVersionRepository ruleVersionRepository,
            RunRepository runtimeRunRepository,
            GateInstanceRepository gateInstanceRepository,
            RuntimeStepTxService runtimeStepTxService,
            RunLifecycleService runLifecycleService,
            GateDecisionService gateDecisionService,
            RuntimeCommandService runtimeCommandService,
            BenchmarkDiffService diffService,
            SettingsService settingsService,
            ObjectMapper objectMapper,
            TaskExecutor taskExecutor
    ) {
        this.caseRepository = caseRepository;
        this.runRepository = runRepository;
        this.projectRepository = projectRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.ruleVersionRepository = ruleVersionRepository;
        this.runtimeRunRepository = runtimeRunRepository;
        this.gateInstanceRepository = gateInstanceRepository;
        this.runtimeStepTxService = runtimeStepTxService;
        this.runLifecycleService = runLifecycleService;
        this.gateDecisionService = gateDecisionService;
        this.runtimeCommandService = runtimeCommandService;
        this.diffService = diffService;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    @Transactional
    public BenchmarkCaseEntity createCase(String instruction, UUID projectId, String name,
                                          String artifactType, String artifactId,
                                          String artifactTypeB, String artifactIdB,
                                          String createdBy) {
        if (instruction == null || instruction.isBlank()) {
            throw new ValidationException("instruction is required");
        }
        if (projectId == null) {
            throw new ValidationException("project_id is required");
        }
        projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        String normalizedArtifactType = normalizeOptional(artifactType);
        String normalizedArtifactId = normalizeOptional(artifactId);
        if (normalizedArtifactType == null ^ normalizedArtifactId == null) {
            throw new ValidationException("artifact_type and artifact_id must be provided together");
        }
        String normalizedArtifactTypeB = normalizeOptional(artifactTypeB);
        String normalizedArtifactIdB = normalizeOptional(artifactIdB);
        if (normalizedArtifactTypeB == null && normalizedArtifactIdB != null) {
            normalizedArtifactTypeB = normalizedArtifactType;
        }
        if (normalizedArtifactTypeB == null ^ normalizedArtifactIdB == null) {
            throw new ValidationException("artifact_type_b and artifact_id_b must be provided together");
        }
        if (normalizedArtifactIdB != null && normalizedArtifactId == null) {
            throw new ValidationException("artifact A must be configured when artifact B is provided");
        }

        ArtifactType parsedTypeA = null;
        ArtifactType parsedTypeB = null;
        if (normalizedArtifactType != null) {
            parsedTypeA = parseArtifactType(normalizedArtifactType);
            // Validate configured artifact up-front so Start Run does not fail later.
            resolveArtifactCanonicalName(parsedTypeA, normalizedArtifactId, null);
            normalizedArtifactType = parsedTypeA.name();
        }
        if (normalizedArtifactTypeB != null) {
            parsedTypeB = parseArtifactType(normalizedArtifactTypeB);
            if (parsedTypeA != null && parsedTypeA != parsedTypeB) {
                throw new ValidationException("artifact B type must match artifact A type");
            }
            resolveArtifactCanonicalName(parsedTypeB, normalizedArtifactIdB, null);
            normalizedArtifactTypeB = parsedTypeB.name();
        }

        BenchmarkCaseEntity entity = BenchmarkCaseEntity.builder()
                .id(UUID.randomUUID())
                .instruction(instruction.trim())
                .projectId(projectId)
                .name(name != null ? name.trim() : null)
                .artifactType(normalizedArtifactType)
                .artifactId(normalizedArtifactId)
                .artifactBType(normalizedArtifactTypeB)
                .artifactBId(normalizedArtifactIdB)
                .createdBy(createdBy)
                .createdAt(Instant.now())
                .build();
        return caseRepository.save(entity);
    }

    @Transactional
    public void deleteCase(UUID caseId) {
        if (!caseRepository.existsById(caseId)) {
            throw new NotFoundException("BenchmarkCase not found: " + caseId);
        }
        runRepository.deleteByCaseId(caseId);
        caseRepository.deleteById(caseId);
    }

    @Transactional
    public BenchmarkRunEntity startRun(
            UUID caseId,
            String instructionOverride,
            UUID projectIdOverride,
            String codingAgent,
            String createdBy
    ) {
        BenchmarkCaseEntity benchCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("BenchmarkCase not found: " + caseId));

        if (benchCase.getArtifactType() == null || benchCase.getArtifactId() == null) {
            throw new ValidationException("Benchmark case has no artifact configured");
        }

        String artifactRefA = normalizeOptional(benchCase.getArtifactId());
        if (artifactRefA == null) {
            throw new ValidationException("Benchmark case has invalid artifact_id");
        }

        ArtifactType typeA = parseArtifactType(benchCase.getArtifactType());
        String canonicalNameA = resolveArtifactCanonicalName(typeA, artifactRefA, null);
        log.debug("startRun: resolved canonicalNameA='{}' for artifactRefA='{}'", canonicalNameA, artifactRefA);

        String artifactRefB = normalizeOptional(benchCase.getArtifactBId());
        String artifactTypeBRaw = normalizeOptional(benchCase.getArtifactBType());
        ArtifactType typeB = null;
        String canonicalNameB = null;
        if (artifactRefB != null || artifactTypeBRaw != null) {
            if (artifactRefB == null) {
                throw new ValidationException("Benchmark case has invalid artifact_id_b");
            }
            typeB = artifactTypeBRaw != null ? parseArtifactType(artifactTypeBRaw) : typeA;
            if (typeA != typeB) {
                throw new ValidationException("artifact B type must match artifact A type");
            }
            canonicalNameB = resolveArtifactCanonicalName(typeB, artifactRefB, null);
            log.debug("startRun: resolved canonicalNameB='{}' for artifactRefB='{}'", canonicalNameB, artifactRefB);
        }

        UUID effectiveProjectId = projectIdOverride != null ? projectIdOverride : benchCase.getProjectId();
        Project project = projectRepository.findById(effectiveProjectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + effectiveProjectId));

        String effectiveCodingAgent = resolveEffectiveCodingAgent(codingAgent, typeA, artifactRefA, typeB, artifactRefB);
        String instruction = (instructionOverride != null && !instructionOverride.isBlank())
                ? instructionOverride.trim() : benchCase.getInstruction();

        UUID benchmarkRunId = UUID.randomUUID();

        // Build flow snapshots
        FlowModel flowA = buildFlow(benchmarkRunId, "a", instruction, typeA, canonicalNameA, effectiveCodingAgent);
        FlowModel flowB = buildFlow(benchmarkRunId, "b", instruction, typeB, canonicalNameB, effectiveCodingAgent);

        String flowAJson = toJson(flowA);
        String flowBJson = toJson(flowB);

        // Create the two runtime runs
        UUID runAId = UUID.randomUUID();
        UUID runBId = UUID.randomUUID();
        Instant now = Instant.now();
        String workspaceRoot = settingsService.getWorkspaceRoot();

        Path runARootPath = Path.of(workspaceRoot).resolve(runAId.toString()).toAbsolutePath().normalize();
        Path runBRootPath = Path.of(workspaceRoot).resolve(runBId.toString()).toAbsolutePath().normalize();

        String targetBranch = project.getDefaultBranch() != null ? project.getDefaultBranch() : "main";

        runtimeStepTxService.createRun(
                runAId,
                project.getId(),
                targetBranch,
                "benchmark:" + benchmarkRunId + ":a",
                flowAJson,
                AiSessionMode.ISOLATED_ATTEMPT_SESSIONS,
                null,
                RunPublishMode.LOCAL,
                "benchmark-" + benchmarkRunId + "-a",
                null,
                RunPublishStatus.SKIPPED,
                RunPublishStatus.SKIPPED,
                RunPublishStatus.SKIPPED,
                "ai_node",
                instruction,
                "[]",
                runARootPath.toString(),
                createdBy,
                now,
                false
        );

        runtimeStepTxService.createRun(
                runBId,
                project.getId(),
                targetBranch,
                "benchmark:" + benchmarkRunId + ":b",
                flowBJson,
                AiSessionMode.ISOLATED_ATTEMPT_SESSIONS,
                null,
                RunPublishMode.LOCAL,
                "benchmark-" + benchmarkRunId + "-b",
                null,
                RunPublishStatus.SKIPPED,
                RunPublishStatus.SKIPPED,
                RunPublishStatus.SKIPPED,
                "ai_node",
                instruction,
                "[]",
                runBRootPath.toString(),
                createdBy,
                now,
                false
        );

        // Persist BenchmarkRun
        BenchmarkRunEntity benchmarkRun = BenchmarkRunEntity.builder()
                .id(benchmarkRunId)
                .caseId(caseId)
                .artifactType(typeA)
                .artifactId(artifactRefA)
                .artifactVersionId(null)
                .artifactBType(typeB)
                .artifactBId(artifactRefB)
                .artifactBVersionId(null)
                .codingAgent(effectiveCodingAgent)
                .runAId(runAId)
                .runBId(runBId)
                .status(BenchmarkStatus.RUNNING)
                .createdBy(createdBy)
                .createdAt(now)
                .build();
        runRepository.save(benchmarkRun);

        // Start both runs async
        taskExecutor.execute(() -> {
            try {
                runLifecycleService.startRun(runAId);
            } catch (Exception ex) {
                log.error("Failed to start benchmark run A id={}", runAId, ex);
            }
        });
        taskExecutor.execute(() -> {
            try {
                runLifecycleService.startRun(runBId);
            } catch (Exception ex) {
                log.error("Failed to start benchmark run B id={}", runBId, ex);
            }
        });

        return benchmarkRun;
    }

    @Transactional
    public BenchmarkRunEntity getBenchmarkRun(UUID benchmarkRunId) {
        BenchmarkRunEntity br = runRepository.findById(benchmarkRunId)
                .orElseThrow(() -> new NotFoundException("BenchmarkRun not found: " + benchmarkRunId));

        if (br.getStatus() == BenchmarkStatus.RUNNING) {
            checkAndTransitionToWaitingComparison(br);
        } else if ((br.getStatus() == BenchmarkStatus.WAITING_COMPARISON || br.getStatus() == BenchmarkStatus.COMPLETED)
                && isBlank(br.getDiffA()) && isBlank(br.getDiffB())) {
            recomputeDiffs(br);
        }
        return br;
    }

    @Transactional
    public BenchmarkRunEntity submitVerdict(
            UUID benchmarkRunId,
            String verdictStr,
            String actorUsername,
            String reviewComment,
            String lineCommentsJson,
            String decisionScoresJson
    ) {
        BenchmarkRunEntity br = runRepository.findById(benchmarkRunId)
                .orElseThrow(() -> new NotFoundException("BenchmarkRun not found: " + benchmarkRunId));

        if (br.getStatus() != BenchmarkStatus.WAITING_COMPARISON) {
            throw new ValidationException("BenchmarkRun is not in WAITING_COMPARISON state");
        }

        BenchmarkVerdict verdict = parseVerdict(verdictStr);

        // Approve both gates
        approveRunGate(br.getRunAId(), actorUsername);
        approveRunGate(br.getRunBId(), actorUsername);

        br.setHumanVerdict(verdict);
        br.setReviewComment(normalizeOptional(reviewComment));
        br.setLineCommentsJson(normalizeOptional(lineCommentsJson));
        br.setDecisionScoresJson(normalizeOptional(decisionScoresJson));
        br.setStatus(BenchmarkStatus.COMPLETED);
        br.setCompletedAt(Instant.now());
        return runRepository.save(br);
    }

    public List<BenchmarkCaseEntity> listCases() {
        return caseRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public List<BenchmarkRunEntity> listRunsByCase(UUID caseId) {
        List<BenchmarkRunEntity> runs = runRepository.findByCaseIdOrderByCreatedAtDesc(caseId);
        for (BenchmarkRunEntity run : runs) {
            if (run.getStatus() == BenchmarkStatus.RUNNING) {
                checkAndTransitionToWaitingComparison(run);
            } else if ((run.getStatus() == BenchmarkStatus.WAITING_COMPARISON
                    || run.getStatus() == BenchmarkStatus.COMPLETED)
                    && isBlank(run.getDiffA()) && isBlank(run.getDiffB())) {
                recomputeDiffs(run);
            }
        }
        return runs;
    }

    @Transactional
    public List<BenchmarkRunEntity> listAllRuns() {
        List<BenchmarkRunEntity> runs = runRepository.findAllByOrderByCreatedAtDesc();
        for (BenchmarkRunEntity run : runs) {
            if (run.getStatus() == BenchmarkStatus.RUNNING) {
                checkAndTransitionToWaitingComparison(run);
            } else if ((run.getStatus() == BenchmarkStatus.WAITING_COMPARISON
                    || run.getStatus() == BenchmarkStatus.COMPLETED)
                    && isBlank(run.getDiffA()) && isBlank(run.getDiffB())) {
                recomputeDiffs(run);
            }
        }
        return runs;
    }

    @Transactional(readOnly = true)
    public List<BenchmarkDiffService.FileComparisonEntry> getRunFileComparison(UUID benchmarkRunId) {
        BenchmarkRunEntity br = runRepository.findById(benchmarkRunId)
                .orElseThrow(() -> new NotFoundException("BenchmarkRun not found: " + benchmarkRunId));
        if (br.getRunAId() == null || br.getRunBId() == null) {
            return List.of();
        }
        RunEntity runA = runtimeRunRepository.findById(br.getRunAId())
                .orElseThrow(() -> new NotFoundException("Run A not found: " + br.getRunAId()));
        RunEntity runB = runtimeRunRepository.findById(br.getRunBId())
                .orElseThrow(() -> new NotFoundException("Run B not found: " + br.getRunBId()));
        return diffService.compareRunFiles(runA, runB);
    }

    // --- internal ---

    private void checkAndTransitionToWaitingComparison(BenchmarkRunEntity br) {
        if (br.getRunAId() == null || br.getRunBId() == null) {
            return;
        }
        RunEntity runA = runtimeRunRepository.findById(br.getRunAId()).orElse(null);
        RunEntity runB = runtimeRunRepository.findById(br.getRunBId()).orElse(null);
        if (runA == null || runB == null) {
            return;
        }

        boolean aFailed = runA.getStatus() == RunStatus.FAILED || runA.getStatus() == RunStatus.CANCELLED;
        boolean bFailed = runB.getStatus() == RunStatus.FAILED || runB.getStatus() == RunStatus.CANCELLED;
        if (aFailed || bFailed) {
            br.setStatus(BenchmarkStatus.FAILED);
            runRepository.save(br);
            return;
        }

        boolean aWaiting = runA.getStatus() == RunStatus.WAITING_GATE;
        boolean bWaiting = runB.getStatus() == RunStatus.WAITING_GATE;

        if (aWaiting && bWaiting) {
            applyDiffs(br, runA, runB);
            br.setStatus(BenchmarkStatus.WAITING_COMPARISON);
            runRepository.save(br);
        }
    }

    private void recomputeDiffs(BenchmarkRunEntity br) {
        if (br.getRunAId() == null || br.getRunBId() == null) {
            return;
        }
        RunEntity runA = runtimeRunRepository.findById(br.getRunAId()).orElse(null);
        RunEntity runB = runtimeRunRepository.findById(br.getRunBId()).orElse(null);
        if (runA == null || runB == null) {
            return;
        }
        applyDiffs(br, runA, runB);
        runRepository.save(br);
    }

    private void applyDiffs(BenchmarkRunEntity br, RunEntity runA, RunEntity runB) {
        String diffA = diffService.computeGitDiff(runA);
        String diffB = diffService.computeGitDiff(runB);
        String diffOfDiffs = diffService.computeDiffOfDiffs(diffA, diffB);
        br.setDiffA(diffA);
        br.setDiffB(diffB);
        br.setDiffOfDiffs(diffOfDiffs);
    }

    private void approveRunGate(UUID runId, String actorUsername) {
        if (runId == null) {
            return;
        }
        gateInstanceRepository
                .findFirstByRunIdAndStatusInOrderByOpenedAtDesc(runId, OPEN_GATE_STATUSES)
                .ifPresent(gate -> {
                    try {
                        gateDecisionService.approveGate(
                                gate.getId(),
                                new ApproveGateCommand(gate.getResourceVersion(), "Benchmark verdict submitted", List.of()),
                                BENCHMARK_SYSTEM_USER
                        );
                        runtimeCommandService.dispatchProcessRunStep(runId);
                    } catch (Exception ex) {
                        log.warn("Could not approve gate {} for run {}: {}", gate.getId(), runId, ex.getMessage());
                    }
                });
    }

    private FlowModel buildFlow(
            UUID benchmarkRunId,
            String arm,
            String instruction,
            ArtifactType artifactType,
            String canonicalName,
            String codingAgent
    ) {
        List<String> skillRefs = new ArrayList<>();
        List<String> ruleRefs = new ArrayList<>();

        if (canonicalName != null) {
            if (artifactType == ArtifactType.SKILL) {
                skillRefs.add(canonicalName);
            } else if (artifactType == ArtifactType.RULE) {
                ruleRefs.add(canonicalName);
            }
        }

        NodeModel aiNode = NodeModel.builder()
                .id("ai_node")
                .type("ai")
                .instruction(instruction)
                .skillRefs(skillRefs.isEmpty() ? null : skillRefs)
                .onSuccess("approval_node")
                .build();

        NodeModel approvalNode = NodeModel.builder()
                .id("approval_node")
                .type("human_approval")
                .allowedRoles(List.of("TECH_APPROVER", "ADMIN"))
                .userInstructions("Benchmark comparison: review changes")
                .onApprove("end_node")
                .onRework(NodeModel.OnRework.builder().nextNode("ai_node").build())
                .build();

        NodeModel terminalNode = NodeModel.builder()
                .id("end_node")
                .type("terminal")
                .build();

        return FlowModel.builder()
                .id("benchmark-" + benchmarkRunId + "-" + arm)
                .version("1.0")
                .title("Benchmark Flow " + arm.toUpperCase())
                .startNodeId("ai_node")
                .codingAgent(codingAgent)
                .ruleRefs(ruleRefs.isEmpty() ? List.of() : ruleRefs)
                .nodes(List.of(aiNode, approvalNode, terminalNode))
                .build();
    }

    private String resolveArtifactCanonicalName(ArtifactType type, String artifactRef, UUID artifactVersionId) {
        String normalizedRef = normalizeOptional(artifactRef);
        log.debug("resolveArtifactCanonicalName: type={} artifactRef='{}' artifactVersionId={}", type, normalizedRef, artifactVersionId);
        if (normalizedRef == null) {
            throw new ValidationException("artifact_id is required");
        }
        if (type == ArtifactType.SKILL) {
            SkillVersion skill;
            if (artifactVersionId != null) {
                skill = skillVersionRepository.findById(artifactVersionId)
                        .orElseThrow(() -> new NotFoundException("SkillVersion not found: " + artifactVersionId));
            } else {
                skill = resolveSkillByReference(normalizedRef);
            }
            return skill.getCanonicalName();
        } else {
            RuleVersion rule;
            if (artifactVersionId != null) {
                rule = ruleVersionRepository.findById(artifactVersionId)
                        .orElseThrow(() -> new NotFoundException("RuleVersion not found: " + artifactVersionId));
            } else {
                rule = resolveRuleByReference(normalizedRef);
            }
            return rule.getCanonicalName();
        }
    }

    private String resolveEffectiveCodingAgent(
            String requestedAgent,
            ArtifactType typeA,
            String artifactRefA,
            ArtifactType typeB,
            String artifactRefB
    ) {
        if (requestedAgent != null && !requestedAgent.isBlank()) {
            return requestedAgent.trim();
        }
        // Use a single coding agent for both benchmark arms.
        String agentA = resolveArtifactCodingAgent(typeA, artifactRefA);
        String agentB = resolveArtifactCodingAgent(typeB, artifactRefB);
        if (agentA != null && agentB != null && !agentA.equals(agentB)) {
            throw new ValidationException("artifact A and B must use the same coding_agent");
        }
        if (agentA != null) {
            return agentA;
        }
        if (agentB != null) {
            return agentB;
        }
        return "claude";
    }

    private String resolveArtifactCodingAgent(ArtifactType type, String artifactRef) {
        if (type == null || artifactRef == null) {
            return null;
        }
        if (type == ArtifactType.SKILL) {
            SkillVersion skill = resolveSkillByReference(artifactRef);
            return skill.getCodingAgent() == null ? null : skill.getCodingAgent().name().toLowerCase(Locale.ROOT);
        }
        RuleVersion rule = resolveRuleByReference(artifactRef);
        return rule.getCodingAgent() == null ? null : rule.getCodingAgent().name().toLowerCase(Locale.ROOT);
    }

    private SkillVersion resolveSkillByReference(String artifactRef) {
        var byId = skillVersionRepository.findFirstBySkillIdAndStatusOrderBySavedAtDesc(artifactRef, SkillStatus.PUBLISHED)
                .or(() -> skillVersionRepository.findFirstBySkillIdAndStatusOrderBySavedAtDesc(artifactRef, SkillStatus.DRAFT));
        if (byId.isPresent()) {
            return byId.get();
        }

        var byCanonical = skillVersionRepository.findFirstByCanonicalNameAndStatus(artifactRef, SkillStatus.PUBLISHED)
                .or(() -> skillVersionRepository.findFirstByCanonicalNameAndStatus(artifactRef, SkillStatus.DRAFT))
                .or(() -> skillVersionRepository.findFirstByCanonicalName(artifactRef));
        if (byCanonical.isPresent()) {
            return byCanonical.get();
        }

        throw new NotFoundException("Skill not found in catalog: " + artifactRef);
    }

    private RuleVersion resolveRuleByReference(String artifactRef) {
        var byId = ruleVersionRepository.findFirstByRuleIdAndStatusOrderBySavedAtDesc(artifactRef, RuleStatus.PUBLISHED)
                .or(() -> ruleVersionRepository.findFirstByRuleIdAndStatusOrderBySavedAtDesc(artifactRef, RuleStatus.DRAFT));
        if (byId.isPresent()) {
            return byId.get();
        }

        var byCanonical = ruleVersionRepository.findFirstByCanonicalNameAndStatus(artifactRef, RuleStatus.PUBLISHED)
                .or(() -> ruleVersionRepository.findFirstByCanonicalNameAndStatus(artifactRef, RuleStatus.DRAFT))
                .or(() -> ruleVersionRepository.findFirstByCanonicalName(artifactRef));
        if (byCanonical.isPresent()) {
            return byCanonical.get();
        }

        throw new NotFoundException("Rule not found in catalog: " + artifactRef);
    }

    private ArtifactType parseArtifactType(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("artifact_type is required (SKILL or RULE)");
        }
        try {
            return ArtifactType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid artifact_type: " + value + " (expected SKILL or RULE)");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BenchmarkVerdict parseVerdict(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("verdict is required");
        }
        try {
            return BenchmarkVerdict.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Invalid verdict: " + value);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize to JSON", ex);
        }
    }
}

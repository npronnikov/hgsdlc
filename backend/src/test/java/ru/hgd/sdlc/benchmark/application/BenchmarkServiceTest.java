package ru.hgd.sdlc.benchmark.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.task.TaskExecutor;
import ru.hgd.sdlc.benchmark.domain.BenchmarkCaseEntity;
import ru.hgd.sdlc.benchmark.domain.BenchmarkRunEntity;
import ru.hgd.sdlc.benchmark.infrastructure.BenchmarkCaseRepository;
import ru.hgd.sdlc.benchmark.infrastructure.BenchmarkRunRepository;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.project.domain.Project;
import ru.hgd.sdlc.project.infrastructure.ProjectRepository;
import ru.hgd.sdlc.rule.domain.RuleProvider;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;
import ru.hgd.sdlc.runtime.application.RuntimeStepTxService;
import ru.hgd.sdlc.runtime.application.service.GateDecisionService;
import ru.hgd.sdlc.runtime.application.service.RunLifecycleService;
import ru.hgd.sdlc.runtime.application.service.RuntimeCommandService;
import ru.hgd.sdlc.runtime.infrastructure.GateInstanceRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;
import ru.hgd.sdlc.settings.application.SettingsService;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

class BenchmarkServiceTest {

    @Test
    void startRunInfersGigacodeAgentFromRuleArtifacts() {
        BenchmarkCaseRepository caseRepository = Mockito.mock(BenchmarkCaseRepository.class);
        BenchmarkRunRepository benchmarkRunRepository = Mockito.mock(BenchmarkRunRepository.class);
        ProjectRepository projectRepository = Mockito.mock(ProjectRepository.class);
        SkillVersionRepository skillVersionRepository = Mockito.mock(SkillVersionRepository.class);
        RuleVersionRepository ruleVersionRepository = Mockito.mock(RuleVersionRepository.class);
        RunRepository runtimeRunRepository = Mockito.mock(RunRepository.class);
        GateInstanceRepository gateInstanceRepository = Mockito.mock(GateInstanceRepository.class);
        RuntimeStepTxService runtimeStepTxService = Mockito.mock(RuntimeStepTxService.class);
        RunLifecycleService runLifecycleService = Mockito.mock(RunLifecycleService.class);
        GateDecisionService gateDecisionService = Mockito.mock(GateDecisionService.class);
        RuntimeCommandService runtimeCommandService = Mockito.mock(RuntimeCommandService.class);
        BenchmarkDiffService diffService = Mockito.mock(BenchmarkDiffService.class);
        SettingsService settingsService = Mockito.mock(SettingsService.class);
        TaskExecutor taskExecutor = Runnable::run;
        BenchmarkService service = new BenchmarkService(
                caseRepository,
                benchmarkRunRepository,
                projectRepository,
                skillVersionRepository,
                ruleVersionRepository,
                runtimeRunRepository,
                gateInstanceRepository,
                runtimeStepTxService,
                runLifecycleService,
                gateDecisionService,
                runtimeCommandService,
                diffService,
                settingsService,
                new ObjectMapper(),
                taskExecutor
        );

        UUID caseId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        BenchmarkCaseEntity benchCase = BenchmarkCaseEntity.builder()
                .id(caseId)
                .name("rule benchmark")
                .instruction("Add guard clause")
                .projectId(projectId)
                .artifactType("RULE")
                .artifactId("rule-a")
                .artifactBType(null)
                .artifactBId(null)
                .createdBy("tester")
                .createdAt(Instant.now())
                .build();
        Project project = Project.builder()
                .id(projectId)
                .name("demo")
                .repoUrl("https://example.com/repo.git")
                .defaultBranch("main")
                .build();
        RuleVersion ruleA = RuleVersion.builder()
                .id(UUID.randomUUID())
                .ruleId("rule-a")
                .canonicalName("rule-a@1.0")
                .codingAgent(RuleProvider.GIGACODE)
                .status(RuleStatus.PUBLISHED)
                .build();

        Mockito.when(caseRepository.findById(caseId)).thenReturn(Optional.of(benchCase));
        Mockito.when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        Mockito.when(settingsService.getWorkspaceRoot()).thenReturn("/tmp/hgd-bench-tests");
        Mockito.when(ruleVersionRepository.findFirstByRuleIdAndStatusOrderBySavedAtDesc(
                Mockito.eq("rule-a"),
                Mockito.any(RuleStatus.class)
        )).thenReturn(Optional.of(ruleA));
        Mockito.when(benchmarkRunRepository.save(Mockito.any(BenchmarkRunEntity.class)))
                .thenAnswer((inv) -> inv.getArgument(0));

        BenchmarkRunEntity run = service.startRun(caseId, null, null, null, "tester");

        Assertions.assertEquals("gigacode", run.getCodingAgent());
    }

    @Test
    void startRunRejectsDifferentAgentsForRuleArtifacts() {
        BenchmarkCaseRepository caseRepository = Mockito.mock(BenchmarkCaseRepository.class);
        BenchmarkRunRepository benchmarkRunRepository = Mockito.mock(BenchmarkRunRepository.class);
        ProjectRepository projectRepository = Mockito.mock(ProjectRepository.class);
        SkillVersionRepository skillVersionRepository = Mockito.mock(SkillVersionRepository.class);
        RuleVersionRepository ruleVersionRepository = Mockito.mock(RuleVersionRepository.class);
        RunRepository runtimeRunRepository = Mockito.mock(RunRepository.class);
        GateInstanceRepository gateInstanceRepository = Mockito.mock(GateInstanceRepository.class);
        RuntimeStepTxService runtimeStepTxService = Mockito.mock(RuntimeStepTxService.class);
        RunLifecycleService runLifecycleService = Mockito.mock(RunLifecycleService.class);
        GateDecisionService gateDecisionService = Mockito.mock(GateDecisionService.class);
        RuntimeCommandService runtimeCommandService = Mockito.mock(RuntimeCommandService.class);
        BenchmarkDiffService diffService = Mockito.mock(BenchmarkDiffService.class);
        SettingsService settingsService = Mockito.mock(SettingsService.class);
        TaskExecutor taskExecutor = Runnable::run;
        BenchmarkService service = new BenchmarkService(
                caseRepository,
                benchmarkRunRepository,
                projectRepository,
                skillVersionRepository,
                ruleVersionRepository,
                runtimeRunRepository,
                gateInstanceRepository,
                runtimeStepTxService,
                runLifecycleService,
                gateDecisionService,
                runtimeCommandService,
                diffService,
                settingsService,
                new ObjectMapper(),
                taskExecutor
        );

        UUID caseId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        BenchmarkCaseEntity benchCase = BenchmarkCaseEntity.builder()
                .id(caseId)
                .instruction("Refactor logging")
                .projectId(projectId)
                .artifactType("RULE")
                .artifactId("rule-a")
                .artifactBType("RULE")
                .artifactBId("rule-b")
                .createdBy("tester")
                .createdAt(Instant.now())
                .build();
        Project project = Project.builder()
                .id(projectId)
                .name("demo")
                .repoUrl("https://example.com/repo.git")
                .defaultBranch("main")
                .build();
        RuleVersion ruleA = RuleVersion.builder()
                .id(UUID.randomUUID())
                .ruleId("rule-a")
                .canonicalName("rule-a@1.0")
                .codingAgent(RuleProvider.GIGACODE)
                .status(RuleStatus.PUBLISHED)
                .build();
        RuleVersion ruleB = RuleVersion.builder()
                .id(UUID.randomUUID())
                .ruleId("rule-b")
                .canonicalName("rule-b@1.0")
                .codingAgent(RuleProvider.CLAUDE)
                .status(RuleStatus.PUBLISHED)
                .build();

        Mockito.when(caseRepository.findById(caseId)).thenReturn(Optional.of(benchCase));
        Mockito.when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        Mockito.when(ruleVersionRepository.findFirstByRuleIdAndStatusOrderBySavedAtDesc(
                Mockito.eq("rule-a"),
                Mockito.any(RuleStatus.class)
        )).thenReturn(Optional.of(ruleA));
        Mockito.when(ruleVersionRepository.findFirstByRuleIdAndStatusOrderBySavedAtDesc(
                Mockito.eq("rule-b"),
                Mockito.any(RuleStatus.class)
        )).thenReturn(Optional.of(ruleB));

        ValidationException ex = Assertions.assertThrows(
                ValidationException.class,
                () -> service.startRun(caseId, null, null, null, "tester")
        );
        Assertions.assertEquals("artifact A and B must use the same coding_agent", ex.getMessage());
    }
}

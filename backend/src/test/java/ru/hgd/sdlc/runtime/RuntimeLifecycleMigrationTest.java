package ru.hgd.sdlc.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.project.domain.Project;
import ru.hgd.sdlc.runtime.application.RuntimeIntegrationTestConfig;
import ru.hgd.sdlc.runtime.application.RuntimeStepTxService;
import ru.hgd.sdlc.runtime.application.command.CreateRunCommand;
import ru.hgd.sdlc.runtime.application.service.RuntimeCommandService;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.infrastructure.AuditEventRepository;
import ru.hgd.sdlc.runtime.support.RuntimeIntegrationTestBase;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RuntimeIntegrationTestConfig.class)
class RuntimeLifecycleMigrationTest extends RuntimeIntegrationTestBase {
    @Autowired
    private RuntimeCommandService runtimeCommandService;

    @Autowired
    private RuntimeStepTxService runtimeStepTxService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private String token;
    private Project project;
    private String terminalFlowCanonicalName;
    private String humanInputFlowCanonicalName;

    @BeforeEach
    void setUp() throws Exception {
        resetRuntimeData();
        var remoteRepo = initGitRemoteRepo();
        String repoUrl = remoteRepo.toUri().toString();
        configureRuntime(tempDir.resolve("workspace"), repoUrl);
        token = loginAsTestUser();
        project = createProject(repoUrl);
        terminalFlowCanonicalName = createPublishedFlow(
                "lifecycle-terminal-flow",
                terminalOnlyFlowYaml("lifecycle-terminal-flow"),
                "complete"
        ).getCanonicalName();
        humanInputFlowCanonicalName = createPublishedFlow(
                "lifecycle-human-input-flow",
                humanInputFlowYaml("lifecycle-human-input-flow"),
                "collect-input"
        ).getCanonicalName();
    }

    @Test
    void resumeRunStartsCreatedRunToCompletion() {
        RunEntity created = runtimeCommandService.createRun(
                new CreateRunCommand(
                        project.getId(),
                        "main",
                        terminalFlowCanonicalName,
                        "Resume lifecycle run",
                        "isolated_attempt_sessions",
                        "local",
                        null,
                        null
                ),
                null
        );
        assertEquals(RunStatus.CREATED, created.getStatus());

        runtimeCommandService.resumeRun(created.getId());
        RunEntity completed = waitForRunStatus(created.getId(), Duration.ofSeconds(10), RunStatus.COMPLETED);
        assertEquals(RunStatus.COMPLETED, completed.getStatus());
    }

    @Test
    void cancelRunCancelsCreatedRun() {
        RunEntity created = runtimeCommandService.createRun(
                new CreateRunCommand(
                        project.getId(),
                        "main",
                        terminalFlowCanonicalName,
                        "Cancel lifecycle run",
                        "isolated_attempt_sessions",
                        "local",
                        null,
                        null
                ),
                null
        );
        assertEquals(RunStatus.CREATED, created.getStatus());

        RunEntity cancelled = runtimeCommandService.cancelRun(created.getId(), null);
        assertEquals(RunStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void recoverActiveRunsProcessesRunningAndAuditsWaitingGateRuns() {
        RunEntity recoverableRunningRun = runtimeCommandService.createRun(
                new CreateRunCommand(
                        project.getId(),
                        "main",
                        terminalFlowCanonicalName,
                        "Recover running run",
                        "isolated_attempt_sessions",
                        "local",
                        null,
                        null
                ),
                null
        );
        runtimeStepTxService.markRunStarted(recoverableRunningRun.getId(), Instant.now());

        Project secondProject = createProject(project.getRepoUrl());
        RunEntity waitingGateRun = runtimeCommandService.createRun(
                new CreateRunCommand(
                        secondProject.getId(),
                        "main",
                        humanInputFlowCanonicalName,
                        "Recover waiting gate run",
                        "isolated_attempt_sessions",
                        "local",
                        null,
                        null
                ),
                null
        );
        runtimeCommandService.startRun(waitingGateRun.getId());
        waitForRunStatus(waitingGateRun.getId(), Duration.ofSeconds(10), RunStatus.WAITING_GATE);

        long runningRecoveredBefore = countRunRecoveredEvents(recoverableRunningRun.getId());
        long waitingRecoveredBefore = countRunRecoveredEvents(waitingGateRun.getId());

        runtimeCommandService.recoverActiveRuns();

        RunEntity recovered = waitForRunStatus(recoverableRunningRun.getId(), Duration.ofSeconds(10), RunStatus.COMPLETED);
        assertEquals(RunStatus.COMPLETED, recovered.getStatus());

        RunEntity waitingAfterRecovery = runRepository.findById(waitingGateRun.getId()).orElseThrow();
        assertEquals(RunStatus.WAITING_GATE, waitingAfterRecovery.getStatus());
        assertEquals(runningRecoveredBefore + 1, countRunRecoveredEvents(recoverableRunningRun.getId()));
        assertEquals(waitingRecoveredBefore + 1, countRunRecoveredEvents(waitingGateRun.getId()));
    }

    @Test
    void createRunIsIdempotentByIdempotencyKey() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = objectMapper.writeValueAsString(Map.of(
                "project_id", project.getId(),
                "target_branch", "main",
                "flow_canonical_name", terminalFlowCanonicalName,
                "feature_request", "Idempotent lifecycle run",
                "ai_session_mode", "isolated_attempt_sessions",
                "publish_mode", "local",
                "idempotency_key", idempotencyKey
        ));

        MvcResult firstResult = mockMvc.perform(post("/api/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult secondResult = mockMvc.perform(post("/api/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode first = parseJson(readBody(firstResult));
        JsonNode second = parseJson(readBody(secondResult));
        assertEquals(first.path("run_id").asText(), second.path("run_id").asText());
        assertEquals(1, runRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).size());
    }

    @Test
    void createRunRejectsSecondActiveRunForSameProjectAndBranch() {
        runtimeCommandService.createRun(
                new CreateRunCommand(
                        project.getId(),
                        "main",
                        terminalFlowCanonicalName,
                        "First active run",
                        "isolated_attempt_sessions",
                        "local",
                        null,
                        null
                ),
                null
        );

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> runtimeCommandService.createRun(
                        new CreateRunCommand(
                                project.getId(),
                                "main",
                                terminalFlowCanonicalName,
                                "Second active run",
                                "isolated_attempt_sessions",
                                "local",
                                null,
                                null
                        ),
                        null
                )
        );
        assertTrue(ex.getMessage().contains("Active run already exists"));
    }

    private long countRunRecoveredEvents(UUID runId) {
        return auditEventRepository.findByRunIdOrderBySequenceNoAsc(runId).stream()
                .filter((event) -> "run_recovered".equals(event.getEventType()))
                .count();
    }

    private String terminalOnlyFlowYaml(String flowId) {
        return """
                id: %s
                version: "1.0"
                canonical_name: %s@1.0
                title: Lifecycle Terminal Flow
                description: Runtime lifecycle terminal flow
                status: published
                start_node_id: complete
                rule_refs: []
                fail_on_missing_declared_output: true
                fail_on_missing_expected_mutation: true

                nodes:
                  - id: complete
                    title: Terminal
                    type: terminal
                    execution_context: []
                    produced_artifacts: []
                    expected_mutations: []
                """.formatted(flowId, flowId);
    }

    private String humanInputFlowYaml(String flowId) {
        return """
                id: %s
                version: "1.0"
                canonical_name: %s@1.0
                title: Lifecycle Human Input Flow
                description: Runtime lifecycle waiting gate flow
                status: published
                start_node_id: draft-input
                rule_refs: []
                fail_on_missing_declared_output: true
                fail_on_missing_expected_mutation: true

                nodes:
                  - id: draft-input
                    title: Draft Input
                    type: command
                    checkpoint_before_run: false
                    execution_context: []
                    instruction: |
                      echo "input draft" > details.md
                    produced_artifacts:
                      - scope: run
                        path: details.md
                        required: true
                    expected_mutations: []
                    on_success: collect-input

                  - id: collect-input
                    title: Collect Input
                    type: human_input
                    execution_context:
                      - type: artifact_ref
                        node_id: draft-input
                        path: details.md
                        scope: run
                        required: true
                        modifiable: true
                        transfer_mode: by_ref
                    instruction: |
                      Provide details and submit.
                    produced_artifacts:
                      - scope: run
                        path: details.md
                        required: true
                    expected_mutations: []
                    on_submit: complete
                    allowed_roles:
                      - FLOW_CONFIGURATOR

                  - id: complete
                    title: Terminal
                    type: terminal
                    execution_context: []
                    produced_artifacts: []
                    expected_mutations: []
                """.formatted(flowId, flowId);
    }
}

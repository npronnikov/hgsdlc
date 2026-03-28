package ru.hgd.sdlc.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.UnprocessableEntityException;
import ru.hgd.sdlc.project.domain.Project;
import ru.hgd.sdlc.runtime.application.RuntimeIntegrationTestConfig;
import ru.hgd.sdlc.runtime.application.command.ApproveGateCommand;
import ru.hgd.sdlc.runtime.application.command.ReworkGateCommand;
import ru.hgd.sdlc.runtime.application.command.SubmitInputCommand;
import ru.hgd.sdlc.runtime.application.command.SubmittedArtifact;
import ru.hgd.sdlc.runtime.application.service.GateDecisionService;
import ru.hgd.sdlc.runtime.domain.GateInstanceEntity;
import ru.hgd.sdlc.runtime.domain.GateStatus;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.support.RuntimeIntegrationTestBase;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RuntimeIntegrationTestConfig.class)
class RuntimeGateDecisionServiceTest extends RuntimeIntegrationTestBase {
    private GateDecisionService gateDecisionService;
    private String token;
    private Project project;
    private User flowConfigurator;

    @org.springframework.beans.factory.annotation.Autowired
    void setGateDecisionService(GateDecisionService gateDecisionService) {
        this.gateDecisionService = gateDecisionService;
    }

    @BeforeEach
    void setUp() throws Exception {
        resetRuntimeData();
        configureRuntime(tempDir.resolve("workspace"));
        token = loginAsTestUser();
        project = createProject(initGitRemoteRepo().toUri().toString());
        flowConfigurator = User.builder()
                .id(UUID.randomUUID())
                .username("flow-configurator")
                .displayName("Flow Configurator")
                .role(Role.FLOW_CONFIGURATOR)
                .roles(Set.of(Role.FLOW_CONFIGURATOR))
                .passwordHash("test")
                .enabled(true)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void submitInputExpectedVersionMismatch() throws Exception {
        var flow = createPublishedFlow("gate-submit-version", humanInputFlowYaml("gate-submit-version"), "draft-questions");

        UUID runId = createRunViaApi(token, project.getId(), flow.getCanonicalName(), "Collect details");
        waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.WAITING_GATE);
        GateInstanceEntity gate = waitForGateStatus(runId, Duration.ofSeconds(10), GateStatus.AWAITING_INPUT);

        SubmitInputCommand command = new SubmitInputCommand(
                gate.getResourceVersion() + 1,
                List.of(new SubmittedArtifact(
                        "questions",
                        "questions.md",
                        "run",
                        encodeBase64("# Questions\n\nQ1: answer\n")
                )),
                "Answers"
        );

        Assertions.assertThrows(
                ConflictException.class,
                () -> gateDecisionService.submitInput(gate.getId(), command, flowConfigurator)
        );
    }

    @Test
    void approveExpectedVersionMismatch() throws Exception {
        var flow = createPublishedFlow("gate-approve-version", humanApprovalFlowYaml("gate-approve-version", "APPROVE_MISMATCH"), "implement-change");

        UUID runId = createRunViaApi(token, project.getId(), flow.getCanonicalName(), "Implement and review");
        waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.WAITING_GATE);
        GateInstanceEntity gate = waitForGateStatus(runId, Duration.ofSeconds(10), GateStatus.AWAITING_DECISION);

        ApproveGateCommand command = new ApproveGateCommand(
                gate.getResourceVersion() + 1,
                "Approve",
                List.of()
        );

        Assertions.assertThrows(
                ConflictException.class,
                () -> gateDecisionService.approveGate(gate.getId(), command, flowConfigurator)
        );
    }

    @Test
    void requestReworkExpectedVersionMismatch() throws Exception {
        var flow = createPublishedFlow("gate-rework-version", humanApprovalFlowYaml("gate-rework-version", "REWORK_MISMATCH"), "implement-change");

        UUID runId = createRunViaApi(token, project.getId(), flow.getCanonicalName(), "Implement and review");
        waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.WAITING_GATE);
        GateInstanceEntity gate = waitForGateStatus(runId, Duration.ofSeconds(10), GateStatus.AWAITING_DECISION);

        ReworkGateCommand command = new ReworkGateCommand(
                gate.getResourceVersion() + 1,
                "keep",
                "Needs update",
                "Adjust naming",
                List.of()
        );

        Assertions.assertThrows(
                ConflictException.class,
                () -> gateDecisionService.requestRework(gate.getId(), command, flowConfigurator)
        );
    }

    @Test
    void submitInputRequiredArtifactMustBeModified() throws Exception {
        var flow = createPublishedFlow("gate-required-input", humanInputFlowYaml("gate-required-input"), "draft-questions");

        UUID runId = createRunViaApi(token, project.getId(), flow.getCanonicalName(), "Collect details");
        RunEntity run = waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.WAITING_GATE);
        GateInstanceEntity gate = waitForGateStatus(runId, Duration.ofSeconds(10), GateStatus.AWAITING_INPUT);

        Path unchangedPath = Path.of(run.getWorkspaceRoot())
                .resolve(".hgsdlc")
                .resolve("nodes")
                .resolve(gate.getNodeId())
                .resolve("attempt-1")
                .resolve("questions.md");
        String unchangedContent = Files.readString(unchangedPath, StandardCharsets.UTF_8);

        SubmitInputCommand command = new SubmitInputCommand(
                gate.getResourceVersion(),
                List.of(new SubmittedArtifact(
                        "questions",
                        "questions.md",
                        "run",
                        encodeBase64(unchangedContent)
                )),
                "No edits"
        );

        Assertions.assertThrows(
                UnprocessableEntityException.class,
                () -> gateDecisionService.submitInput(gate.getId(), command, flowConfigurator)
        );

        GateInstanceEntity updatedGate = gateInstanceRepository.findById(gate.getId())
                .orElseThrow(() -> new IllegalStateException("Gate not found after validation"));
        Assertions.assertEquals(GateStatus.FAILED_VALIDATION, updatedGate.getStatus());
    }

    @Test
    void requestReworkDiscardResetsWorkspaceToCheckpoint() throws Exception {
        String marker = "DISCARD_MARKER_" + UUID.randomUUID().toString().substring(0, 8);
        var flow = createPublishedFlow(
                "gate-rework-discard",
                humanApprovalFlowYaml("gate-rework-discard", marker),
                "implement-change"
        );

        UUID runId = createRunViaApi(token, project.getId(), flow.getCanonicalName(), "Implement and review");
        RunEntity run = waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.WAITING_GATE);
        GateInstanceEntity gate = waitForGateStatus(runId, Duration.ofSeconds(10), GateStatus.AWAITING_DECISION);

        Path readmePath = Path.of(run.getWorkspaceRoot()).resolve("README.md");
        String beforeRework = Files.readString(readmePath, StandardCharsets.UTF_8);
        Assertions.assertTrue(beforeRework.contains(marker), "Marker should exist before discard rework");

        ReworkGateCommand command = new ReworkGateCommand(
                gate.getResourceVersion(),
                "discard",
                "Discard changes",
                "Rework required",
                List.of()
        );
        gateDecisionService.requestRework(gate.getId(), command, flowConfigurator);

        String afterRework = Files.readString(readmePath, StandardCharsets.UTF_8);
        Assertions.assertFalse(afterRework.contains(marker), "Discard rework must reset workspace to checkpoint");
    }

    @Test
    void requestReworkKeepPreservesWorkspaceChanges() throws Exception {
        String marker = "KEEP_MARKER_" + UUID.randomUUID().toString().substring(0, 8);
        var flow = createPublishedFlow(
                "gate-rework-keep",
                humanApprovalFlowYaml("gate-rework-keep", marker),
                "implement-change"
        );

        UUID runId = createRunViaApi(token, project.getId(), flow.getCanonicalName(), "Implement and review");
        RunEntity run = waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.WAITING_GATE);
        GateInstanceEntity gate = waitForGateStatus(runId, Duration.ofSeconds(10), GateStatus.AWAITING_DECISION);

        Path readmePath = Path.of(run.getWorkspaceRoot()).resolve("README.md");
        String beforeRework = Files.readString(readmePath, StandardCharsets.UTF_8);
        Assertions.assertTrue(beforeRework.contains(marker), "Marker should exist before keep rework");

        ReworkGateCommand command = new ReworkGateCommand(
                gate.getResourceVersion(),
                "keep",
                "Keep changes",
                "Rework required",
                List.of()
        );
        gateDecisionService.requestRework(gate.getId(), command, flowConfigurator);

        String afterRework = Files.readString(readmePath, StandardCharsets.UTF_8);
        Assertions.assertTrue(afterRework.contains(marker), "Keep rework must preserve workspace changes");
    }

    private String humanInputFlowYaml(String flowId) {
        return """
                id: %s
                version: "1.0"
                canonical_name: %s@1.0
                title: Human Input Flow
                description: Runtime human input flow
                status: published
                start_node_id: draft-questions
                rule_refs: []
                fail_on_missing_declared_output: true
                fail_on_missing_expected_mutation: true

                nodes:
                  - id: draft-questions
                    title: Draft Questions
                    type: command
                    execution_context: []
                    instruction: |
                      echo "prepare questions"
                    produced_artifacts:
                      - scope: run
                        path: questions.md
                        required: true
                        modifiable: true
                    expected_mutations: []
                    on_success: answer-questions

                  - id: answer-questions
                    title: Human answers
                    type: human_input
                    execution_context:
                      - type: artifact_ref
                        node_id: draft-questions
                        path: questions.md
                        scope: run
                        required: true
                        transfer_mode: by_ref
                    instruction: |
                      Fill questions and submit.
                    produced_artifacts:
                      - scope: run
                        path: questions.md
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

    private String humanApprovalFlowYaml(String flowId, String marker) {
        return """
                id: %s
                version: "1.0"
                canonical_name: %s@1.0
                title: Human Approval Flow
                description: Runtime human approval flow
                status: published
                start_node_id: implement-change
                rule_refs: []
                fail_on_missing_declared_output: true
                fail_on_missing_expected_mutation: true

                nodes:
                  - id: implement-change
                    title: Implement Change
                    type: command
                    checkpoint_before_run: true
                    execution_context: []
                    instruction: |
                      echo "%s" >> README.md
                    produced_artifacts: []
                    expected_mutations:
                      - scope: project
                        path: README.md
                        required: true
                    on_success: review-change

                  - id: review-change
                    title: Review
                    type: human_approval
                    execution_context: []
                    instruction: |
                      Review the changes.
                    produced_artifacts: []
                    expected_mutations: []
                    on_approve: complete
                    on_rework:
                      keep_changes: true
                      next_node: implement-change
                    allowed_roles:
                      - FLOW_CONFIGURATOR

                  - id: complete
                    title: Terminal
                    type: terminal
                    execution_context: []
                    produced_artifacts: []
                    expected_mutations: []
                """.formatted(flowId, flowId, marker);
    }
}

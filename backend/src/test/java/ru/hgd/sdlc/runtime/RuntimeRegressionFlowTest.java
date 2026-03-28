package ru.hgd.sdlc.runtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import ru.hgd.sdlc.project.domain.Project;
import ru.hgd.sdlc.runtime.application.RuntimeIntegrationTestConfig;
import ru.hgd.sdlc.runtime.domain.GateInstanceEntity;
import ru.hgd.sdlc.runtime.domain.GateStatus;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.support.RuntimeIntegrationTestBase;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RuntimeIntegrationTestConfig.class)
class RuntimeRegressionFlowTest extends RuntimeIntegrationTestBase {
    private String token;
    private Project project;

    @BeforeEach
    void setUp() throws Exception {
        resetRuntimeData();
        configureRuntime(tempDir.resolve("workspace"));
        token = loginAsTestUser();
        project = createProject(initGitRemoteRepo().toUri().toString());
    }

    @Test
    void aiSuccessFlowCompletes() throws Exception {
        var flow = createPublishedFlow("ai-success-flow", aiFlowYaml("ai-success-flow", "Write short summary", false), "ai-start");

        UUID runId = createRunViaApi(token, project.getId(), flow.getCanonicalName(), "Implement success scenario");
        RunEntity completed = waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.COMPLETED);

        Assertions.assertEquals(RunStatus.COMPLETED, completed.getStatus());

        MvcResult nodesResult = mockMvc.perform(get("/api/runs/{runId}/nodes", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode nodes = parseJson(readBody(nodesResult));
        Assertions.assertEquals(2, nodes.size());
        Assertions.assertEquals("ai", nodes.get(0).path("node_kind").asText());
        Assertions.assertEquals("succeeded", nodes.get(0).path("status").asText());
    }

    @Test
    void aiFailureFlowEndsWithFailedRun() throws Exception {
        var flow = createPublishedFlow("ai-failure-flow", aiFlowYaml("ai-failure-flow", "[[FAIL]] trigger", true), "ai-start");

        UUID runId = createRunViaApi(token, project.getId(), flow.getCanonicalName(), "Implement failure scenario");
        RunEntity failed = waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.FAILED);

        Assertions.assertEquals(RunStatus.FAILED, failed.getStatus());

        MvcResult auditResult = mockMvc.perform(get("/api/runs/{runId}/audit", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode events = parseJson(readBody(auditResult));
        boolean hasFailureTransition = false;
        for (JsonNode event : events) {
            if ("transition_applied".equals(event.path("event_type").asText())
                    && "on_failure".equals(event.path("payload").path("transition").asText())) {
                hasFailureTransition = true;
                break;
            }
        }
        Assertions.assertTrue(hasFailureTransition, "Expected on_failure transition in audit");
    }

    @Test
    void humanInputSubmitCompletesFlow() throws Exception {
        var flow = createPublishedFlow("human-input-flow", humanInputFlowYaml("human-input-flow"), "draft-questions");

        UUID runId = createRunViaApi(token, project.getId(), flow.getCanonicalName(), "Collect clarifications");
        RunEntity waiting = waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.WAITING_GATE);
        Assertions.assertEquals(RunStatus.WAITING_GATE, waiting.getStatus());

        GateInstanceEntity gate = waitForGateStatus(runId, Duration.ofSeconds(10), GateStatus.AWAITING_INPUT);

        String submitBody = objectMapper.writeValueAsString(Map.of(
                "expected_gate_version", gate.getResourceVersion(),
                "comment", "Answered",
                "artifacts", List.of(Map.of(
                        "artifact_key", "questions",
                        "path", "questions.md",
                        "scope", "run",
                        "content_base64", encodeBase64("# Questions\n\nQ1: answer\n")
                ))
        ));

        mockMvc.perform(post("/api/gates/{gateId}/submit-input", gate.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isOk());

        RunEntity completed = waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.COMPLETED);
        Assertions.assertEquals(RunStatus.COMPLETED, completed.getStatus());

        MvcResult auditResult = mockMvc.perform(get("/api/runs/{runId}/audit", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode events = parseJson(readBody(auditResult));
        boolean hasGateSubmit = false;
        for (JsonNode event : events) {
            if ("gate_input_submitted".equals(event.path("event_type").asText())) {
                hasGateSubmit = true;
                break;
            }
        }
        Assertions.assertTrue(hasGateSubmit, "Expected gate_input_submitted event");
    }

    @Test
    void humanApprovalReworkThenApproveCompletes() throws Exception {
        var flow = createPublishedFlow("rework-flow", humanApprovalFlowYaml("rework-flow"), "implement-change");

        UUID runId = createRunViaApi(token, project.getId(), flow.getCanonicalName(), "Implement and review");
        waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.WAITING_GATE);
        GateInstanceEntity firstGate = waitForGateStatus(runId, Duration.ofSeconds(10), GateStatus.AWAITING_DECISION);

        String reworkBody = objectMapper.writeValueAsString(Map.of(
                "expected_gate_version", firstGate.getResourceVersion(),
                "comment", "Need refinements",
                "instruction", "Please improve naming",
                "reviewed_artifact_version_ids", List.of()
        ));
        mockMvc.perform(post("/api/gates/{gateId}/request-rework", firstGate.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reworkBody))
                .andExpect(status().isOk());

        GateInstanceEntity secondGate = waitForDifferentGate(runId, firstGate.getId(), Duration.ofSeconds(10));
        Assertions.assertEquals(GateStatus.AWAITING_DECISION, secondGate.getStatus());

        String approveBody = objectMapper.writeValueAsString(Map.of(
                "expected_gate_version", secondGate.getResourceVersion(),
                "comment", "Approved",
                "reviewed_artifact_version_ids", List.of()
        ));
        mockMvc.perform(post("/api/gates/{gateId}/approve", secondGate.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveBody))
                .andExpect(status().isOk());

        waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.COMPLETED);

        List<GateInstanceEntity> gates = gateInstanceRepository.findByRunIdOrderByOpenedAtDesc(runId);
        Assertions.assertTrue(gates.size() >= 2);
        boolean hasRework = gates.stream().anyMatch(gate -> gate.getStatus() == GateStatus.REWORK_REQUESTED);
        boolean hasApproved = gates.stream().anyMatch(gate -> gate.getStatus() == GateStatus.APPROVED);
        Assertions.assertTrue(hasRework, "Expected rework gate in history");
        Assertions.assertTrue(hasApproved, "Expected approved gate in history");
    }

    @Test
    void terminalFlowCompletesImmediately() throws Exception {
        var flow = createPublishedFlow("terminal-flow", terminalOnlyFlowYaml("terminal-flow"), "complete");

        UUID runId = createRunViaApi(token, project.getId(), flow.getCanonicalName(), "No-op flow");
        RunEntity completed = waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.COMPLETED);

        Assertions.assertEquals(RunStatus.COMPLETED, completed.getStatus());

        MvcResult nodesResult = mockMvc.perform(get("/api/runs/{runId}/nodes", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode nodes = parseJson(readBody(nodesResult));
        Assertions.assertEquals(1, nodes.size());
        Assertions.assertEquals("terminal", nodes.get(0).path("node_kind").asText());
        Assertions.assertEquals("succeeded", nodes.get(0).path("status").asText());
    }

    private GateInstanceEntity waitForDifferentGate(UUID runId, UUID previousGateId, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            List<GateInstanceEntity> gates = gateInstanceRepository.findByRunIdOrderByOpenedAtDesc(runId);
            if (!gates.isEmpty() && !gates.get(0).getId().equals(previousGateId)) {
                return gates.get(0);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for next gate", ex);
            }
        }
        Assertions.fail("Next gate was not opened in time");
        return null;
    }

    private String aiFlowYaml(String flowId, String instruction, boolean failureTerminal) {
        String successTerminal = failureTerminal ? "terminal-success" : "terminal";
        String failureNode = failureTerminal ? "terminal-failure" : "terminal";
        return """
                id: %s
                version: "1.0"
                canonical_name: %s@1.0
                title: AI Flow
                description: Runtime AI test flow
                status: published
                start_node_id: ai-start
                rule_refs: []
                fail_on_missing_declared_output: true
                fail_on_missing_expected_mutation: true

                nodes:
                  - id: ai-start
                    title: AI Start
                    type: ai
                    execution_context: []
                    instruction: |
                      %s
                    skill_refs: []
                    produced_artifacts: []
                    expected_mutations: []
                    on_success: %s
                    on_failure: %s

                  - id: %s
                    title: Terminal
                    type: terminal
                    execution_context: []
                    produced_artifacts: []
                    expected_mutations: []
                %s
                """.formatted(
                flowId,
                flowId,
                instruction,
                successTerminal,
                failureNode,
                successTerminal,
                failureTerminal
                        ? "\n  - id: terminal-failure\n    title: Failure Terminal\n    type: terminal\n    execution_context: []\n    produced_artifacts: []\n    expected_mutations: []\n"
                        : ""
        );
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

    private String humanApprovalFlowYaml(String flowId) {
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
                    execution_context: []
                    instruction: |
                      echo "change line" >> README.md
                    produced_artifacts:
                      - scope: run
                        path: summary.md
                        required: true
                        modifiable: true
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
                """.formatted(flowId, flowId);
    }

    private String terminalOnlyFlowYaml(String flowId) {
        return """
                id: %s
                version: "1.0"
                canonical_name: %s@1.0
                title: Terminal Flow
                description: Runtime terminal-only flow
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
}

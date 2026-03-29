package ru.hgd.sdlc.runtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.support.RuntimeIntegrationTestBase;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RuntimeIntegrationTestConfig.class)
class RuntimeApiContractTest extends RuntimeIntegrationTestBase {
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
    void runAndGatePayloadContracts() throws Exception {
        var flow = createPublishedFlow("contract-flow", contractFlowYaml("contract-flow"), "implement-change");

        UUID runId = createRunViaApi(token, project.getId(), flow.getCanonicalName(), "Contract baseline");
        waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.WAITING_GATE);
        GateInstanceEntity gate = waitForGateStatus(runId, Duration.ofSeconds(10), GateStatus.AWAITING_DECISION);

        mockMvc.perform(get("/api/runs/{runId}", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run_id").isString())
                .andExpect(jsonPath("$.project_id").isString())
                .andExpect(jsonPath("$.target_branch").value("main"))
                .andExpect(jsonPath("$.flow_canonical_name").isString())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.current_node_id").isString())
                .andExpect(jsonPath("$.feature_request").isString())
                .andExpect(jsonPath("$.resource_version").isNumber())
                .andExpect(jsonPath("$.current_gate.gate_id").isString())
                .andExpect(jsonPath("$.current_gate.gate_kind").value("human_approval"))
                .andExpect(jsonPath("$.current_gate.payload.rework_mode").value("keep"))
                .andExpect(jsonPath("$.current_gate.payload.rework_keep_changes").value(true))
                .andExpect(jsonPath("$.current_gate.payload.rework_keep_changes_selectable").value(true))
                .andExpect(jsonPath("$.current_gate.payload.rework_discard_available").value(true))
                .andExpect(jsonPath("$.current_gate.payload.rework_discard_unavailable_reason").doesNotExist());

        mockMvc.perform(get("/api/runs")
                        .param("limit", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].run_id").isString())
                .andExpect(jsonPath("$[0].status").isString())
                .andExpect(jsonPath("$[0].current_gate.gate_id").isString());

        mockMvc.perform(get("/api/runs/{runId}/gates/current", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gate_id").isString())
                .andExpect(jsonPath("$.run_id").isString())
                .andExpect(jsonPath("$.node_execution_id").isString())
                .andExpect(jsonPath("$.node_id").isString())
                .andExpect(jsonPath("$.gate_kind").value("human_approval"))
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.resource_version").isNumber());

        mockMvc.perform(get("/api/gates/inbox")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gate_id").isString())
                .andExpect(jsonPath("$[0].status").isString())
                .andExpect(jsonPath("$[0].payload.git_summary").exists())
                .andExpect(jsonPath("$[0].payload.rework_mode").value("keep"));

        mockMvc.perform(get("/api/gates/{gateId}/changes", gate.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gate_id").isString())
                .andExpect(jsonPath("$.run_id").isString())
                .andExpect(jsonPath("$.gate_kind").value("human_approval"))
                .andExpect(jsonPath("$.git_changes").isArray())
                .andExpect(jsonPath("$.git_summary.files_changed").isNumber())
                .andExpect(jsonPath("$.git_summary.status_label").isString())
                .andExpect(jsonPath("$.status_label").isString());

        mockMvc.perform(get("/api/gates/{gateId}/diff", gate.getId())
                        .param("path", "README.md")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gate_id").isString())
                .andExpect(jsonPath("$.run_id").isString())
                .andExpect(jsonPath("$.path").value("README.md"))
                .andExpect(jsonPath("$.patch").isString())
                .andExpect(jsonPath("$.original_content").isString())
                .andExpect(jsonPath("$.modified_content").isString());
    }

    @Test
    void auditArtifactAndLogPayloadContracts() throws Exception {
        var flow = createPublishedFlow("contract-flow-2", contractFlowYaml("contract-flow-2"), "implement-change");

        UUID runId = createRunViaApi(token, project.getId(), flow.getCanonicalName(), "Contract completion");
        waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.WAITING_GATE);
        GateInstanceEntity gate = waitForGateStatus(runId, Duration.ofSeconds(10), GateStatus.AWAITING_DECISION);

        String approveBody = objectMapper.writeValueAsString(Map.of(
                "expected_gate_version", gate.getResourceVersion(),
                "comment", "Approved",
                "reviewed_artifact_version_ids", List.of()
        ));
        mockMvc.perform(post("/api/gates/{gateId}/approve", gate.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transition").value("on_approve"));

        waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.COMPLETED);

        MvcResult nodesResult = mockMvc.perform(get("/api/runs/{runId}/nodes", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].node_execution_id").isString())
                .andExpect(jsonPath("$[0].run_id").isString())
                .andExpect(jsonPath("$[0].node_id").isString())
                .andExpect(jsonPath("$[0].node_kind").isString())
                .andExpect(jsonPath("$[0].attempt_no").isNumber())
                .andReturn();
        JsonNode nodes = parseJson(readBody(nodesResult));
        String commandNodeExecutionId = nodes.get(0).path("node_execution_id").asText();

        MvcResult artifactsResult = mockMvc.perform(get("/api/runs/{runId}/artifacts", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].artifact_version_id").isString())
                .andExpect(jsonPath("$[0].run_id").isString())
                .andExpect(jsonPath("$[0].artifact_key").isString())
                .andExpect(jsonPath("$[0].path").isString())
                .andExpect(jsonPath("$[0].scope").isString())
                .andExpect(jsonPath("$[0].kind").isString())
                .andExpect(jsonPath("$[0].version_no").isNumber())
                .andReturn();
        JsonNode artifacts = parseJson(readBody(artifactsResult));
        String artifactVersionId = artifacts.get(0).path("artifact_version_id").asText();

        mockMvc.perform(get("/api/runs/{runId}/artifacts/{artifactVersionId}/content", runId, artifactVersionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifact_version_id").isString())
                .andExpect(jsonPath("$.run_id").isString())
                .andExpect(jsonPath("$.artifact_key").isString())
                .andExpect(jsonPath("$.path").isString())
                .andExpect(jsonPath("$.content").isString());

        mockMvc.perform(get("/api/runs/{runId}/audit", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].event_id").isString())
                .andExpect(jsonPath("$[0].run_id").isString())
                .andExpect(jsonPath("$[0].sequence_no").isNumber())
                .andExpect(jsonPath("$[0].event_type").isString())
                .andExpect(jsonPath("$[0].actor_type").isString())
                .andExpect(jsonPath("$[0].payload").exists());

        mockMvc.perform(get("/api/runs/{runId}/audit/query", runId)
                        .param("limit", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isArray())
                .andExpect(jsonPath("$.has_more").isBoolean());

        mockMvc.perform(get("/api/runs/{runId}/nodes/{nodeExecutionId}/log", runId, commandNodeExecutionId)
                        .header("Authorization", "Bearer " + token)
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isString())
                .andExpect(jsonPath("$.offset").isNumber())
                .andExpect(jsonPath("$.running").isBoolean());
    }

    @Test
    void reworkDiscardAvailabilityReflectsCheckpointPresence() throws Exception {
        var flow = createPublishedFlow(
                "discard-unavailable-flow",
                discardUnavailableFlowYaml("discard-unavailable-flow"),
                "review-change"
        );

        UUID runId = createRunViaApi(token, project.getId(), flow.getCanonicalName(), "Checkpoint availability");
        waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.WAITING_GATE);

        mockMvc.perform(get("/api/runs/{runId}", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_gate.gate_kind").value("human_approval"))
                .andExpect(jsonPath("$.current_gate.payload.rework_mode").value("keep"))
                .andExpect(jsonPath("$.current_gate.payload.rework_keep_changes").value(true))
                .andExpect(jsonPath("$.current_gate.payload.rework_keep_changes_selectable").value(true))
                .andExpect(jsonPath("$.current_gate.payload.rework_discard_available").value(false))
                .andExpect(jsonPath("$.current_gate.payload.rework_discard_unavailable_reason").value("target_checkpoint_not_found"));
    }

    private String contractFlowYaml(String flowId) {
        return """
                id: %s
                version: "1.0"
                canonical_name: %s@1.0
                title: Contract Flow
                description: Runtime contract flow
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
                      echo "contract line" >> README.md
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
                      Review changes.
                    produced_artifacts: []
                    expected_mutations: []
                    on_approve: complete
                    on_rework:
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

    private String discardUnavailableFlowYaml(String flowId) {
        return """
                id: %s
                version: "1.0"
                canonical_name: %s@1.0
                title: Discard Availability Flow
                description: Runtime discard availability contract flow
                status: published
                start_node_id: review-change
                rule_refs: []
                fail_on_missing_declared_output: true
                fail_on_missing_expected_mutation: true

                nodes:
                  - id: review-change
                    title: Review
                    type: human_approval
                    execution_context: []
                    instruction: |
                      Review changes.
                    produced_artifacts: []
                    expected_mutations: []
                    on_approve: complete
                    on_rework:
                      next_node: implement-change
                    allowed_roles:
                      - FLOW_CONFIGURATOR

                  - id: implement-change
                    title: Implement
                    type: command
                    checkpoint_before_run: true
                    execution_context: []
                    instruction: |
                      echo "implement change" >> README.md
                    produced_artifacts: []
                    expected_mutations:
                      - scope: project
                        path: README.md
                        required: true
                    on_success: complete

                  - id: complete
                    title: Terminal
                    type: terminal
                    execution_context: []
                    produced_artifacts: []
                    expected_mutations: []
                """.formatted(flowId, flowId);
    }
}

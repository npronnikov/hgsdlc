package ru.hgd.sdlc.runtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import ru.hgd.sdlc.runtime.support.RuntimeSnapshotAssertions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RuntimeIntegrationTestConfig.class)
class RuntimeBaselineSnapshotTest extends RuntimeIntegrationTestBase {
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
    void runtimeApiBaselineSnapshots() throws Exception {
        var flow = createPublishedFlow("baseline-flow", baselineFlowYaml("baseline-flow"), "implement-change");

        String createBody = objectMapper.writeValueAsString(Map.of(
                "project_id", project.getId(),
                "target_branch", "main",
                "flow_canonical_name", flow.getCanonicalName(),
                "feature_request", "Snapshot baseline request",
                "idempotency_key", UUID.randomUUID().toString()
        ));

        MvcResult createResult = mockMvc.perform(post("/api/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        String createResponse = readBody(createResult);
        UUID runId = UUID.fromString(parseJson(createResponse).path("run_id").asText());

        waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.WAITING_GATE);
        GateInstanceEntity gate = waitForGateStatus(runId, Duration.ofSeconds(10), GateStatus.AWAITING_DECISION);

        String runWaiting = readBody(mockMvc.perform(get("/api/runs/{runId}", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());

        String runsListWaiting = readBody(mockMvc.perform(get("/api/runs")
                        .param("limit", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());

        String nodesWaiting = readBody(mockMvc.perform(get("/api/runs/{runId}/nodes", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode nodesJson = parseJson(nodesWaiting);
        String commandNodeExecutionId = nodesJson.get(0).path("node_execution_id").asText();

        String currentGate = readBody(mockMvc.perform(get("/api/runs/{runId}/gates/current", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());

        String inbox = readBody(mockMvc.perform(get("/api/gates/inbox")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());

        String gateChanges = readBody(mockMvc.perform(get("/api/gates/{gateId}/changes", gate.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());

        String gateDiff = readBody(mockMvc.perform(get("/api/gates/{gateId}/diff", gate.getId())
                        .param("path", "README.md")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());

        String approveBody = objectMapper.writeValueAsString(Map.of(
                "expected_gate_version", gate.getResourceVersion(),
                "comment", "Looks good",
                "reviewed_artifact_version_ids", List.of()
        ));
        String approveResponse = readBody(mockMvc.perform(post("/api/gates/{gateId}/approve", gate.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveBody))
                .andExpect(status().isOk())
                .andReturn());

        waitForRunStatus(runId, Duration.ofSeconds(10), RunStatus.COMPLETED);

        String runCompleted = readBody(mockMvc.perform(get("/api/runs/{runId}", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());

        String artifacts = readBody(mockMvc.perform(get("/api/runs/{runId}/artifacts", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode artifactsJson = parseJson(artifacts);
        String artifactVersionId = artifactsJson.get(0).path("artifact_version_id").asText();

        String artifactContent = readBody(mockMvc.perform(get("/api/runs/{runId}/artifacts/{artifactVersionId}/content", runId, artifactVersionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());

        String audit = readBody(mockMvc.perform(get("/api/runs/{runId}/audit", runId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());

        String auditQuery = readBody(mockMvc.perform(get("/api/runs/{runId}/audit/query", runId)
                        .param("limit", "20")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());

        String nodeLog = readBody(mockMvc.perform(get("/api/runs/{runId}/nodes/{nodeExecutionId}/log", runId, commandNodeExecutionId)
                        .param("offset", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());

        List<java.nio.file.Path> dynamicRoots = List.of(tempDir);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, createResponse, "runtime/baseline/runs-create.json", dynamicRoots);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, runWaiting, "runtime/baseline/runs-get-waiting.json", dynamicRoots);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, runsListWaiting, "runtime/baseline/runs-list-waiting.json", dynamicRoots);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, nodesWaiting, "runtime/baseline/runs-nodes-waiting.json", dynamicRoots);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, currentGate, "runtime/baseline/runs-current-gate.json", dynamicRoots);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, inbox, "runtime/baseline/gates-inbox.json", dynamicRoots);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, gateChanges, "runtime/baseline/gates-changes.json", dynamicRoots);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, gateDiff, "runtime/baseline/gates-diff.json", dynamicRoots);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, approveResponse, "runtime/baseline/gates-approve.json", dynamicRoots);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, runCompleted, "runtime/baseline/runs-get-completed.json", dynamicRoots);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, artifacts, "runtime/baseline/runs-artifacts.json", dynamicRoots);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, artifactContent, "runtime/baseline/runs-artifact-content.json", dynamicRoots);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, audit, "runtime/baseline/runs-audit.json", dynamicRoots);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, auditQuery, "runtime/baseline/runs-audit-query.json", dynamicRoots);
        RuntimeSnapshotAssertions.assertJsonSnapshot(objectMapper, nodeLog, "runtime/baseline/runs-node-log.json", dynamicRoots);
    }

    private String baselineFlowYaml(String flowId) {
        return """
                id: %s
                version: "1.0"
                canonical_name: %s@1.0
                title: Baseline Flow
                description: Runtime baseline flow for snapshots
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
                      echo "snapshot line" >> README.md
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
                      Review snapshot changes.
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
}

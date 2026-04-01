package ru.hgd.sdlc.runtime.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.hgd.sdlc.flow.domain.FlowApprovalStatus;
import ru.hgd.sdlc.flow.domain.FlowLifecycleStatus;
import ru.hgd.sdlc.flow.domain.FlowStatus;
import ru.hgd.sdlc.flow.domain.FlowVersion;
import ru.hgd.sdlc.flow.infrastructure.FlowVersionRepository;
import ru.hgd.sdlc.project.domain.Project;
import ru.hgd.sdlc.project.domain.ProjectStatus;
import ru.hgd.sdlc.project.infrastructure.ProjectRepository;
import ru.hgd.sdlc.publication.domain.PublicationStatus;
import ru.hgd.sdlc.runtime.domain.GateInstanceEntity;
import ru.hgd.sdlc.runtime.domain.GateStatus;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.infrastructure.GateInstanceRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;
import ru.hgd.sdlc.settings.application.SettingsService;

public abstract class RuntimeIntegrationTestBase {
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected SettingsService settingsService;

    @Autowired
    protected ProjectRepository projectRepository;

    @Autowired
    protected FlowVersionRepository flowVersionRepository;

    @Autowired
    protected RunRepository runRepository;

    @Autowired
    protected GateInstanceRepository gateInstanceRepository;

    @TempDir
    protected Path tempDir;

    protected void resetRuntimeData() {
        List<String> statements = List.of(
                "DELETE FROM publication_approvals",
                "DELETE FROM publication_jobs",
                "DELETE FROM publication_requests",
                "DELETE FROM audit_events",
                "DELETE FROM artifact_versions",
                "DELETE FROM gate_instances",
                "DELETE FROM node_executions",
                "DELETE FROM runs",
                "DELETE FROM flows",
                "DELETE FROM projects",
                "DELETE FROM idempotency_keys",
                "DELETE FROM system_settings"
        );
        for (String sql : statements) {
            jdbcTemplate.execute(sql);
        }
    }

    protected void configureRuntime(Path workspaceRoot) {
        settingsService.updateWorkspaceRoot(workspaceRoot.toAbsolutePath().toString(), "test");
        settingsService.updateRuntimeCodingAgent("stub", "test");
        settingsService.updateAiTimeoutSeconds(60, "test");
        settingsService.updateCatalogSettings(
                "",
                "main",
                "pr",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "Runtime Test",
                "runtime-test@example.com",
                "test"
        );
    }

    protected String loginAsTestUser() throws Exception {
        String loginPayload = objectMapper.writeValueAsString(Map.of(
                "username", "test",
                "password", "test"
        ));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("token").asText();
    }

    protected Project createProject(String repoUrl) {
        Instant now = Instant.now();
        Project project = Project.builder()
                .id(UUID.randomUUID())
                .name("Runtime Test Project")
                .repoUrl(repoUrl)
                .defaultBranch("main")
                .status(ProjectStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return projectRepository.save(project);
    }

    protected FlowVersion createPublishedFlow(String flowId, String flowYaml, String startNodeId) {
        String version = "1.0";
        Instant now = Instant.now();
        String sourcePath = "flows/" + flowId + "/" + version;
        materializeFlowToMirror(sourcePath, flowYaml);
        FlowVersion flow = FlowVersion.builder()
                .id(UUID.randomUUID())
                .flowId(flowId)
                .version(version)
                .canonicalName(flowId + "@" + version)
                .status(FlowStatus.PUBLISHED)
                .title(flowId)
                .description("runtime test flow")
                .startNodeId(startNodeId)
                .ruleRefs(List.of())
                .codingAgent("stub")
                .flowYaml(flowYaml)
                .checksum(null)
                .teamCode("qa")
                .platformCode("BACK")
                .tags(List.of("runtime", "test"))
                .flowKind("analysis")
                .riskLevel("low")
                .approvalStatus(FlowApprovalStatus.PUBLISHED)
                .approvedBy("test")
                .approvedAt(now)
                .publishedAt(now)
                .sourceRef("local")
                .sourcePath(sourcePath)
                .lifecycleStatus(FlowLifecycleStatus.ACTIVE)
                .publicationStatus(PublicationStatus.PUBLISHED)
                .scope("organization")
                .publishedCommitSha(null)
                .publishedPrUrl(null)
                .lastPublishError(null)
                .savedBy("test")
                .savedAt(now)
                .build();
        return flowVersionRepository.save(flow);
    }

    private void materializeFlowToMirror(String sourcePath, String flowYaml) {
        try {
            Path mirrorRoot = resolveCatalogMirrorRoot();
            Path flowFile = mirrorRoot.resolve(sourcePath).resolve("FLOW.yaml").normalize();
            Files.createDirectories(flowFile.getParent());
            Files.writeString(flowFile, flowYaml, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to materialize flow yaml in catalog mirror", ex);
        }
    }

    private Path resolveCatalogMirrorRoot() {
        String repoUrl = settingsService.getCatalogRepoUrl();
        String suffix = Integer.toHexString(repoUrl.toLowerCase(Locale.ROOT).hashCode());
        return Path.of(settingsService.getWorkspaceRoot()).toAbsolutePath().normalize()
                .resolve(".catalog-repo")
                .resolve(suffix);
    }

    protected Path initGitRemoteRepo() {
        try {
            Path seedRepo = tempDir.resolve("seed-repo");
            Path remoteRepo = tempDir.resolve("remote-repo.git");
            Files.createDirectories(seedRepo);
            runCommand(tempDir, "git", "init", seedRepo.toAbsolutePath().toString());
            runCommand(seedRepo, "git", "config", "user.email", "runtime-test@example.com");
            runCommand(seedRepo, "git", "config", "user.name", "Runtime Test");
            Files.writeString(
                    seedRepo.resolve("README.md"),
                    "# Runtime integration test\n",
                    StandardCharsets.UTF_8
            );
            runCommand(seedRepo, "git", "add", ".");
            runCommand(seedRepo, "git", "commit", "-m", "Initial commit");
            runCommand(seedRepo, "git", "branch", "-M", "main");
            runCommand(tempDir, "git", "init", "--bare", remoteRepo.toAbsolutePath().toString());
            runCommand(seedRepo, "git", "remote", "add", "origin", remoteRepo.toAbsolutePath().toString());
            runCommand(seedRepo, "git", "push", "-u", "origin", "main");
            return remoteRepo;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize git fixture repository", ex);
        }
    }

    protected UUID createRunViaApi(String token, UUID projectId, String flowCanonicalName, String featureRequest) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "project_id", projectId,
                "target_branch", "main",
                "flow_canonical_name", flowCanonicalName,
                "feature_request", featureRequest,
                "publish_mode", "local",
                "idempotency_key", UUID.randomUUID().toString()
        ));
        MvcResult result = mockMvc.perform(post("/api/runs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).path("run_id").asText());
    }

    protected RunEntity waitForRunStatus(UUID runId, Duration timeout, RunStatus... statuses) {
        List<RunStatus> expected = List.of(statuses);
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            RunEntity run = runRepository.findById(runId)
                    .orElseThrow(() -> new IllegalStateException("Run not found: " + runId));
            if (expected.contains(run.getStatus())) {
                return run;
            }
            sleep(50);
        }
        RunEntity current = runRepository.findById(runId).orElse(null);
        Assertions.fail("Run did not reach expected status " + expected + ", current="
                + (current == null ? "null" : current.getStatus()));
        return null;
    }

    protected GateInstanceEntity waitForGateStatus(UUID runId, Duration timeout, GateStatus... statuses) {
        List<GateStatus> expected = List.of(statuses);
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            GateInstanceEntity gate = gateInstanceRepository.findByRunIdOrderByOpenedAtDesc(runId)
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (gate != null && expected.contains(gate.getStatus())) {
                return gate;
            }
            sleep(50);
        }
        Assertions.fail("Gate did not reach expected status " + expected);
        return null;
    }

    protected JsonNode parseJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    protected String encodeBase64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    protected String readBody(MvcResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read response body", ex);
        }
    }

    private void runCommand(Path workDir, String... command) throws IOException {
        Process process = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .start();
        String output;
        try (var input = process.getInputStream()) {
            output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Command failed (" + String.join(" ", command) + "):\n" + output);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Command interrupted: " + String.join(" ", command), ex);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting", ex);
        }
    }
}

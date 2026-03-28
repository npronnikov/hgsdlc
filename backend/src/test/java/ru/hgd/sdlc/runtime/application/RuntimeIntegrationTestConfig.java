package ru.hgd.sdlc.runtime.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.flow.domain.PathRequirement;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;

@TestConfiguration
public class RuntimeIntegrationTestConfig {
    @Bean
    public TaskExecutor taskExecutor() {
        return Runnable::run;
    }

    @Bean
    public CodingAgentStrategy stubCodingAgentStrategy() {
        return new StubCodingAgentStrategy();
    }

    private static final class StubCodingAgentStrategy implements CodingAgentStrategy {
        private static final String FAIL_MARKER = "[[FAIL]]";

        @Override
        public String codingAgent() {
            return "stub";
        }

        @Override
        public AgentInvocationContext materializeWorkspace(MaterializationRequest request) throws CodingAgentException {
            try {
                Path projectRoot = request.projectRoot();
                Path nodeExecutionRoot = request.nodeExecutionRoot();
                NodeExecutionEntity execution = request.execution();
                createDirectories(projectRoot.resolve(".stub").resolve("skills"));
                createDirectories(nodeExecutionRoot);

                Path rulesPath = projectRoot.resolve(".stub").resolve("STUB.md");
                Path promptPath = nodeExecutionRoot.resolve("prompt.md");
                Path skillsRoot = projectRoot.resolve(".stub").resolve("skills");
                Path stdoutPath = nodeExecutionRoot.resolve("agent.stdout.log");
                Path stderrPath = nodeExecutionRoot.resolve("agent.stderr.log");

                String prompt = buildPrompt(request);
                String checksum = ChecksumUtil.sha256(prompt);
                AgentPromptBuilder.AgentInput agentInput = new AgentPromptBuilder.AgentInput(
                        request.flowModel() != null
                                && request.flowModel().getStartNodeId() != null
                                && request.flowModel().getStartNodeId().equals(request.node().getId()),
                        request.run().getFeatureRequest(),
                        request.run().getPendingReworkInstruction(),
                        request.node().getInstruction(),
                        summarizeInputs(request.resolvedContext()),
                        List.of("summary")
                );
                AgentPromptBuilder.AgentPromptPackage promptPackage = new AgentPromptBuilder.AgentPromptPackage(
                        agentInput,
                        prompt,
                        checksum
                );

                writeFile(rulesPath, "# Stub rules\n\nRuntime integration test strategy\n");
                writeFile(promptPath, prompt);
                materializeDeclaredArtifacts(request);

                boolean shouldFail = shouldFail(request.node().getInstruction());
                String script = shouldFail
                        ? "echo '{\"type\":\"stream_event\",\"event\":{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"stub ai output\"}}}'; "
                                + "echo '{\"type\":\"error\",\"message\":\"stub failure\"}'; exit 1"
                        : "echo '{\"type\":\"stream_event\",\"event\":{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"stub ai output\"}}}'; "
                                + "echo '{\"type\":\"result\",\"result\":\" done\"}'; exit 0";

                return new AgentInvocationContext(
                        projectRoot,
                        List.of("zsh", "-lc", script),
                        promptPath,
                        rulesPath,
                        skillsRoot,
                        stdoutPath,
                        stderrPath,
                        promptPackage
                );
            } catch (IOException ex) {
                throw new CodingAgentException("AGENT_WORKSPACE_FAILED", "Stub strategy failed: " + ex.getMessage());
            }
        }

        private String buildPrompt(MaterializationRequest request) {
            StringBuilder sb = new StringBuilder();
            sb.append("Task:\n");
            if (request.run().getFeatureRequest() != null) {
                sb.append(request.run().getFeatureRequest());
            }
            sb.append("\n\nInstruction:\n");
            if (request.node().getInstruction() != null) {
                sb.append(request.node().getInstruction());
            }
            return sb.toString();
        }

        private List<String> summarizeInputs(List<Map<String, Object>> resolvedContext) {
            if (resolvedContext == null || resolvedContext.isEmpty()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (Map<String, Object> item : resolvedContext) {
                if (item == null) {
                    continue;
                }
                Object type = item.get("type");
                Object path = item.get("path");
                result.add(String.valueOf(type) + ":" + String.valueOf(path));
            }
            return result;
        }

        private void materializeDeclaredArtifacts(MaterializationRequest request) throws IOException {
            List<PathRequirement> produced = request.node().getProducedArtifacts() == null
                    ? List.of()
                    : request.node().getProducedArtifacts();
            for (PathRequirement requirement : produced) {
                if (requirement == null || requirement.getPath() == null || requirement.getPath().isBlank()) {
                    continue;
                }
                Path target = "project".equals(normalizeScope(requirement.getScope()))
                        ? request.projectRoot().resolve(requirement.getPath()).normalize()
                        : request.nodeExecutionRoot().resolve(requirement.getPath()).normalize();
                createDirectories(target.getParent());
                writeFile(
                        target,
                        "# Stub artifact\n\nnode=" + request.node().getId() + "\nattempt=" + request.execution().getAttemptNo() + "\n"
                );
            }
        }

        private String normalizeScope(String rawScope) {
            if (rawScope == null) {
                return "run";
            }
            String normalized = rawScope.trim().toLowerCase(Locale.ROOT);
            return "project".equals(normalized) ? "project" : "run";
        }

        private boolean shouldFail(String instruction) {
            return instruction != null && instruction.contains(FAIL_MARKER);
        }

        private void writeFile(Path path, String content) throws IOException {
            createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        }

        private void createDirectories(Path path) throws IOException {
            if (path != null) {
                Files.createDirectories(path);
            }
        }
    }
}

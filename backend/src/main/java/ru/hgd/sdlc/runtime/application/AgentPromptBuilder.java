package ru.hgd.sdlc.runtime.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.flow.domain.PathRequirement;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;

@Service
public class AgentPromptBuilder {
    private static final String TASK_SECTION_TOKEN = "{{TASK_SECTION}}";
    private static final String REQUEST_CLARIFICATION_SECTION_TOKEN = "{{REQUEST_CLARIFICATION_SECTION}}";
    private static final String NODE_INSTRUCTION_SECTION_TOKEN = "{{NODE_INSTRUCTION_SECTION}}";
    private static final String INPUTS_SECTION_TOKEN = "{{INPUTS_SECTION}}";
    private static final String EXPECTED_RESULTS_SECTION_TOKEN = "{{EXPECTED_RESULTS_SECTION}}";
    private static final String FOOTER_SECTION_TOKEN = "{{FOOTER_SECTION}}";

    private final String promptTemplate;
    private final PromptTexts promptTexts;

    public AgentPromptBuilder(
            @Value("classpath:runtime/prompt-template.md") Resource promptTemplateResource,
            @Value("classpath:runtime/prompt-texts.ru.yaml") Resource promptTextsResource
    ) {
        this.promptTemplate = readTemplate(promptTemplateResource);
        this.promptTexts = readPromptTexts(promptTextsResource);
    }

    public AgentPromptPackage build(
            RunEntity run,
            FlowModel flowModel,
            NodeModel node,
            NodeExecutionEntity execution,
            List<Map<String, Object>> resolvedContext
    ) {
        boolean startNode = isStartNode(flowModel, node);
        String task = trimToNull(run.getFeatureRequest());
        String requestClarification = trimToNull(run.getPendingReworkInstruction());
        String instruction = trimToNull(node.getInstruction());
        List<String> inputs = summarizePromptInputs(resolvedContext);
        List<String> expectedResults = summarizeExpectedResults(node, execution);
        AgentInput agentInput = new AgentInput(startNode, task, requestClarification, instruction, inputs, expectedResults);
        String prompt = renderPrompt(agentInput);
        return new AgentPromptPackage(agentInput, prompt, ChecksumUtil.sha256(prompt));
    }

    private String renderPrompt(AgentInput agentInput) {
        String taskSection = buildTaskSection(agentInput.task());
        String clarificationSection = buildRequestClarificationSection(agentInput.requestClarification());
        String instructionSection = buildNodeInstructionSection(agentInput.nodeInstruction());
        String inputsSection = buildInputsSection(agentInput.inputs());
        String expectedResultsSection = buildExpectedResultsSection(agentInput.expectedResults());
        String footerSection = promptTexts.footer() + "\n";

        String rendered = promptTemplate
                .replace(TASK_SECTION_TOKEN, taskSection)
                .replace(REQUEST_CLARIFICATION_SECTION_TOKEN, clarificationSection)
                .replace(NODE_INSTRUCTION_SECTION_TOKEN, instructionSection)
                .replace(INPUTS_SECTION_TOKEN, inputsSection)
                .replace(EXPECTED_RESULTS_SECTION_TOKEN, expectedResultsSection)
                .replace(FOOTER_SECTION_TOKEN, footerSection);
        return normalizePrompt(rendered);
    }

    private String buildTaskSection(String task) {
        if (task == null) {
            return "";
        }
        return promptTexts.taskHeader() + "\n" + task + "\n\n";
    }

    private String buildRequestClarificationSection(String clarification) {
        if (clarification == null) {
            return "";
        }
        return promptTexts.requestClarificationHeader() + "\n" + clarification + "\n\n";
    }

    private String buildNodeInstructionSection(String instruction) {
        if (instruction == null) {
            return "";
        }
        return promptTexts.nodeInstructionHeader() + "\n" + instruction + "\n\n";
    }

    private String buildInputsSection(List<String> inputs) {
        if (inputs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(promptTexts.inputsHeader()).append("\n");
        for (String input : inputs) {
            sb.append("- ").append(input).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String buildExpectedResultsSection(List<String> expectedResults) {
        if (expectedResults.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(promptTexts.expectedResultsHeader()).append("\n");
        for (String expectedResult : expectedResults) {
            sb.append("- ").append(expectedResult).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String normalizePrompt(String prompt) {
        String normalized = prompt.replace("\r\n", "\n");
        return normalized.replaceAll("\n{3,}", "\n\n");
    }

    private String readTemplate(Resource templateResource) {
        try (InputStream inputStream = templateResource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load runtime prompt template", exception);
        }
    }

    private PromptTexts readPromptTexts(Resource promptTextsResource) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream inputStream = promptTextsResource.getInputStream()) {
            JsonNode root = mapper.readTree(inputStream);
            return new PromptTexts(
                    text(root, "/sections/task_header"),
                    text(root, "/sections/request_clarification_header"),
                    text(root, "/sections/node_instruction_header"),
                    text(root, "/sections/inputs_header"),
                    text(root, "/sections/expected_results_header"),
                    text(root, "/sections/footer"),
                    text(root, "/inputs/use_upstream_artifact_by_path"),
                    text(root, "/inputs/use_upstream_artifact_by_key_and_path"),
                    text(root, "/expected_results/required_artifacts"),
                    text(root, "/expected_results/required_run_paths"),
                    text(root, "/expected_results/required_mutations"),
                    text(root, "/expected_results/summary")
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load runtime prompt texts", exception);
        }
    }

    private String text(JsonNode root, String jsonPointer) {
        JsonNode node = root.at(jsonPointer);
        if (node.isMissingNode() || node.isNull()) {
            throw new IllegalStateException("Missing prompt text: " + jsonPointer);
        }
        String value = trimToNull(node.asText());
        if (value == null) {
            throw new IllegalStateException("Blank prompt text: " + jsonPointer);
        }
        return value;
    }

    private List<String> summarizePromptInputs(List<Map<String, Object>> resolvedContext) {
        List<String> inputs = new ArrayList<>();
        for (Map<String, Object> contextEntry : resolvedContext) {
            String type = normalize(asString(contextEntry.get("type")));
            switch (type) {
                case "artifact_ref" -> {
                    String artifactKey = trimToNull(asString(contextEntry.get("artifact_key")));
                    String path = trimToNull(asString(contextEntry.get("path")));
                    inputs.add(artifactKey == null
                            ? promptTexts.useUpstreamArtifactByPath()
                                    .replace("{path}", path == null ? "path is not provided" : path)
                            : promptTexts.useUpstreamArtifactByKeyAndPath()
                                    .replace("{artifact_key}", artifactKey)
                                    .replace("{path}", path == null ? "path is not provided" : path));
                }
                default -> {
                }
            }
        }
        return inputs.stream().distinct().toList();
    }

    private List<String> summarizeExpectedResults(NodeModel node, NodeExecutionEntity execution) {
        List<String> expectedResults = new ArrayList<>();
        List<String> requiredArtifacts = new ArrayList<>();
        List<String> requiredRunScopePaths = new ArrayList<>();
        String outputArtifact = trimToNull(node.getOutputArtifact());
        if (outputArtifact != null) {
            requiredArtifacts.add(outputArtifact);
        }
        List<PathRequirement> producedArtifacts = node.getProducedArtifacts() == null ? List.of() : node.getProducedArtifacts();
        for (PathRequirement requirement : producedArtifacts) {
            if (requirement == null || !Boolean.TRUE.equals(requirement.getRequired())) {
                continue;
            }
            String path = trimToNull(requirement.getPath());
            if (path != null) {
                requiredArtifacts.add(artifactKeyForPath(path));
                if (!"project".equals(normalize(requirement.getScope()))) {
                    requiredRunScopePaths.add(runScopeArtifactPath(node, execution, path));
                }
            }
        }
        List<String> distinctArtifacts = requiredArtifacts.stream().distinct().toList();
        if (!distinctArtifacts.isEmpty()) {
            expectedResults.add(promptTexts.requiredArtifacts()
                    .replace("{artifacts}", String.join(", ", distinctArtifacts)));
        }
        List<String> distinctRunScopePaths = requiredRunScopePaths.stream().distinct().toList();
        if (!distinctRunScopePaths.isEmpty()) {
            expectedResults.add(promptTexts.requiredRunPaths()
                    .replace("{paths}", String.join(", ", distinctRunScopePaths)));
        }
        if (hasRequiredMutations(node.getExpectedMutations())) {
            expectedResults.add(promptTexts.requiredMutations());
        }
        expectedResults.add(promptTexts.summary());
        return expectedResults;
    }

    private String runScopeArtifactPath(NodeModel node, NodeExecutionEntity execution, String artifactPath) {
        String normalized = artifactPath.replace('\\', '/');
        return ".hgsdlc/nodes/" + node.getId() + "/attempt-" + execution.getAttemptNo() + "/" + normalized;
    }

    private boolean hasRequiredMutations(List<PathRequirement> mutations) {
        if (mutations == null) {
            return false;
        }
        for (PathRequirement mutation : mutations) {
            if (mutation != null && Boolean.TRUE.equals(mutation.getRequired())) {
                return true;
            }
        }
        return false;
    }

    private boolean isStartNode(FlowModel flowModel, NodeModel node) {
        return flowModel != null
                && node != null
                && flowModel.getStartNodeId() != null
                && flowModel.getStartNodeId().equals(node.getId());
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

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase().replace('-', '_').replace(' ', '_');
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String asString(Object value) {
        return value instanceof String stringValue ? stringValue : null;
    }

    public record AgentPromptPackage(
            AgentInput agentInput,
            String prompt,
            String promptChecksum
    ) {}

    public record AgentInput(
            boolean startNode,
            String task,
            String requestClarification,
            String nodeInstruction,
            List<String> inputs,
            List<String> expectedResults
    ) {}

    private record PromptTexts(
            String taskHeader,
            String requestClarificationHeader,
            String nodeInstructionHeader,
            String inputsHeader,
            String expectedResultsHeader,
            String footer,
            String useUpstreamArtifactByPath,
            String useUpstreamArtifactByKeyAndPath,
            String requiredArtifacts,
            String requiredRunPaths,
            String requiredMutations,
            String summary
    ) {}
}

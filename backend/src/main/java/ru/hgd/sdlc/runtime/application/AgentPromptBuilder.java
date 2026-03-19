package ru.hgd.sdlc.runtime.application;

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

    public AgentPromptBuilder(
            @Value("classpath:runtime/prompt-template.md") Resource promptTemplateResource
    ) {
        this.promptTemplate = readTemplate(promptTemplateResource);
    }

    public AgentPromptPackage build(
            RunEntity run,
            FlowModel flowModel,
            NodeModel node,
            List<Map<String, Object>> resolvedContext
    ) {
        boolean startNode = isStartNode(flowModel, node);
        String task = startNode ? trimToNull(run.getFeatureRequest()) : null;
        String requestClarification = trimToNull(run.getPendingReworkInstruction());
        String instruction = trimToNull(node.getInstruction());
        List<String> inputs = summarizePromptInputs(resolvedContext, startNode);
        List<String> expectedResults = summarizeExpectedResults(node);
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
        String footerSection = "Use repository rules and installed skills already prepared for this run.\n";

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
        return "Task:\n" + task + "\n\n";
    }

    private String buildRequestClarificationSection(String clarification) {
        if (clarification == null) {
            return "";
        }
        return "Уточнение запроса:\n" + clarification + "\n\n";
    }

    private String buildNodeInstructionSection(String instruction) {
        if (instruction == null) {
            return "";
        }
        return "Node instruction:\n" + instruction + "\n\n";
    }

    private String buildInputsSection(List<String> inputs) {
        if (inputs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Available inputs:\n");
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
        sb.append("Expected result:\n");
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

    private List<String> summarizePromptInputs(List<Map<String, Object>> resolvedContext, boolean includeUserRequest) {
        List<String> inputs = new ArrayList<>();
        for (Map<String, Object> contextEntry : resolvedContext) {
            String type = normalize(asString(contextEntry.get("type")));
            switch (type) {
                case "user_request" -> {
                    if (!includeUserRequest) {
                        inputs.add("Continue from the existing workflow state.");
                    }
                }
                case "artifact_ref" -> {
                    String artifactKey = trimToNull(asString(contextEntry.get("artifact_key")));
                    inputs.add(artifactKey == null
                            ? "Use the available upstream artifact."
                            : "Use upstream artifact '" + artifactKey + "'.");
                }
                default -> {
                }
            }
        }
        return inputs.stream().distinct().toList();
    }

    private List<String> summarizeExpectedResults(NodeModel node) {
        List<String> expectedResults = new ArrayList<>();
        List<String> requiredArtifacts = new ArrayList<>();
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
            }
        }
        List<String> distinctArtifacts = requiredArtifacts.stream().distinct().toList();
        if (!distinctArtifacts.isEmpty()) {
            expectedResults.add("Produce required artifacts: " + String.join(", ", distinctArtifacts) + ".");
        }
        if (hasRequiredMutations(node.getExpectedMutations())) {
            expectedResults.add("Apply the repository changes required for this node.");
        }
        expectedResults.add("Return a concise summary of the completed work.");
        return expectedResults;
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
}

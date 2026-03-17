package ru.hgd.sdlc.runtime.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.flow.domain.PathRequirement;
import ru.hgd.sdlc.runtime.domain.RunEntity;

@Service
public class AgentPromptBuilder {
    public AgentPromptPackage build(
            RunEntity run,
            FlowModel flowModel,
            NodeModel node,
            List<Map<String, Object>> resolvedContext
    ) {
        boolean startNode = isStartNode(flowModel, node);
        String task = startNode ? trimToNull(run.getFeatureRequest()) : null;
        String instruction = trimToNull(node.getInstruction());
        List<String> inputs = summarizePromptInputs(resolvedContext, startNode);
        List<String> expectedResults = summarizeExpectedResults(node);
        AgentInput agentInput = new AgentInput(startNode, task, instruction, inputs, expectedResults);
        String prompt = renderPrompt(agentInput);
        return new AgentPromptPackage(agentInput, prompt, ChecksumUtil.sha256(prompt));
    }

    private String renderPrompt(AgentInput agentInput) {
        StringBuilder sb = new StringBuilder();
        if (agentInput.task() != null) {
            sb.append("Task:\n");
            sb.append(agentInput.task()).append("\n\n");
        }
        if (agentInput.nodeInstruction() != null) {
            sb.append("Node instruction:\n");
            sb.append(agentInput.nodeInstruction()).append("\n\n");
        }
        if (!agentInput.inputs().isEmpty()) {
            sb.append("Available inputs:\n");
            for (String input : agentInput.inputs()) {
                sb.append("- ").append(input).append("\n");
            }
            sb.append("\n");
        }
        if (!agentInput.expectedResults().isEmpty()) {
            sb.append("Expected result:\n");
            for (String expectedResult : agentInput.expectedResults()) {
                sb.append("- ").append(expectedResult).append("\n");
            }
            sb.append("\n");
        }
        sb.append("Use repository rules and installed skills already prepared for this run.\n");
        return sb.toString();
    }

    private List<String> summarizePromptInputs(List<Map<String, Object>> resolvedContext, boolean includeUserRequest) {
        List<String> inputs = new ArrayList<>();
        int fileRefs = 0;
        int directoryRefs = 0;
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
                case "file_ref" -> {
                    fileRefs++;
                    inputs.add("Use provided file input #" + fileRefs + ".");
                }
                case "directory_ref" -> {
                    directoryRefs++;
                    inputs.add("Use provided directory input #" + directoryRefs + ".");
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
            String nodeInstruction,
            List<String> inputs,
            List<String> expectedResults
    ) {}
}

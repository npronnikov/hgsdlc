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
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.settings.application.SettingsService;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.flow.domain.PathRequirement;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;

@Service
public class AgentPromptBuilder {
    private static final String SYSTEM_INTRO_SECTION_TOKEN      = "{{SYSTEM_INTRO_SECTION}}";
    private static final String WORKFLOW_PROGRESS_SECTION_TOKEN = "{{WORKFLOW_PROGRESS_SECTION}}";
    private static final String CONTEXT_SECTION_TOKEN           = "{{CONTEXT_SECTION}}";
    private static final String TASK_SECTION_TOKEN              = "{{TASK_SECTION}}";
    private static final String INPUTS_SECTION_TOKEN            = "{{INPUTS_SECTION}}";
    private static final String EXPECTED_OUTPUTS_SECTION_TOKEN  = "{{EXPECTED_OUTPUTS_SECTION}}";
    private static final String STRUCTURED_OUTPUT_SECTION_TOKEN = "{{STRUCTURED_OUTPUT_SECTION}}";
    private static final String FOOTER_SECTION_TOKEN            = "{{FOOTER_SECTION}}";

    private final String promptTemplate;
    private final Map<String, PromptTexts> promptTextsMap;
    private final SettingsService settingsService;

    public AgentPromptBuilder(
            @Value("classpath:runtime/prompt-template.md") Resource promptTemplateResource,
            @Value("classpath:runtime/prompt-texts.en.yaml") Resource enTextsResource,
            @Value("classpath:runtime/prompt-texts.ru.yaml") Resource ruTextsResource,
            @Lazy SettingsService settingsService
    ) {
        this.promptTemplate = readTemplate(promptTemplateResource);
        this.promptTextsMap = Map.of(
                "en", readPromptTexts(enTextsResource),
                "ru", readPromptTexts(ruTextsResource)
        );
        this.settingsService = settingsService;
    }

    private PromptTexts resolvePromptTexts() {
        String lang = settingsService.getPromptLanguage();
        return promptTextsMap.getOrDefault(lang, promptTextsMap.get("en"));
    }

    public AgentPromptPackage build(
            RunEntity run,
            FlowModel flowModel,
            NodeModel node,
            NodeExecutionEntity execution,
            List<Map<String, Object>> resolvedContext,
            List<WorkflowProgressEntry> workflowProgress
    ) {
        boolean startNode = isStartNode(flowModel, node);
        String context = trimToNull(run.getFeatureRequest());
        String rework = trimToNull(run.getPendingReworkInstruction());
        String instruction = trimToNull(node.getInstruction());
        boolean taskIsRework = rework != null;
        String task = taskIsRework ? rework : instruction;
        String nodeInstructionContext = taskIsRework ? instruction : null;

        PromptTexts texts = resolvePromptTexts();
        List<String> inputs = summarizePromptInputs(resolvedContext, texts);
        List<String> outputFiles = summarizeOutputFiles(node, execution);
        List<String> projectChangePaths = summarizeProjectChangePaths(node);
        boolean hasProjectChanges = !projectChangePaths.isEmpty()
                || hasRequiredMutations(node.getExpectedMutations());

        AgentInput agentInput = new AgentInput(
                startNode,
                context,
                task,
                taskIsRework,
                nodeInstructionContext,
                inputs,
                outputFiles,
                projectChangePaths,
                hasProjectChanges,
                workflowProgress == null ? List.of() : workflowProgress,
                node.getId(),
                execution.getAttemptNo()
        );
        String prompt = renderPrompt(agentInput, texts);
        return new AgentPromptPackage(agentInput, prompt, ChecksumUtil.sha256(prompt));
    }

    private String renderPrompt(AgentInput a, PromptTexts texts) {
        String rendered = promptTemplate
                .replace(SYSTEM_INTRO_SECTION_TOKEN,      buildSystemIntroSection(texts))
                .replace(WORKFLOW_PROGRESS_SECTION_TOKEN, buildWorkflowProgressSection(a.workflowProgress(), a.stepId(), a.attemptNo(), texts))
                .replace(CONTEXT_SECTION_TOKEN,           buildContextSection(a.context(), texts))
                .replace(TASK_SECTION_TOKEN,              buildTaskSection(a.task(), a.taskIsRework(), a.nodeInstructionContext(), texts))
                .replace(INPUTS_SECTION_TOKEN,            buildInputsSection(a.inputs(), texts))
                .replace(EXPECTED_OUTPUTS_SECTION_TOKEN,  buildExpectedOutputsSection(a.outputFiles(), a.projectChangePaths(), a.hasProjectChanges(), texts))
                .replace(STRUCTURED_OUTPUT_SECTION_TOKEN, buildStructuredOutputSection(a.stepId(), a.attemptNo(), texts))
                .replace(FOOTER_SECTION_TOKEN,            texts.footer() + "\n");
        return normalizePrompt(rendered);
    }

    private String buildSystemIntroSection(PromptTexts texts) {
        return texts.instructionBegin() + "\n"
                + texts.systemIntro().stripTrailing() + "\n"
                + texts.instructionEnd() + "\n\n";
    }

    private String buildWorkflowProgressSection(
            List<WorkflowProgressEntry> progress, String currentStepId, int currentAttemptNo, PromptTexts texts) {
        if (progress == null || progress.isEmpty()) {
            return "";
        }
        int totalStepNo = progress.size() + 1;
        StringBuilder sb = new StringBuilder();
        sb.append(texts.workflowProgressHeader()
                .replace("{step_no}", String.valueOf(totalStepNo))
                .replace("{step_id}", currentStepId != null ? currentStepId : ""))
                .append("\n");
        for (WorkflowProgressEntry entry : progress) {
            sb.append(texts.workflowProgressStep()
                    .replace("{step_no}", String.valueOf(entry.stepNo()))
                    .replace("{step_id}", entry.stepId() != null ? entry.stepId() : "")
                    .replace("{summary}", entry.summary() != null ? entry.summary() : ""))
                    .append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String buildContextSection(String context, PromptTexts texts) {
        if (context == null) {
            return "";
        }
        return texts.contextHeader() + "\n" + context + "\n\n";
    }

    private String buildTaskSection(String task, boolean taskIsRework, String nodeInstructionContext, PromptTexts texts) {
        if (task == null && nodeInstructionContext == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (taskIsRework) {
            sb.append(texts.reworkTaskHeader()).append("\n");
            if (task != null) {
                sb.append(task).append("\n");
            }
            if (nodeInstructionContext != null) {
                sb.append("\n").append(texts.nodeInstructionContextHeader()).append("\n");
                sb.append(nodeInstructionContext).append("\n");
            }
        } else {
            sb.append(texts.taskHeader()).append("\n");
            if (task != null) {
                sb.append(task).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private String buildInputsSection(List<String> inputs, PromptTexts texts) {
        if (inputs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(texts.inputsHeader()).append("\n");
        for (String input : inputs) {
            sb.append("- ").append(input).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String buildExpectedOutputsSection(
            List<String> outputFiles, List<String> projectChangePaths, boolean hasProjectChanges, PromptTexts texts) {
        if (outputFiles.isEmpty() && !hasProjectChanges) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (!outputFiles.isEmpty()) {
            sb.append(texts.outputFilesHeader()).append("\n");
            for (String path : outputFiles) {
                sb.append("- ").append(path).append("\n");
            }
            if (hasProjectChanges) {
                sb.append("\n");
            }
        }
        if (hasProjectChanges) {
            sb.append(texts.projectChangesHeader()).append("\n");
            if (!projectChangePaths.isEmpty()) {
                for (String path : projectChangePaths) {
                    sb.append("- ").append(path).append("\n");
                }
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private String buildStructuredOutputSection(String stepId, int attemptNo, PromptTexts texts) {
        return texts.structuredOutput()
                .replace("{step_id}", stepId != null ? stepId : "")
                .replace("{attempt_no}", String.valueOf(attemptNo))
                .stripTrailing() + "\n\n";
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
                    text(root, "/sections/system_intro"),
                    text(root, "/sections/workflow_progress_header"),
                    text(root, "/sections/workflow_progress_step"),
                    text(root, "/sections/context_header"),
                    text(root, "/sections/task_header"),
                    text(root, "/sections/rework_task_header"),
                    text(root, "/sections/node_instruction_context_header"),
                    text(root, "/sections/instruction_begin"),
                    text(root, "/sections/instruction_end"),
                    text(root, "/sections/inputs_header"),
                    text(root, "/sections/output_files_header"),
                    text(root, "/sections/project_changes_header"),
                    text(root, "/sections/structured_output"),
                    text(root, "/sections/footer"),
                    text(root, "/inputs/use_upstream_artifact_by_path"),
                    text(root, "/inputs/use_upstream_artifact_by_key_and_path"),
                    text(root, "/inputs/use_upstream_artifact_by_value")
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

    private List<String> summarizePromptInputs(List<Map<String, Object>> resolvedContext, PromptTexts texts) {
        List<String> inputs = new ArrayList<>();
        for (Map<String, Object> contextEntry : resolvedContext) {
            String type = normalize(asString(contextEntry.get("type")));
            switch (type) {
                case "artifact_ref" -> {
                    String artifactKey = trimToNull(asString(contextEntry.get("artifact_key")));
                    String path = trimToNull(asString(contextEntry.get("path")));
                    String transferMode = normalize(asString(contextEntry.get("transfer_mode")));
                    if ("by_value".equals(transferMode)) {
                        String content = trimToNull(asString(contextEntry.get("content")));
                        String sizeBytes = String.valueOf(asLong(contextEntry.get("size_bytes")));
                        inputs.add(texts.useUpstreamArtifactByValue()
                                .replace("{artifact_key}", artifactKey == null ? "artifact" : artifactKey)
                                .replace("{path}", path == null ? "path is not provided" : path)
                                .replace("{size_bytes}", sizeBytes)
                                .replace("{content}", content == null ? "" : content));
                    } else {
                        inputs.add(artifactKey == null
                                ? texts.useUpstreamArtifactByPath()
                                        .replace("{path}", path == null ? "path is not provided" : path)
                                : texts.useUpstreamArtifactByKeyAndPath()
                                        .replace("{artifact_key}", artifactKey)
                                        .replace("{path}", path == null ? "path is not provided" : path));
                    }
                }
                default -> {
                }
            }
        }
        return inputs.stream().distinct().toList();
    }

    private List<String> summarizeOutputFiles(NodeModel node, NodeExecutionEntity execution) {
        List<String> outputFiles = new ArrayList<>();
        List<PathRequirement> producedArtifacts = node.getProducedArtifacts() == null
                ? List.of() : node.getProducedArtifacts();
        for (PathRequirement requirement : producedArtifacts) {
            if (requirement == null || !Boolean.TRUE.equals(requirement.getRequired())) {
                continue;
            }
            String path = trimToNull(requirement.getPath());
            if (path != null && !"project".equals(normalize(requirement.getScope()))) {
                outputFiles.add(runScopeArtifactPath(node, execution, path));
            }
        }
        return outputFiles.stream().distinct().toList();
    }

    private List<String> summarizeProjectChangePaths(NodeModel node) {
        List<String> paths = new ArrayList<>();
        List<PathRequirement> producedArtifacts = node.getProducedArtifacts() == null
                ? List.of() : node.getProducedArtifacts();
        for (PathRequirement artifact : producedArtifacts) {
            if (artifact == null || !Boolean.TRUE.equals(artifact.getRequired())) {
                continue;
            }
            if ("project".equals(normalize(artifact.getScope()))) {
                String path = trimToNull(artifact.getPath());
                if (path != null) {
                    paths.add(path);
                }
            }
        }
        List<PathRequirement> mutations = node.getExpectedMutations();
        if (mutations != null) {
            for (PathRequirement mutation : mutations) {
                if (mutation == null || !Boolean.TRUE.equals(mutation.getRequired())) {
                    continue;
                }
                String path = trimToNull(mutation.getPath());
                if (path != null) {
                    paths.add(path);
                }
            }
        }
        return paths.stream().distinct().toList();
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

    private String runScopeArtifactPath(NodeModel node, NodeExecutionEntity execution, String artifactPath) {
        String normalized = artifactPath.replace('\\', '/');
        return ".hgsdlc/nodes/" + node.getId() + "/attempt-" + execution.getAttemptNo() + "/" + normalized;
    }

    private boolean isStartNode(FlowModel flowModel, NodeModel node) {
        return flowModel != null
                && node != null
                && flowModel.getStartNodeId() != null
                && flowModel.getStartNodeId().equals(node.getId());
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

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    public record AgentPromptPackage(
            AgentInput agentInput,
            String prompt,
            String promptChecksum
    ) {}

    public record AgentInput(
            boolean startNode,
            String context,
            String task,
            boolean taskIsRework,
            String nodeInstructionContext,
            List<String> inputs,
            List<String> outputFiles,
            List<String> projectChangePaths,
            boolean hasProjectChanges,
            List<WorkflowProgressEntry> workflowProgress,
            String stepId,
            int attemptNo
    ) {}

    public record WorkflowProgressEntry(int stepNo, String stepId, String summary) {}

    private record PromptTexts(
            String systemIntro,
            String workflowProgressHeader,
            String workflowProgressStep,
            String contextHeader,
            String taskHeader,
            String reworkTaskHeader,
            String nodeInstructionContextHeader,
            String instructionBegin,
            String instructionEnd,
            String inputsHeader,
            String outputFilesHeader,
            String projectChangesHeader,
            String structuredOutput,
            String footer,
            String useUpstreamArtifactByPath,
            String useUpstreamArtifactByKeyAndPath,
            String useUpstreamArtifactByValue
    ) {}
}

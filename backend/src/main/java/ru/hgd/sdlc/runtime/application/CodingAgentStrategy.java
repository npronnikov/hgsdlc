package ru.hgd.sdlc.runtime.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import lombok.RequiredArgsConstructor;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;
import ru.hgd.sdlc.runtime.application.port.WorkspacePort;
import ru.hgd.sdlc.runtime.domain.ActorType;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.settings.application.SettingsService;
import ru.hgd.sdlc.skill.domain.SkillFileEntity;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.infrastructure.SkillFileRepository;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

@RequiredArgsConstructor
public abstract class CodingAgentStrategy {
    private final RuleVersionRepository ruleVersionRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final RuntimeStepTxService runtimeStepTxService;
    private final AgentPromptBuilder agentPromptBuilder;
    private final CatalogContentResolver catalogContentResolver;
    private final WorkspacePort workspacePort;
    private final SettingsService settingsService;

    public abstract String codingAgent();

    public record MaterializationRequest(
        RunEntity run,
        FlowModel flowModel,
        NodeModel node,
        NodeExecutionEntity execution,
        List<Map<String, Object>> resolvedContext,
        Path projectRoot,
        Path nodeExecutionRoot,
        List<AgentPromptBuilder.WorkflowProgressEntry> workflowProgress,
        AgentSessionCommandMode sessionCommandMode,
        String sessionId
    ) {}

    public AgentInvocationContext materializeWorkspace(MaterializationRequest request) throws CodingAgentException {
        RunEntity run = request.run();
        FlowModel flowModel = request.flowModel();
        NodeModel node = request.node();
        Path projectRoot = request.projectRoot();
        Path nodeExecutionRoot = request.nodeExecutionRoot();

        Path root = projectRoot.resolve("." + codingAgent());
        Path rulesPath = projectRoot.resolve(codingAgent().toUpperCase() + ".md");
        Path skillsRoot = root.resolve("skills");
        Path promptPath = nodeExecutionRoot.resolve("prompt.md");
        Path stdoutPath = nodeExecutionRoot.resolve("agent.stdout.log");
        Path stderrPath = nodeExecutionRoot.resolve("agent.stderr.log");

        createDirectories(root);
        createDirectories(skillsRoot);
        ensureGitIgnoreContains(projectRoot, codingAgent().toUpperCase() + ".md");
        deleteDirectoryContents(skillsRoot);

        List<RuleVersion> rules = resolveFlowRules(flowModel);
        List<SkillVersion> skills = resolveNodeSkills(flowModel, node);
        boolean autoInitWithoutRules = shouldAutoInitWhenNoRule(flowModel, rules);

        if (!autoInitWithoutRules) {
            writeFile(rulesPath, renderRules(flowModel, rules).getBytes(StandardCharsets.UTF_8));
        }
        runtimeStepTxService.appendAudit(
            run.getId(),
            request.execution().getId(),
            null,
            "rules_materialized",
            ActorType.SYSTEM,
            "runtime",
            mapOf(
                "path", rulesPath.toString(),
                "rule_refs", flowModel.getRuleRefs() == null ? List.of() : flowModel.getRuleRefs(),
                "materialization_mode", autoInitWithoutRules ? "agent_init" : "flow_rules"
            )
        );

        for (SkillVersion skill : skills) {
            Path skillDir = skillsRoot.resolve(skill.getCanonicalName());
            createDirectories(skillDir);
            List<SkillFileEntity> files = skillFileRepository.findBySkillVersionIdOrderByPathAsc(skill.getId());
            if (files.isEmpty()) {
                Path skillFile = skillDir.resolve("SKILL.md");
                writeFile(skillFile, catalogContentResolver.resolveSkillMarkdown(skill).getBytes(StandardCharsets.UTF_8));
                continue;
            }
            for (SkillFileEntity file : files) {
                Path filePath = skillDir.resolve(file.getPath()).normalize();
                if (!filePath.startsWith(skillDir)) {
                    throw new CodingAgentException("SKILL_PACKAGE_PATH_INVALID", "Skill file path escapes package root: " + file.getPath());
                }
                createDirectories(filePath.getParent());
                writeFile(filePath, file.getTextContent().getBytes(StandardCharsets.UTF_8));
                if (file.isExecutable()) {
                    setExecutable(filePath);
                }
            }
        }
        runtimeStepTxService.appendAudit(
            run.getId(),
            request.execution().getId(),
            null,
            "skills_materialized",
            ActorType.SYSTEM,
            "runtime",
            mapOf(
                "skills_root", skillsRoot.toString(),
                "skill_refs", node.getSkillRefs() == null ? List.of() : node.getSkillRefs()
            )
        );

        AgentPromptBuilder.AgentPromptPackage promptPackage = agentPromptBuilder.build(
            run,
            flowModel,
            node,
            request.execution(),
            request.resolvedContext(),
            request.workflowProgress()
        );
        writeFile(promptPath, promptPackage.prompt().getBytes(StandardCharsets.UTF_8));

        String launchCommand = resolveLaunchCommand(
            promptPackage.prompt(),
            promptPath,
            projectRoot,
            request.sessionCommandMode(),
            request.sessionId()
        );
        String commandScript = launchCommand;
        if (autoInitWithoutRules) {
            String initCommand = resolveInitCommand(promptPackage.prompt(), promptPath, projectRoot);
            commandScript = initCommand + " && " + launchCommand;
        }
        List<String> command = List.of("bash", "-lc", commandScript);

        return new AgentInvocationContext(
            projectRoot,
            command,
            promptPath,
            rulesPath,
            skillsRoot,
            stdoutPath,
            stderrPath,
            promptPackage
        );
    }

    private List<RuleVersion> resolveFlowRules(FlowModel flowModel) throws CodingAgentException {
        String codingAgent = normalize(flowModel.getCodingAgent());
        List<String> refs = flowModel.getRuleRefs() == null ? List.of() : flowModel.getRuleRefs();
        List<RuleVersion> rules = new ArrayList<>();
        for (String ref : refs) {
            if (ref == null || ref.isBlank()) {
                continue;
            }
            RuleVersion rule = ruleVersionRepository.findFirstByCanonicalName(ref)
                .orElseThrow(() -> new CodingAgentException(
                    "RULE_NOT_FOUND",
                    "Rule not found: " + ref
                ));
            if (!codingAgent.equals(normalize(rule.getCodingAgent().name()))) {
                throw new CodingAgentException(
                    "RULE_PROVIDER_MISMATCH",
                    "Rule provider must match flow coding_agent " + codingAgent + ": " + ref
                );
            }
            rules.add(rule);
        }
        return rules;
    }

    private List<SkillVersion> resolveNodeSkills(FlowModel flowModel, NodeModel node) throws CodingAgentException {
        String codingAgent = normalize(flowModel.getCodingAgent());
        List<String> refs = node.getSkillRefs() == null ? List.of() : node.getSkillRefs();
        List<SkillVersion> skills = new ArrayList<>();
        for (String ref : refs) {
            if (ref == null || ref.isBlank()) {
                continue;
            }
            SkillVersion skill = skillVersionRepository.findFirstByCanonicalName(ref)
                .orElseThrow(() -> new CodingAgentException(
                    "SKILL_NOT_FOUND",
                    "Skill not found: " + ref
                ));
            if (!codingAgent.equals(normalize(skill.getCodingAgent().name()))) {
                throw new CodingAgentException(
                    "SKILL_PROVIDER_MISMATCH",
                    "Skill provider must match flow coding_agent " + codingAgent + ": " + ref
                );
            }
            skills.add(skill);
        }
        return skills;
    }

    private String renderRules(FlowModel flowModel, List<RuleVersion> rules) {
        StringBuilder sb = new StringBuilder();
        sb.append("# HGSDLC Runtime Rules\n\n");
        sb.append("flow: ").append(flowModel.getCanonicalName() == null ? flowModel.getId() : flowModel.getCanonicalName()).append("\n");
        sb.append("coding_agent: ").append(flowModel.getCodingAgent()).append("\n\n");
        if (rules.isEmpty()) {
            sb.append("No flow-level rules provided.\n");
            return sb.toString();
        }
        for (RuleVersion rule : rules) {
            sb.append("## ").append(rule.getCanonicalName()).append("\n\n");
            sb.append(catalogContentResolver.resolveRuleMarkdown(rule)).append("\n\n");
        }
        return sb.toString();
    }

    private boolean shouldAutoInitWhenNoRule(FlowModel flowModel, List<RuleVersion> resolvedRules) {
        if (!settingsService.isRuntimeAgentAutoInitWhenNoRule(codingAgent())) {
            return false;
        }
        if (resolvedRules != null && !resolvedRules.isEmpty()) {
            return false;
        }
        List<String> refs = flowModel.getRuleRefs();
        if (refs == null || refs.isEmpty()) {
            return true;
        }
        return refs.stream().noneMatch((ref) -> ref != null && !ref.isBlank());
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private Map<String, Object> mapOf(Object... keyValues) {
        if (keyValues == null || keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Invalid payload map");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private void createDirectories(Path directory) throws CodingAgentException {
        if (directory == null) {
            return;
        }
        try {
            workspacePort.createDirectories(directory);
        } catch (IOException ex) {
            throw new CodingAgentException("AGENT_WORKSPACE_FAILED", "Failed to create directory: " + directory);
        }
    }

    private void writeFile(Path path, byte[] content) throws CodingAgentException {
        try {
            if (path.getParent() != null) {
                workspacePort.createDirectories(path.getParent());
            }
            workspacePort.write(path, content == null ? new byte[0] : content);
        } catch (IOException ex) {
            throw new CodingAgentException("AGENT_WORKSPACE_FAILED", "Failed to write file: " + path);
        }
    }

    private void ensureGitIgnoreContains(Path projectRoot, String entry) throws CodingAgentException {
        if (projectRoot == null || entry == null || entry.isBlank()) {
            return;
        }
        Path gitIgnorePath = projectRoot.resolve(".gitignore");
        try {
            String content = workspacePort.exists(gitIgnorePath)
                ? workspacePort.readString(gitIgnorePath, StandardCharsets.UTF_8)
                : "";
            if (hasGitIgnoreEntry(content, entry)) {
                return;
            }
            StringBuilder updated = new StringBuilder(content == null ? "" : content);
            if (!updated.isEmpty() && updated.charAt(updated.length() - 1) != '\n') {
                updated.append('\n');
            }
            updated.append(entry).append('\n');
            workspacePort.writeString(gitIgnorePath, updated.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new CodingAgentException("AGENT_WORKSPACE_FAILED", "Failed to update .gitignore: " + gitIgnorePath);
        }
    }

    private boolean hasGitIgnoreEntry(String content, String entry) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String normalized = line == null ? "" : line.trim();
            if (normalized.equals(entry) || normalized.equals("/" + entry)) {
                return true;
            }
        }
        return false;
    }

    private void setExecutable(Path path) throws CodingAgentException {
        if (path == null) {
            return;
        }
        try {
            path.toFile().setExecutable(true, true);
        } catch (RuntimeException ex) {
            throw new CodingAgentException("AGENT_WORKSPACE_FAILED", "Failed to set executable bit: " + path);
        }
    }

    private String resolveLaunchCommand(
        String prompt,
        Path promptPath,
        Path projectRoot,
        AgentSessionCommandMode sessionCommandMode,
        String sessionId
    ) {
        String template = settingsService.getRuntimeAgentLaunchCommand(codingAgent());
        String baseCommand = applyCommandTemplate(template, prompt, promptPath, projectRoot);
        return appendSessionCommand(baseCommand, sessionCommandMode, sessionId);
    }

    protected String appendSessionCommand(String baseCommand, AgentSessionCommandMode mode, String sessionId) {
        if (mode == null || sessionId == null || sessionId.isBlank()) {
            return baseCommand;
        }
        if (mode == AgentSessionCommandMode.RESUME_PREVIOUS_SESSION) {
            return baseCommand + " --resume " + shellQuote(sessionId);
        }
        return baseCommand + " --session-id " + shellQuote(sessionId);
    }

    private String resolveInitCommand(String prompt, Path promptPath, Path projectRoot) {
        String template = settingsService.getRuntimeAgentInitCommand(codingAgent());
        return applyCommandTemplate(template, prompt, promptPath, projectRoot);
    }

    private String applyCommandTemplate(String template, String prompt, Path promptPath, Path projectRoot) {
        return template
            .replace("{{PROMPT_FILE}}", shellQuote(promptPath == null ? "" : promptPath.toString()))
            .replace("{{PROMPT}}", shellQuote(prompt))
            .replace("{{PROJECT_ROOT}}", shellQuote(projectRoot == null ? "" : projectRoot.toString()));
    }

    protected String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private void deleteDirectoryContents(Path directory) throws CodingAgentException {
        if (directory == null || !workspacePort.exists(directory)) {
            return;
        }
        try {
            for (Path path : workspacePort.listDescendantsReverse(directory)) {
                workspacePort.deleteIfExists(path);
            }
        } catch (IOException ex) {
            throw new CodingAgentException("AGENT_WORKSPACE_FAILED", "Failed to clean directory: " + directory);
        }
    }

}

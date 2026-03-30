package ru.hgd.sdlc.runtime.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import org.springframework.stereotype.Component;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.flow.domain.FlowVersion;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.runtime.application.port.WorkspacePort;
import ru.hgd.sdlc.settings.application.SettingsService;
import ru.hgd.sdlc.skill.domain.SkillVersion;

@Component
public class CatalogContentResolver {
    private final SettingsService settingsService;
    private final WorkspacePort workspacePort;

    public CatalogContentResolver(SettingsService settingsService, WorkspacePort workspacePort) {
        this.settingsService = settingsService;
        this.workspacePort = workspacePort;
    }

    public String resolveFlowYaml(FlowVersion flowVersion) {
        if (flowVersion == null) {
            throw new ValidationException("Flow version is required");
        }
        String fallbackPath = "flows/" + flowVersion.getFlowId() + "/" + flowVersion.getVersion();
        return readFromMirror(resolveSourcePath(flowVersion.getSourcePath(), fallbackPath), "FLOW.yaml");
    }

    public String resolveRuleMarkdown(RuleVersion ruleVersion) {
        if (ruleVersion == null) {
            throw new ValidationException("Rule version is required");
        }
        String fallbackPath = "rules/" + ruleVersion.getRuleId() + "/" + ruleVersion.getVersion();
        return readFromMirror(resolveSourcePath(ruleVersion.getSourcePath(), fallbackPath), "RULE.md");
    }

    public String resolveSkillMarkdown(SkillVersion skillVersion) {
        if (skillVersion == null) {
            throw new ValidationException("Skill version is required");
        }
        String fallbackPath = "skills/" + skillVersion.getSkillId() + "/" + skillVersion.getVersion();
        return readFromMirror(resolveSourcePath(skillVersion.getSourcePath(), fallbackPath), "SKILL.md");
    }

    private String readFromMirror(String sourcePath, String defaultFileName) {
        String repoUrl = settingsService.getCatalogRepoUrl();
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new ValidationException("catalog_repo_url is required for git content source");
        }
        Path mirrorRoot = resolveCatalogMirrorPath(settingsService.getWorkspaceRoot(), repoUrl);
        Path candidate = mirrorRoot.resolve(sourcePath).normalize();
        if (workspacePort.isDirectory(candidate)) {
            candidate = candidate.resolve(defaultFileName);
        }
        if (!candidate.isAbsolute()) {
            candidate = mirrorRoot.resolve(candidate).normalize();
        }
        if (!candidate.startsWith(mirrorRoot)) {
            throw new ValidationException("source_path points outside local catalog repo: " + sourcePath);
        }
        if (!workspacePort.exists(candidate)) {
            throw new ValidationException("Git content file not found: " + candidate);
        }
        try {
            return workspacePort.readString(candidate, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ValidationException("Failed to read git content file: " + candidate + " (" + ex.getMessage() + ")");
        }
    }

    private String resolveSourcePath(String sourcePath, String fallbackPath) {
        if (sourcePath != null && !sourcePath.isBlank()) {
            return sourcePath;
        }
        if (fallbackPath == null || fallbackPath.isBlank()) {
            throw new ValidationException("source_path is required");
        }
        return fallbackPath;
    }

    private Path resolveCatalogMirrorPath(String workspaceRoot, String repoUrl) {
        Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        String suffix = Integer.toHexString(repoUrl.toLowerCase(Locale.ROOT).hashCode());
        return root.resolve(".catalog-repo").resolve(suffix);
    }
}

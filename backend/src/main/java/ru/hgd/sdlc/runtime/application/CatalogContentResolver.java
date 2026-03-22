package ru.hgd.sdlc.runtime.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import org.springframework.stereotype.Component;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.flow.domain.FlowContentSource;
import ru.hgd.sdlc.flow.domain.FlowVersion;
import ru.hgd.sdlc.rule.domain.RuleContentSource;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.settings.application.SettingsService;
import ru.hgd.sdlc.skill.domain.SkillContentSource;
import ru.hgd.sdlc.skill.domain.SkillVersion;

@Component
class CatalogContentResolver {
    private final SettingsService settingsService;

    CatalogContentResolver(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    String resolveFlowYaml(FlowVersion flowVersion) {
        if (flowVersion == null) {
            throw new ValidationException("Flow version is required");
        }
        if (flowVersion.getContentSource() == FlowContentSource.GIT) {
            return readFromMirror(flowVersion.getSourcePath(), "FLOW.yaml");
        }
        return flowVersion.getFlowYaml();
    }

    String resolveRuleMarkdown(RuleVersion ruleVersion) {
        if (ruleVersion == null) {
            throw new ValidationException("Rule version is required");
        }
        if (ruleVersion.getContentSource() == RuleContentSource.GIT) {
            return readFromMirror(ruleVersion.getSourcePath(), "RULE.md");
        }
        return ruleVersion.getRuleMarkdown();
    }

    String resolveSkillMarkdown(SkillVersion skillVersion) {
        if (skillVersion == null) {
            throw new ValidationException("Skill version is required");
        }
        if (skillVersion.getContentSource() == SkillContentSource.GIT) {
            return readFromMirror(skillVersion.getSourcePath(), "SKILL.md");
        }
        return skillVersion.getSkillMarkdown();
    }

    private String readFromMirror(String sourcePath, String defaultFileName) {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new ValidationException("source_path is required for git content source");
        }
        String repoUrl = settingsService.getCatalogRepoUrl();
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new ValidationException("catalog_repo_url is required for git content source");
        }
        Path mirrorRoot = resolveCatalogMirrorPath(settingsService.getWorkspaceRoot(), repoUrl);
        Path candidate = mirrorRoot.resolve(sourcePath).normalize();
        if (Files.isDirectory(candidate)) {
            candidate = candidate.resolve(defaultFileName);
        }
        if (!candidate.isAbsolute()) {
            candidate = mirrorRoot.resolve(candidate).normalize();
        }
        if (!candidate.startsWith(mirrorRoot)) {
            throw new ValidationException("source_path points outside catalog mirror: " + sourcePath);
        }
        if (!Files.exists(candidate)) {
            throw new ValidationException("Git content file not found: " + candidate);
        }
        try {
            return Files.readString(candidate, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ValidationException("Failed to read git content file: " + candidate + " (" + ex.getMessage() + ")");
        }
    }

    private Path resolveCatalogMirrorPath(String workspaceRoot, String repoUrl) {
        Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        String suffix = Integer.toHexString(repoUrl.toLowerCase(Locale.ROOT).hashCode());
        return root.resolve(".catalog-mirror").resolve(suffix);
    }
}

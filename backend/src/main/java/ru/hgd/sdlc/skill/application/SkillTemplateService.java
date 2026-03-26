package ru.hgd.sdlc.skill.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.skill.domain.SkillProvider;

@Service
public class SkillTemplateService {
    private final Map<SkillProvider, List<FrontmatterField>> summaryByProvider;
    private final Map<SkillProvider, List<String>> requiredFrontmatter;

    public SkillTemplateService() {
        this.summaryByProvider = buildSummary();
        this.requiredFrontmatter = buildRequiredFrontmatter();
    }

    public SkillTemplate loadTemplate(SkillProvider provider) {
        String fileName = provider.name().toLowerCase().replace('_', '-') + ".md";
        ClassPathResource resource = new ClassPathResource("skill-templates/" + fileName);
        if (!resource.exists()) {
            throw new NotFoundException("Skill template not found for provider: " + provider.name().toLowerCase());
        }
        try {
            String template = resource.getContentAsString(StandardCharsets.UTF_8);
            List<FrontmatterField> summary = summaryByProvider.getOrDefault(provider, List.of());
            return new SkillTemplate(provider, template, summary);
        } catch (IOException ex) {
            throw new NotFoundException("Failed to read skill template for provider: " + provider.name().toLowerCase());
        }
    }

    public List<String> requiredFrontmatter(SkillProvider provider) {
        return requiredFrontmatter.getOrDefault(provider, List.of());
    }

    private Map<SkillProvider, List<FrontmatterField>> buildSummary() {
        Map<SkillProvider, List<FrontmatterField>> summary = new EnumMap<>(SkillProvider.class);
        summary.put(SkillProvider.QWEN, List.of(
                new FrontmatterField("name", "Skill name"),
                new FrontmatterField("description", "What the skill does and when to use it")
        ));
        summary.put(SkillProvider.CLAUDE, List.of(
                new FrontmatterField("name", "Skill name"),
                new FrontmatterField("description", "What the skill does and when to use it"),
                new FrontmatterField("argument-hint", "Argument hint"),
                new FrontmatterField("disable-model-invocation", "Disable automatic model invocation"),
                new FrontmatterField("user-invocable", "Show skill in menu"),
                new FrontmatterField("allowed-tools", "Allowed tools"),
                new FrontmatterField("model", "Model for the skill"),
                new FrontmatterField("context", "Execution context (for example, fork)"),
                new FrontmatterField("agent", "Subagent type"),
                new FrontmatterField("hooks", "Lifecycle hooks")
        ));
        return summary;
    }

    private Map<SkillProvider, List<String>> buildRequiredFrontmatter() {
        Map<SkillProvider, List<String>> required = new EnumMap<>(SkillProvider.class);
        required.put(SkillProvider.QWEN, List.of("name", "description"));
        required.put(SkillProvider.CLAUDE, List.of());
        return required;
    }

    public record SkillTemplate(
            SkillProvider provider,
            String template,
            List<FrontmatterField> frontmatterSummary
    ) {}

    public record FrontmatterField(String field, String meaning) {}
}

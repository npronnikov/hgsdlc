package ru.hgd.sdlc.rule.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.rule.domain.RuleProvider;

@Service
public class RuleTemplateService {
    private final Map<RuleProvider, List<FrontmatterField>> summaryByProvider;
    private final Map<RuleProvider, List<String>> requiredFrontmatter;

    public RuleTemplateService() {
        this.summaryByProvider = buildSummary();
        this.requiredFrontmatter = buildRequiredFrontmatter();
    }

    public RuleTemplate loadTemplate(RuleProvider provider) {
        String fileName = provider.name().toLowerCase().replace('_', '-') + ".md";
        ClassPathResource resource = new ClassPathResource("rule-templates/" + fileName);
        if (!resource.exists()) {
            throw new NotFoundException("Rule template not found for provider: " + provider.name().toLowerCase());
        }
        try {
            String template = resource.getContentAsString(StandardCharsets.UTF_8);
            List<FrontmatterField> summary = summaryByProvider.getOrDefault(provider, List.of());
            return new RuleTemplate(provider, template, summary);
        } catch (IOException ex) {
            throw new NotFoundException("Failed to read rule template for provider: " + provider.name().toLowerCase());
        }
    }

    public List<String> requiredFrontmatter(RuleProvider provider) {
        return requiredFrontmatter.getOrDefault(provider, List.of());
    }

    private Map<RuleProvider, List<FrontmatterField>> buildSummary() {
        Map<RuleProvider, List<FrontmatterField>> summary = new EnumMap<>(RuleProvider.class);
        summary.put(RuleProvider.CLAUDE, List.of(
                new FrontmatterField("paths", "Paths where this rule applies")
        ));
        summary.put(RuleProvider.CURSOR, List.of(
                new FrontmatterField("description", "Short rule purpose"),
                new FrontmatterField("globs", "File patterns where the rule applies"),
                new FrontmatterField("alwaysApply", "Whether to always apply the rule")
        ));
        return summary;
    }

    private Map<RuleProvider, List<String>> buildRequiredFrontmatter() {
        Map<RuleProvider, List<String>> required = new EnumMap<>(RuleProvider.class);
        required.put(RuleProvider.QWEN, List.of());
        required.put(RuleProvider.CLAUDE, List.of());
        required.put(RuleProvider.CURSOR, List.of("description", "globs", "alwaysApply"));
        return required;
    }

    public record RuleTemplate(
            RuleProvider provider,
            String template,
            List<FrontmatterField> frontmatterSummary
    ) {}

    public record FrontmatterField(String field, String meaning) {}
}

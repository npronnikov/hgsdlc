package ru.hgd.sdlc.rule.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import ru.hgd.sdlc.rule.application.RuleTemplateService;

public record RuleTemplateResponse(
        @JsonProperty("provider") String provider,
        @JsonProperty("template") String template,
        @JsonProperty("frontmatterSummary") List<FrontmatterField> frontmatterSummary
) {
    public static RuleTemplateResponse from(RuleTemplateService.RuleTemplate template) {
        List<FrontmatterField> summary = template.frontmatterSummary().stream()
                .map(field -> new FrontmatterField(field.field(), field.meaning()))
                .toList();
        return new RuleTemplateResponse(
                template.provider().name().toLowerCase().replace('_', '-'),
                template.template(),
                summary
        );
    }

    public record FrontmatterField(
            @JsonProperty("field") String field,
            @JsonProperty("meaning") String meaning
    ) {}
}

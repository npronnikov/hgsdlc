package ru.hgd.sdlc.skill.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import ru.hgd.sdlc.skill.application.SkillTemplateService;

public record SkillTemplateResponse(
        @JsonProperty("provider") String provider,
        @JsonProperty("template") String template,
        @JsonProperty("frontmatterSummary") List<FrontmatterField> frontmatterSummary
) {
    public static SkillTemplateResponse from(SkillTemplateService.SkillTemplate template) {
        List<FrontmatterField> summary = template.frontmatterSummary().stream()
                .map(field -> new FrontmatterField(field.field(), field.meaning()))
                .toList();
        return new SkillTemplateResponse(
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

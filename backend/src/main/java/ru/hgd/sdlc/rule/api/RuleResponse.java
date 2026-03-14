package ru.hgd.sdlc.rule.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import ru.hgd.sdlc.rule.domain.RuleVersion;

public record RuleResponse(
        @JsonProperty("rule_id") String ruleId,
        @JsonProperty("title") String title,
        @JsonProperty("coding_agent") String codingAgent,
        @JsonProperty("version") String version,
        @JsonProperty("canonical_name") String canonicalName,
        @JsonProperty("status") String status,
        @JsonProperty("rule_markdown") String ruleMarkdown,
        @JsonProperty("saved_by") String savedBy,
        @JsonProperty("saved_at") Instant savedAt,
        @JsonProperty("resource_version") long resourceVersion
) {
    public static RuleResponse from(RuleVersion version) {
        return new RuleResponse(
                version.getRuleId(),
                version.getTitle(),
                version.getCodingAgent() == null ? null : version.getCodingAgent().name().toLowerCase().replace('_', '-'),
                version.getVersion(),
                version.getCanonicalName(),
                version.getStatus().name().toLowerCase(),
                version.getRuleMarkdown(),
                version.getSavedBy(),
                version.getSavedAt(),
                version.getResourceVersion()
        );
    }
}

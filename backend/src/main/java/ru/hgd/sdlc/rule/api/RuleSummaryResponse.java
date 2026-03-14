package ru.hgd.sdlc.rule.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import ru.hgd.sdlc.rule.domain.RuleVersion;

public record RuleSummaryResponse(
        @JsonProperty("rule_id") String ruleId,
        @JsonProperty("title") String title,
        @JsonProperty("provider") String provider,
        @JsonProperty("version") String version,
        @JsonProperty("canonical_name") String canonicalName,
        @JsonProperty("status") String status,
        @JsonProperty("saved_by") String savedBy,
        @JsonProperty("saved_at") Instant savedAt,
        @JsonProperty("resource_version") long resourceVersion
) {
    public static RuleSummaryResponse from(RuleVersion version) {
        return new RuleSummaryResponse(
                version.getRuleId(),
                version.getTitle(),
                version.getProvider() == null ? null : version.getProvider().name().toLowerCase().replace('_', '-'),
                version.getVersion(),
                version.getCanonicalName(),
                version.getStatus().name().toLowerCase(),
                version.getSavedBy(),
                version.getSavedAt(),
                version.getResourceVersion()
        );
    }
}

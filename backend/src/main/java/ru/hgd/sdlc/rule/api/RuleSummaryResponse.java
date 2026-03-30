package ru.hgd.sdlc.rule.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import ru.hgd.sdlc.rule.domain.RuleVersion;

public record RuleSummaryResponse(
        @JsonProperty("rule_id") String ruleId,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("coding_agent") String codingAgent,
        @JsonProperty("team_code") String teamCode,
        @JsonProperty("platform_code") String platformCode,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("rule_kind") String ruleKind,
        @JsonProperty("scope") String scope,
        @JsonProperty("environment") String environment,
        @JsonProperty("approval_status") String approvalStatus,
        @JsonProperty("content_source") String contentSource,
        @JsonProperty("visibility") String visibility,
        @JsonProperty("lifecycle_status") String lifecycleStatus,
        @JsonProperty("publication_status") String publicationStatus,
        @JsonProperty("publication_target") String publicationTarget,
        @JsonProperty("published_pr_url") String publishedPrUrl,
        @JsonProperty("forked_from") String forkedFrom,
        @JsonProperty("forked_by") String forkedBy,
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
                version.getDescription(),
                version.getCodingAgent() == null ? null : version.getCodingAgent().name().toLowerCase().replace('_', '-'),
                version.getTeamCode(),
                version.getPlatformCode(),
                version.getTags(),
                version.getRuleKind(),
                version.getScope(),
                version.getEnvironment() == null ? null : version.getEnvironment().name().toLowerCase(),
                version.getApprovalStatus() == null ? null : version.getApprovalStatus().name().toLowerCase(),
                version.getContentSource() == null ? null : version.getContentSource().name().toLowerCase(),
                version.getVisibility() == null ? null : version.getVisibility().name().toLowerCase(),
                version.getLifecycleStatus() == null ? null : version.getLifecycleStatus().name().toLowerCase(),
                version.getPublicationStatus() == null ? null : version.getPublicationStatus().name().toLowerCase(),
                version.getPublicationTarget() == null ? null : version.getPublicationTarget().name().toLowerCase(),
                version.getPublishedPrUrl(),
                version.getForkedFrom(),
                version.getForkedBy(),
                version.getVersion(),
                version.getCanonicalName(),
                version.getStatus().name().toLowerCase(),
                version.getSavedBy(),
                version.getSavedAt(),
                version.getResourceVersion()
        );
    }
}

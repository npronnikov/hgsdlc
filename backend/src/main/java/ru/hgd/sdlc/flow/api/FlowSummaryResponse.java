package ru.hgd.sdlc.flow.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import ru.hgd.sdlc.flow.domain.FlowVersion;

public record FlowSummaryResponse(
        @JsonProperty("flow_id") String flowId,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("start_node_id") String startNodeId,
        @JsonProperty("node_count") Integer nodeCount,
        @JsonProperty("rule_refs") List<String> ruleRefs,
        @JsonProperty("coding_agent") String codingAgent,
        @JsonProperty("team_code") String teamCode,
        @JsonProperty("platform_code") String platformCode,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("flow_kind") String flowKind,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("scope") String scope,
        @JsonProperty("lifecycle_status") String lifecycleStatus,
        @JsonProperty("publication_status") String publicationStatus,
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
    public static FlowSummaryResponse from(FlowVersion version) {
        return new FlowSummaryResponse(
                version.getFlowId(),
                version.getTitle(),
                version.getDescription(),
                version.getStartNodeId(),
                null,
                version.getRuleRefs(),
                version.getCodingAgent(),
                version.getTeamCode(),
                version.getPlatformCode(),
                version.getTags(),
                version.getFlowKind(),
                version.getRiskLevel(),
                version.getScope(),
                version.getLifecycleStatus() == null ? null : version.getLifecycleStatus().name().toLowerCase(),
                version.getPublicationStatus() == null ? null : version.getPublicationStatus().name().toLowerCase(),
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

    public static FlowSummaryResponse from(FlowVersion version, Integer nodeCount) {
        return new FlowSummaryResponse(
                version.getFlowId(),
                version.getTitle(),
                version.getDescription(),
                version.getStartNodeId(),
                nodeCount,
                version.getRuleRefs(),
                version.getCodingAgent(),
                version.getTeamCode(),
                version.getPlatformCode(),
                version.getTags(),
                version.getFlowKind(),
                version.getRiskLevel(),
                version.getScope(),
                version.getLifecycleStatus() == null ? null : version.getLifecycleStatus().name().toLowerCase(),
                version.getPublicationStatus() == null ? null : version.getPublicationStatus().name().toLowerCase(),
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

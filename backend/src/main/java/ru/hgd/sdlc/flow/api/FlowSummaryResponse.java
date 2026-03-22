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
        @JsonProperty("rule_refs") List<String> ruleRefs,
        @JsonProperty("coding_agent") String codingAgent,
        @JsonProperty("team_code") String teamCode,
        @JsonProperty("platform_code") String platformCode,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("flow_kind") String flowKind,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("environment") String environment,
        @JsonProperty("approval_status") String approvalStatus,
        @JsonProperty("content_source") String contentSource,
        @JsonProperty("visibility") String visibility,
        @JsonProperty("lifecycle_status") String lifecycleStatus,
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
                version.getRuleRefs(),
                version.getCodingAgent(),
                version.getTeamCode(),
                version.getPlatformCode(),
                version.getTags(),
                version.getFlowKind(),
                version.getRiskLevel(),
                version.getEnvironment() == null ? null : version.getEnvironment().name().toLowerCase(),
                version.getApprovalStatus() == null ? null : version.getApprovalStatus().name().toLowerCase(),
                version.getContentSource() == null ? null : version.getContentSource().name().toLowerCase(),
                version.getVisibility() == null ? null : version.getVisibility().name().toLowerCase(),
                version.getLifecycleStatus() == null ? null : version.getLifecycleStatus().name().toLowerCase(),
                version.getVersion(),
                version.getCanonicalName(),
                version.getStatus().name().toLowerCase(),
                version.getSavedBy(),
                version.getSavedAt(),
                version.getResourceVersion()
        );
    }
}

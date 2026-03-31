package ru.hgd.sdlc.flow.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.FlowVersion;

public record FlowResponse(
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
        @JsonProperty("scope") String scope,
        @JsonProperty("published_at") Instant publishedAt,
        @JsonProperty("source_ref") String sourceRef,
        @JsonProperty("source_path") String sourcePath,
        @JsonProperty("lifecycle_status") String lifecycleStatus,
        @JsonProperty("publication_status") String publicationStatus,
        @JsonProperty("published_commit_sha") String publishedCommitSha,
        @JsonProperty("published_pr_url") String publishedPrUrl,
        @JsonProperty("last_publish_error") String lastPublishError,
        @JsonProperty("forked_from") String forkedFrom,
        @JsonProperty("forked_by") String forkedBy,
        @JsonProperty("fail_on_missing_declared_output") Boolean failOnMissingDeclaredOutput,
        @JsonProperty("fail_on_missing_expected_mutation") Boolean failOnMissingExpectedMutation,
        @JsonProperty("response_schema") JsonNode responseSchema,
        @JsonProperty("version") String version,
        @JsonProperty("canonical_name") String canonicalName,
        @JsonProperty("status") String status,
        @JsonProperty("flow_yaml") String flowYaml,
        @JsonProperty("saved_by") String savedBy,
        @JsonProperty("saved_at") Instant savedAt,
        @JsonProperty("resource_version") long resourceVersion
) {
    public static FlowResponse from(FlowVersion version, FlowModel model) {
        return new FlowResponse(
                version.getFlowId(),
                model.getTitle(),
                model.getDescription(),
                model.getStartNodeId(),
                model.getRuleRefs(),
                version.getCodingAgent(),
                version.getTeamCode(),
                version.getPlatformCode(),
                version.getTags(),
                version.getFlowKind(),
                version.getRiskLevel(),
                version.getScope(),
                version.getPublishedAt(),
                version.getSourceRef(),
                version.getSourcePath(),
                version.getLifecycleStatus() == null ? null : version.getLifecycleStatus().name().toLowerCase(),
                version.getPublicationStatus() == null ? null : version.getPublicationStatus().name().toLowerCase(),
                version.getPublishedCommitSha(),
                version.getPublishedPrUrl(),
                version.getLastPublishError(),
                version.getForkedFrom(),
                version.getForkedBy(),
                model.getFailOnMissingDeclaredOutput(),
                model.getFailOnMissingExpectedMutation(),
                model.getResponseSchema(),
                version.getVersion(),
                version.getCanonicalName(),
                version.getStatus().name().toLowerCase(),
                version.getFlowYaml(),
                version.getSavedBy(),
                version.getSavedAt(),
                version.getResourceVersion()
        );
    }
}

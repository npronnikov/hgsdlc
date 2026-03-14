package ru.hgd.sdlc.flow.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import ru.hgd.sdlc.flow.domain.FlowVersion;

public record FlowResponse(
        @JsonProperty("flow_id") String flowId,
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("start_role") String startRole,
        @JsonProperty("approver_role") String approverRole,
        @JsonProperty("start_node_id") String startNodeId,
        @JsonProperty("rule_refs") List<String> ruleRefs,
        @JsonProperty("version") String version,
        @JsonProperty("canonical_name") String canonicalName,
        @JsonProperty("status") String status,
        @JsonProperty("flow_yaml") String flowYaml,
        @JsonProperty("saved_by") String savedBy,
        @JsonProperty("saved_at") Instant savedAt,
        @JsonProperty("resource_version") long resourceVersion
) {
    public static FlowResponse from(FlowVersion version) {
        return new FlowResponse(
                version.getFlowId(),
                version.getTitle(),
                version.getDescription(),
                version.getStartRole(),
                version.getApproverRole(),
                version.getStartNodeId(),
                version.getRuleRefs(),
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

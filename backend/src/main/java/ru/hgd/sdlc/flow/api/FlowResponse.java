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
                model.getCodingAgent(),
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

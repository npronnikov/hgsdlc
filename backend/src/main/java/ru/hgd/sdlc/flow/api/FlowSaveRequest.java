package ru.hgd.sdlc.flow.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record FlowSaveRequest(
        @JsonProperty("flow_id") String flowId,
        @JsonProperty("coding_agent") String codingAgent,
        @JsonProperty("team_code") String teamCode,
        @JsonProperty("platform_code") String platformCode,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("flow_kind") String flowKind,
        @JsonProperty("risk_level") String riskLevel,
        @JsonProperty("environment") String environment,
        @JsonProperty("visibility") String visibility,
        @JsonProperty("lifecycle_status") String lifecycleStatus,
        @JsonProperty("source_ref") String sourceRef,
        @JsonProperty("source_path") String sourcePath,
        @JsonProperty("flow_yaml") String flowYaml,
        @JsonProperty("publish") Boolean publish,
        @JsonProperty("release") Boolean release,
        @JsonProperty("base_version") String baseVersion,
        @JsonProperty("resource_version") Long resourceVersion
) {
}

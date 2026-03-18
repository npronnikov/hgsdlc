package ru.hgd.sdlc.flow.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FlowSaveRequest(
        @JsonProperty("flow_id") String flowId,
        @JsonProperty("coding_agent") String codingAgent,
        @JsonProperty("flow_yaml") String flowYaml,
        @JsonProperty("publish") Boolean publish,
        @JsonProperty("release") Boolean release,
        @JsonProperty("base_version") String baseVersion,
        @JsonProperty("resource_version") Long resourceVersion
) {
}

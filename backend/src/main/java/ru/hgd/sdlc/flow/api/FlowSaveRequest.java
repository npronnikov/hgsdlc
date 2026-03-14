package ru.hgd.sdlc.flow.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FlowSaveRequest(
        @JsonProperty("flow_id") String flowId,
        @JsonProperty("flow_yaml") String flowYaml,
        @JsonProperty("publish") Boolean publish,
        @JsonProperty("release") Boolean release,
        @JsonProperty("resource_version") Long resourceVersion
) {
}

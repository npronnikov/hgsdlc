package ru.hgd.sdlc.flow.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlowModel {
    private String id;
    private String version;

    @JsonProperty("canonical_name")
    private String canonicalName;

    private String title;
    private String description;

    private String status;

    @JsonProperty("start_node_id")
    private String startNodeId;

    @JsonProperty("rule_refs")
    private List<String> ruleRefs;

    @JsonProperty("coding_agent")
    private String codingAgent;

    @JsonProperty("fail_on_missing_declared_output")
    private Boolean failOnMissingDeclaredOutput;

    @JsonProperty("fail_on_missing_expected_mutation")
    private Boolean failOnMissingExpectedMutation;

    @JsonProperty("response_schema")
    private JsonNode responseSchema;

    private List<NodeModel> nodes;
}

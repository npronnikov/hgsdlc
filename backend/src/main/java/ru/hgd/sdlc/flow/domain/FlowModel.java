package ru.hgd.sdlc.flow.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("start_role")
    private String startRole;

    @JsonProperty("approver_role")
    private String approverRole;

    @JsonProperty("start_node_id")
    private String startNodeId;

    @JsonProperty("rule_refs")
    private List<String> ruleRefs;

    @JsonProperty("rule_ref")
    private String ruleRef;

    private List<NodeModel> nodes;
}

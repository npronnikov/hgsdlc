package ru.hgd.sdlc.flow.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
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
public class NodeModel {
    private String id;
    private String title;
    private String description;
    private String type;

    @JsonProperty("node_kind")
    private String nodeKind;

    @JsonProperty("execution_context")
    private List<ExecutionContextEntry> executionContext;

    private String instruction;

    @JsonProperty("skill_refs")
    private List<String> skillRefs;

    @JsonProperty("response_schema")
    private JsonNode responseSchema;

    @JsonProperty("produced_artifacts")
    private List<PathRequirement> producedArtifacts;

    @JsonProperty("expected_mutations")
    private List<PathRequirement> expectedMutations;

    @JsonProperty("on_success")
    private String onSuccess;

    @JsonProperty("on_failure")
    private String onFailure;

    @JsonProperty("on_submit")
    private String onSubmit;

    @JsonProperty("on_approve")
    private String onApprove;

    @JsonProperty("on_rework_routes")
    private Map<String, String> onReworkRoutes;
}

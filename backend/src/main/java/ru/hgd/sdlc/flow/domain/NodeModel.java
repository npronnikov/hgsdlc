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
public class NodeModel {
    private String id;
    private String title;
    private String description;
    private String type;

    @JsonProperty("node_kind")
    private String nodeKind;

    @JsonProperty("gate_kind")
    private String gateKind;

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

    @JsonProperty("input_artifact")
    private String inputArtifact;

    @JsonProperty("output_artifact")
    private String outputArtifact;

    @JsonProperty("review_artifacts")
    private List<String> reviewArtifacts;

    @JsonProperty("allowed_actions")
    private List<String> allowedActions;

    @JsonProperty("allowed_roles")
    private List<String> allowedRoles;

    @JsonProperty("completion_policy")
    private JsonNode completionPolicy;

    @JsonProperty("user_instructions")
    private String userInstructions;

    @JsonProperty("command_engine")
    private String commandEngine;

    @JsonProperty("command_spec")
    private JsonNode commandSpec;

    @JsonProperty("success_exit_codes")
    private List<Integer> successExitCodes;

    @JsonProperty("retry_policy")
    private JsonNode retryPolicy;

    private Boolean idempotent;

    @JsonProperty("checkpoint_before_run")
    private Boolean checkpointBeforeRun;

    @JsonProperty("on_success")
    private String onSuccess;

    @JsonProperty("on_failure")
    private String onFailure;

    @JsonProperty("on_submit")
    private String onSubmit;

    @JsonProperty("on_approve")
    private String onApprove;

    @JsonProperty("on_rework")
    private OnRework onRework;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OnRework {
        @JsonProperty("next_node")
        private String nextNode;
    }
}

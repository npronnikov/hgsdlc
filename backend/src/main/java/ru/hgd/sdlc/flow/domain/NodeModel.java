package ru.hgd.sdlc.flow.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private String type;

    @JsonProperty("executor_kind")
    private String executorKind;

    @JsonProperty("gate_kind")
    private String gateKind;

    @JsonProperty("skill_refs")
    private List<String> skillRefs;

    @JsonProperty("on_success")
    private String onSuccess;

    @JsonProperty("allowed_outcomes")
    private List<String> allowedOutcomes;

    @JsonProperty("outcome_routes")
    private Map<String, String> outcomeRoutes;

    @JsonProperty("on_submit")
    private String onSubmit;

    @JsonProperty("on_approve")
    private String onApprove;

    @JsonProperty("on_reject")
    private String onReject;

    @JsonProperty("on_rework_routes")
    private Map<String, String> onReworkRoutes;
}

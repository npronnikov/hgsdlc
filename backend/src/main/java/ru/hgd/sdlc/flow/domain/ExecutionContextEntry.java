package ru.hgd.sdlc.flow.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class ExecutionContextEntry {
    private String type;
    private String path;
    private String scope;
    private Boolean required;
    private Boolean modifiable;

    @JsonProperty("node_id")
    private String nodeId;

    @JsonProperty("transfer_mode")
    private String transferMode;
}

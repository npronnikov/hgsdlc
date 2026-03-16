package ru.hgd.sdlc.project.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record RunSummaryResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("status") String status,
        @JsonProperty("flow_canonical_name") String flowCanonicalName,
        @JsonProperty("created_at") Instant createdAt
) {
}

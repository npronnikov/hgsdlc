package ru.hgd.sdlc.skill.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import ru.hgd.sdlc.skill.domain.SkillVersion;

public record SkillSummaryResponse(
        @JsonProperty("skill_id") String skillId,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("coding_agent") String codingAgent,
        @JsonProperty("version") String version,
        @JsonProperty("canonical_name") String canonicalName,
        @JsonProperty("status") String status,
        @JsonProperty("saved_by") String savedBy,
        @JsonProperty("saved_at") Instant savedAt,
        @JsonProperty("resource_version") long resourceVersion
) {
    public static SkillSummaryResponse from(SkillVersion version) {
        return new SkillSummaryResponse(
                version.getSkillId(),
                version.getName(),
                version.getDescription(),
                version.getCodingAgent() == null ? null : version.getCodingAgent().name().toLowerCase().replace('_', '-'),
                version.getVersion(),
                version.getCanonicalName(),
                version.getStatus().name().toLowerCase(),
                version.getSavedBy(),
                version.getSavedAt(),
                version.getResourceVersion()
        );
    }
}

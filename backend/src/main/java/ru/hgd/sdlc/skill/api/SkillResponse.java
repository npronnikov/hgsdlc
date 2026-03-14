package ru.hgd.sdlc.skill.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import ru.hgd.sdlc.skill.domain.SkillVersion;

public record SkillResponse(
        @JsonProperty("skill_id") String skillId,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("provider") String provider,
        @JsonProperty("version") String version,
        @JsonProperty("canonical_name") String canonicalName,
        @JsonProperty("status") String status,
        @JsonProperty("skill_markdown") String skillMarkdown,
        @JsonProperty("saved_by") String savedBy,
        @JsonProperty("saved_at") Instant savedAt,
        @JsonProperty("resource_version") long resourceVersion
) {
    public static SkillResponse from(SkillVersion version) {
        return new SkillResponse(
                version.getSkillId(),
                version.getName(),
                version.getDescription(),
                version.getProvider() == null ? null : version.getProvider().name().toLowerCase().replace('_', '-'),
                version.getVersion(),
                version.getCanonicalName(),
                version.getStatus().name().toLowerCase(),
                version.getSkillMarkdown(),
                version.getSavedBy(),
                version.getSavedAt(),
                version.getResourceVersion()
        );
    }
}

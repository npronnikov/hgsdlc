package ru.hgd.sdlc.skill.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import ru.hgd.sdlc.skill.domain.SkillVersion;

public record SkillSummaryResponse(
        @JsonProperty("skill_id") String skillId,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("coding_agent") String codingAgent,
        @JsonProperty("team_code") String teamCode,
        @JsonProperty("platform_code") String platformCode,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("skill_kind") String skillKind,
        @JsonProperty("scope") String scope,
        @JsonProperty("lifecycle_status") String lifecycleStatus,
        @JsonProperty("publication_status") String publicationStatus,
        @JsonProperty("published_pr_url") String publishedPrUrl,
        @JsonProperty("forked_from") String forkedFrom,
        @JsonProperty("forked_by") String forkedBy,
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
                version.getTeamCode(),
                version.getPlatformCode(),
                version.getTags() == null ? List.of() : version.getTags(),
                version.getSkillKind(),
                version.getScope(),
                version.getLifecycleStatus() == null ? null : version.getLifecycleStatus().name().toLowerCase(),
                version.getPublicationStatus() == null ? null : version.getPublicationStatus().name().toLowerCase(),
                version.getPublishedPrUrl(),
                version.getForkedFrom(),
                version.getForkedBy(),
                version.getVersion(),
                version.getCanonicalName(),
                version.getStatus().name().toLowerCase(),
                version.getSavedBy(),
                version.getSavedAt(),
                version.getResourceVersion()
        );
    }
}

package ru.hgd.sdlc.skill.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SkillSaveRequest(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("skill_id") String skillId,
        @JsonProperty("coding_agent") String codingAgent,
        @JsonProperty("team_code") String teamCode,
        @JsonProperty("platform_code") String platformCode,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("skill_kind") String skillKind,
        @JsonProperty("environment") String environment,
        @JsonProperty("visibility") String visibility,
        @JsonProperty("lifecycle_status") String lifecycleStatus,
        @JsonProperty("source_ref") String sourceRef,
        @JsonProperty("source_path") String sourcePath,
        @JsonProperty("skill_markdown") String skillMarkdown,
        @JsonProperty("publish") Boolean publish,
        @JsonProperty("publication_target") String publicationTarget,
        @JsonProperty("publish_mode") String publishMode,
        @JsonProperty("release") Boolean release,
        @JsonProperty("base_version") String baseVersion,
        @JsonProperty("resource_version") Long resourceVersion
) {
}

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
        @JsonProperty("scope") String scope,
        @JsonProperty("lifecycle_status") String lifecycleStatus,
        @JsonProperty("forked_from") String forkedFrom,
        @JsonProperty("forked_by") String forkedBy,
        @JsonProperty("source_ref") String sourceRef,
        @JsonProperty("source_path") String sourcePath,
        @JsonProperty("files") List<SkillFileSaveRequest> files,
        @JsonProperty("publish") Boolean publish,
        @JsonProperty("release") Boolean release,
        @JsonProperty("base_version") String baseVersion,
        @JsonProperty("resource_version") Long resourceVersion
) {
    public record SkillFileSaveRequest(
            @JsonProperty("path") String path,
            @JsonProperty("text_content") String textContent,
            @JsonProperty("is_executable") Boolean executable
    ) {
    }
}

package ru.hgd.sdlc.skill.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SkillSaveRequest(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("skill_id") String skillId,
        @JsonProperty("provider") String provider,
        @JsonProperty("skill_markdown") String skillMarkdown,
        @JsonProperty("publish") Boolean publish,
        @JsonProperty("release") Boolean release,
        @JsonProperty("resource_version") Long resourceVersion
) {
}

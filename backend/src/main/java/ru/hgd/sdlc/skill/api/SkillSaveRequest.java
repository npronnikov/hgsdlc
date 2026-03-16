package ru.hgd.sdlc.skill.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SkillSaveRequest(
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("skill_id") String skillId,
        @JsonProperty("coding_agent") String codingAgent,
        @JsonProperty("skill_markdown") String skillMarkdown,
        @JsonProperty("publish") Boolean publish,
        @JsonProperty("release") Boolean release,
        @JsonProperty("base_version") String baseVersion,
        @JsonProperty("resource_version") Long resourceVersion
) {
}

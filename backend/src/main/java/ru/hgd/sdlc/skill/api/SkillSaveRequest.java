package ru.hgd.sdlc.skill.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SkillSaveRequest(
        @JsonProperty("skill_markdown") String skillMarkdown,
        @JsonProperty("publish") Boolean publish,
        @JsonProperty("resource_version") Long resourceVersion
) {
}

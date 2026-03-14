package ru.hgd.sdlc.rule.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RuleSaveRequest(
        @JsonProperty("title") String title,
        @JsonProperty("rule_id") String ruleId,
        @JsonProperty("provider") String provider,
        @JsonProperty("rule_markdown") String ruleMarkdown,
        @JsonProperty("publish") Boolean publish,
        @JsonProperty("release") Boolean release,
        @JsonProperty("resource_version") Long resourceVersion
) {
}

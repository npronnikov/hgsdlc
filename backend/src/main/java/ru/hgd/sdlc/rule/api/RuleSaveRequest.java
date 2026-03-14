package ru.hgd.sdlc.rule.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RuleSaveRequest(
        @JsonProperty("rule_markdown") String ruleMarkdown,
        @JsonProperty("publish") Boolean publish,
        @JsonProperty("resource_version") Long resourceVersion
) {
}

package ru.hgd.sdlc.rule.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record RuleSaveRequest(
        @JsonProperty("title") String title,
        @JsonProperty("description") String description,
        @JsonProperty("rule_id") String ruleId,
        @JsonProperty("coding_agent") String codingAgent,
        @JsonProperty("team_code") String teamCode,
        @JsonProperty("platform_code") String platformCode,
        @JsonProperty("tags") List<String> tags,
        @JsonProperty("rule_kind") String ruleKind,
        @JsonProperty("scope") String scope,
        @JsonProperty("environment") String environment,
        @JsonProperty("visibility") String visibility,
        @JsonProperty("lifecycle_status") String lifecycleStatus,
        @JsonProperty("forked_from") String forkedFrom,
        @JsonProperty("forked_by") String forkedBy,
        @JsonProperty("source_ref") String sourceRef,
        @JsonProperty("source_path") String sourcePath,
        @JsonProperty("rule_markdown") String ruleMarkdown,
        @JsonProperty("publish") Boolean publish,
        @JsonProperty("publication_target") String publicationTarget,
        @JsonProperty("publish_mode") String publishMode,
        @JsonProperty("release") Boolean release,
        @JsonProperty("base_version") String baseVersion,
        @JsonProperty("resource_version") Long resourceVersion
) {
}

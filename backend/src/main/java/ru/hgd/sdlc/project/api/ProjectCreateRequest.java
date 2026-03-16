package ru.hgd.sdlc.project.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProjectCreateRequest(
        @JsonProperty("name") String name,
        @JsonProperty("repo_url") String repoUrl,
        @JsonProperty("default_branch") String defaultBranch
) {
}

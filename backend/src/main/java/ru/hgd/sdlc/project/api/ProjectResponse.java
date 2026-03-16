package ru.hgd.sdlc.project.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import ru.hgd.sdlc.project.domain.Project;

public record ProjectResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("name") String name,
        @JsonProperty("repo_url") String repoUrl,
        @JsonProperty("default_branch") String defaultBranch,
        @JsonProperty("status") String status,
        @JsonProperty("last_run_id") UUID lastRunId,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("resource_version") long resourceVersion
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getRepoUrl(),
                project.getDefaultBranch(),
                project.getStatus().name().toLowerCase(),
                project.getLastRunId(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getResourceVersion()
        );
    }
}

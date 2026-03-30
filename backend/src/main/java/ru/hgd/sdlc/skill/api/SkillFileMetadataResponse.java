package ru.hgd.sdlc.skill.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.hgd.sdlc.skill.domain.SkillFileEntity;

public record SkillFileMetadataResponse(
        @JsonProperty("path") String path,
        @JsonProperty("role") String role,
        @JsonProperty("is_executable") boolean executable,
        @JsonProperty("size_bytes") long sizeBytes
) {
    public static SkillFileMetadataResponse from(SkillFileEntity file) {
        return new SkillFileMetadataResponse(
                file.getPath(),
                file.getRole().toApiValue(),
                file.isExecutable(),
                file.getSizeBytes()
        );
    }
}

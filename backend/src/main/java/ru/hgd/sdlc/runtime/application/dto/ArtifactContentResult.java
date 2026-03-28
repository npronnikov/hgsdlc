package ru.hgd.sdlc.runtime.application.dto;

import ru.hgd.sdlc.runtime.domain.ArtifactVersionEntity;

public record ArtifactContentResult(
        ArtifactVersionEntity artifact,
        String content
) {}


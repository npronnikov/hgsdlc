package ru.hgd.sdlc.runtime.application.command;

import java.util.List;

public record SubmitInputCommand(
        Long expectedGateVersion,
        List<SubmittedArtifact> artifacts,
        String comment
) {}


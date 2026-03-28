package ru.hgd.sdlc.runtime.application.command;

import java.util.List;
import java.util.UUID;

public record ReworkGateCommand(
        Long expectedGateVersion,
        String comment,
        String instruction,
        List<UUID> reviewedArtifactVersionIds
) {}

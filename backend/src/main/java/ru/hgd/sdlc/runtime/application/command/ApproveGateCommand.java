package ru.hgd.sdlc.runtime.application.command;

import java.util.List;
import java.util.UUID;

public record ApproveGateCommand(
        Long expectedGateVersion,
        String comment,
        List<UUID> reviewedArtifactVersionIds
) {}


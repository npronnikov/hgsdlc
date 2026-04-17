package ru.hgd.sdlc.runtime.application.command;

import java.util.UUID;

public record CreateRunCommand(
        UUID projectId,
        String targetBranch,
        String flowCanonicalName,
        String featureRequest,
        String aiSessionMode,
        String publishMode,
        String workBranch,
        String prCommitStrategy,
        Boolean debugMode
) {}

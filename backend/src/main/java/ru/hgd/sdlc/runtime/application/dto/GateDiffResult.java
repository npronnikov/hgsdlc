package ru.hgd.sdlc.runtime.application.dto;

import java.util.UUID;

public record GateDiffResult(
        UUID gateId,
        UUID runId,
        String path,
        String patch,
        String originalContent,
        String modifiedContent
) {}


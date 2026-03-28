package ru.hgd.sdlc.runtime.application.dto;

import java.util.List;
import java.util.UUID;

public record GateChangesResult(
        UUID gateId,
        UUID runId,
        String gateKind,
        String gateStatus,
        String statusLabel,
        List<GitChangeEntry> gitChanges,
        int filesChanged,
        int addedLines,
        int removedLines
) {}


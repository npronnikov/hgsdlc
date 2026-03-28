package ru.hgd.sdlc.runtime.application.dto;

public record GitChangeEntry(
        String path,
        String status,
        int added,
        int removed,
        boolean binary
) {}


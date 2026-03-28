package ru.hgd.sdlc.runtime.application.dto;

public record CommandResult(
        int exitCode,
        String stdout,
        String stderr,
        String stdoutPath,
        String stderrPath
) {}


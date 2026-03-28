package ru.hgd.sdlc.runtime.application.dto;

public record NodeLogResult(
        String content,
        long offset,
        boolean running
) {}


package ru.hgd.sdlc.runtime.application.command;

public record SubmittedArtifact(
        String artifactKey,
        String path,
        String scope,
        String contentBase64
) {}


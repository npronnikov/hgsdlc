package com.example.sdlc.compiler.domain.model;

import java.util.Map;

/**
 * Template for artifacts produced by nodes.
 */
public record ArtifactTemplateDocument(
    String id,
    String name,
    String mimeType,
    String schemaRef,
    boolean required,
    Map<String, Object> constraints
) {}

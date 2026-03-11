package com.example.sdlc.compiler.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a parsed Flow document from Markdown.
 */
public record FlowDocument(
    String id,
    String name,
    String version,
    String description,
    List<PhaseDocument> phases,
    Map<String, Object> metadata
) {}

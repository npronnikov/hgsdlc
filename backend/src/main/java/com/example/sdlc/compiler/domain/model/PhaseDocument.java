package com.example.sdlc.compiler.domain.model;

import java.util.List;

/**
 * Represents a phase within a Flow document.
 */
public record PhaseDocument(
    String id,
    String name,
    String description,
    int order,
    List<NodeDocument> nodes
) {}

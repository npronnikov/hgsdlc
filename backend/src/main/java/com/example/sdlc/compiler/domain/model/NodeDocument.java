package com.example.sdlc.compiler.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a node within a phase.
 */
public record NodeDocument(
    String id,
    String type,
    String name,
    String description,
    Map<String, Object> config,
    List<String> dependencies
) {}

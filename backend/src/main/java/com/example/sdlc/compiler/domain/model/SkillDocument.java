package com.example.sdlc.compiler.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a skill that can be invoked during flow execution.
 */
public record SkillDocument(
    String id,
    String name,
    String version,
    String description,
    String handler,
    Map<String, Object> inputSchema,
    Map<String, Object> outputSchema,
    List<String> tags
) {}

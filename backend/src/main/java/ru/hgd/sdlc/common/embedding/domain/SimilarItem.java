package ru.hgd.sdlc.common.embedding.domain;

import java.util.List;
import java.util.UUID;

public record SimilarItem(
    UUID id,
    String itemId,
    String version,
    String name,
    String description,
    double similarityScore,
    List<String> tags,
    String teamCode,
    String scope
) {}

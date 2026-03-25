package ru.hgd.sdlc.publication.domain;

public enum PublicationEntityType {
    SKILL,
    RULE,
    FLOW;

    public static PublicationEntityType from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("entity_type is required");
        }
        return PublicationEntityType.valueOf(value.trim().toUpperCase());
    }
}

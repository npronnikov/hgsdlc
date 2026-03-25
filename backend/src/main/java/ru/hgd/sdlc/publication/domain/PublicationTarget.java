package ru.hgd.sdlc.publication.domain;

public enum PublicationTarget {
    DB_ONLY,
    GIT_ONLY,
    DB_AND_GIT;

    public static PublicationTarget from(String value) {
        if (value == null || value.isBlank()) {
            return DB_AND_GIT;
        }
        return PublicationTarget.valueOf(value.trim().toUpperCase());
    }
}

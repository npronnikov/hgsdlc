package ru.hgd.sdlc.skill.domain;

public enum SkillContentSource {
    DB,
    GIT;

    public static SkillContentSource from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return SkillContentSource.valueOf(value.trim().toUpperCase());
    }
}

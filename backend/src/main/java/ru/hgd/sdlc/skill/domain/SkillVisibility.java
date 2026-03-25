package ru.hgd.sdlc.skill.domain;

public enum SkillVisibility {
    INTERNAL,
    RESTRICTED,
    PUBLIC;

    public static SkillVisibility from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return SkillVisibility.valueOf(value.trim().toUpperCase());
    }
}

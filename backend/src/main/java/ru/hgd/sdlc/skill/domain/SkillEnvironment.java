package ru.hgd.sdlc.skill.domain;

public enum SkillEnvironment {
    DEV,
    PROD;

    public static SkillEnvironment from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return SkillEnvironment.valueOf(value.trim().toUpperCase());
    }
}

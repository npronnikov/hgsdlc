package ru.hgd.sdlc.skill.domain;

public enum SkillLifecycleStatus {
    ACTIVE,
    DEPRECATED,
    RETIRED;

    public static SkillLifecycleStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return SkillLifecycleStatus.valueOf(value.trim().toUpperCase());
    }
}

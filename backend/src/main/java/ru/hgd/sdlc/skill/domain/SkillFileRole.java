package ru.hgd.sdlc.skill.domain;

import java.util.Locale;

public enum SkillFileRole {
    INSTRUCTION,
    SCRIPT,
    TEMPLATE,
    ASSET;

    public static SkillFileRole from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("skill file role is required");
        }
        return SkillFileRole.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
    }

    public String toApiValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}

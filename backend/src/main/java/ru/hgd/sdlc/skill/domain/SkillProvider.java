package ru.hgd.sdlc.skill.domain;

public enum SkillProvider {
    QWEN,
    GIGACODE,
    CLAUDE;

    public static SkillProvider from(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase();
        return SkillProvider.valueOf(normalized);
    }
}

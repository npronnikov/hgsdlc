package ru.hgd.sdlc.rule.domain;

public enum RuleProvider {
    QWEN,
    GIGACODE,
    CLAUDE;

    public static RuleProvider from(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase();
        return RuleProvider.valueOf(normalized);
    }
}

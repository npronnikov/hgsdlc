package ru.hgd.sdlc.compiler.domain.model.ir;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.compiler.domain.model.authored.ExecutorKind;
import ru.hgd.sdlc.compiler.domain.model.authored.HandlerRef;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.util.Map;
import java.util.Optional;

/**
 * Normalized configuration for an executor node.
 * This is the compiled, resolved form of executor settings.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public class ExecutorConfig {

    @NonNull private final ExecutorKind kind;

    /**
     * Handler reference (skill, builtin, or script).
     */
    private final HandlerRef handler;

    /**
     * Resolved skill checksum (for SKILL kind).
     */
    private final Sha256 resolvedSkillChecksum;

    /**
     * Configuration parameters.
     */
    @Builder.Default private final Map<String, Object> config = Map.of();

    /**
     * Returns the handler if present.
     */
    public Optional<HandlerRef> handler() {
        return Optional.ofNullable(handler);
    }

    /**
     * Returns the resolved skill checksum if present.
     */
    public Optional<Sha256> resolvedSkillChecksum() {
        return Optional.ofNullable(resolvedSkillChecksum);
    }

    /**
     * Checks if this is a skill executor.
     */
    public boolean isSkill() {
        return kind == ExecutorKind.SKILL;
    }

    /**
     * Checks if this is a builtin executor.
     */
    public boolean isBuiltin() {
        return kind == ExecutorKind.BUILTIN;
    }

    /**
     * Checks if this is a script executor.
     */
    public boolean isScript() {
        return kind == ExecutorKind.SCRIPT;
    }
}

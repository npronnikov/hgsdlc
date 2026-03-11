package ru.hgd.sdlc.compiler.domain.compiler;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import ru.hgd.sdlc.compiler.domain.model.authored.HandlerRef;
import ru.hgd.sdlc.compiler.domain.model.authored.MarkdownBody;
import ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compiled skill IR - the canonical executable representation for skills.
 * Skills are reusable capabilities that can be invoked by executor nodes.
 *
 * <p>Per ADR-002: Runtime executes compiled IR, not Markdown.
 * This ensures deterministic execution and reproducible skill invocations.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
@EqualsAndHashCode
public final class SkillIr implements CompiledIR {

    /**
     * Identity of the source skill.
     */
    @NonNull private final SkillId skillId;

    /**
     * Version of the source skill.
     */
    @NonNull private final SemanticVersion skillVersion;

    /**
     * Human-readable name.
     */
    @NonNull private final String name;

    /**
     * Description compiled from markdown.
     */
    private final MarkdownBody description;

    /**
     * Handler reference (skill implementation).
     */
    @NonNull private final HandlerRef handler;

    /**
     * Input schema for skill parameters.
     */
    @Builder.Default private final Map<String, Object> inputSchema = Map.of();

    /**
     * Output schema for skill results.
     */
    @Builder.Default private final Map<String, Object> outputSchema = Map.of();

    /**
     * Tags for categorization and search.
     */
    @Builder.Default private final List<String> tags = List.of();

    /**
     * IR checksum for content addressing.
     */
    @NonNull private final Sha256 irChecksum;

    /**
     * Timestamp when this IR was compiled.
     */
    @NonNull private final Instant compiledAt;

    /**
     * Version of the compiler that produced this IR.
     */
    @NonNull private final String compilerVersion;

    /**
     * IR schema version for evolution.
     */
    @Builder.Default private final int irSchemaVersion = CURRENT_SCHEMA_VERSION;

    /**
     * Current IR schema version.
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * Returns the description if present.
     */
    public Optional<MarkdownBody> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Checks if this skill has the given tag.
     */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    // CompiledIR interface implementation

    @Override
    public String irId() {
        return skillId.value();
    }

    @Override
    public String sourceVersion() {
        return skillVersion.toString();
    }

    @Override
    public String checksum() {
        return irChecksum.hexValue();
    }
}

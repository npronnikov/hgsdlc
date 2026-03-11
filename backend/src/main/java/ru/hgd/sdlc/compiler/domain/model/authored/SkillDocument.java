package ru.hgd.sdlc.compiler.domain.model.authored;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a skill that can be invoked during flow execution.
 * A skill is a reusable capability that can be referenced by executor nodes.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
public class SkillDocument {

    @NonNull private final SkillId id;
    @NonNull private final String name;
    @NonNull private final SemanticVersion version;
    private final MarkdownBody description;
    @NonNull private final HandlerRef handler;
    @Builder.Default private final Map<String, Object> inputSchema = Map.of();
    @Builder.Default private final Map<String, Object> outputSchema = Map.of();
    @Builder.Default private final List<String> tags = List.of();
    @Builder.Default private final Map<String, Object> extensions = Map.of();
    private final Instant authoredAt;
    private final String author;

    /**
     * Checks if this skill has the given tag.
     */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /**
     * Creates a reference to this skill.
     */
    public HandlerRef toRef() {
        return HandlerRef.skill(id);
    }
}

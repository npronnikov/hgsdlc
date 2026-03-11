package ru.hgd.sdlc.compiler.domain.model.authored;

import lombok.experimental.Accessors;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;
import java.util.Set;

/**
 * Template for artifacts produced or consumed by nodes.
 * Defines the contract for artifact validation and promotion.
 */
@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
@Jacksonized
public class ArtifactTemplateDocument {

    private final ArtifactTemplateId id;
    private final String name;
    private final MarkdownBody description;
    private final LogicalRole logicalRole;
    private final SchemaId schemaId;
    private final PathPattern pathPattern;
    @Builder.Default private final Set<PromotionEligibility> promotionEligibility = Set.of(PromotionEligibility.ELIGIBLE);
    @Builder.Default private final boolean required = true;
    @Builder.Default private final Map<String, Object> constraints = Map.of();
    @Builder.Default private final Map<String, Object> extensions = Map.of();

    /**
     * Checks if the given path matches this artifact's path pattern.
     */
    public boolean matchesPath(String path) {
        return pathPattern != null && pathPattern.matches(path);
    }

    /**
     * Checks if this artifact can be promoted to canonical state.
     */
    public boolean canPromote() {
        return promotionEligibility.contains(PromotionEligibility.ELIGIBLE);
    }

    /**
     * Checks if this artifact requires review before promotion.
     */
    public boolean requiresReview() {
        return promotionEligibility.contains(PromotionEligibility.REVIEW_REQUIRED);
    }
}

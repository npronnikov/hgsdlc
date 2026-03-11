package ru.hgd.sdlc.compiler.domain.model.authored;

/**
 * Eligibility for artifact promotion to canonical project state.
 */
public enum PromotionEligibility {

    /**
     * Artifact can be promoted to canonical state.
     */
    ELIGIBLE,

    /**
     * Artifact requires review before promotion.
     */
    REVIEW_REQUIRED,

    /**
     * Artifact is not eligible for promotion.
     */
    NOT_ELIGIBLE
}

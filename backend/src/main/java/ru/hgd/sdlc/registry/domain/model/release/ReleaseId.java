package ru.hgd.sdlc.registry.domain.model.release;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;

import java.util.Objects;

/**
 * Value object representing a release identifier.
 * Combines a FlowId with a ReleaseVersion to uniquely identify a release.
 *
 * <p>Canonical format: "flowId@version" (e.g., "code-generation-flow@1.2.3")
 */
public final class ReleaseId {

    /**
     * Maximum length for the canonical ID string.
     */
    public static final int MAX_CANONICAL_ID_LENGTH = 512;

    /**
     * Maximum length for flow ID component.
     */
    public static final int MAX_FLOW_ID_LENGTH = 255;

    private final FlowId flowId;
    private final ReleaseVersion version;
    private final String canonicalId;

    private ReleaseId(FlowId flowId, ReleaseVersion version) {
        this.flowId = flowId;
        this.version = version;
        this.canonicalId = flowId.value() + "@" + version.formatted();
    }

    /**
     * Creates a ReleaseId from a FlowId and ReleaseVersion.
     *
     * @param flowId the flow identifier
     * @param version the release version
     * @return a new ReleaseId instance
     * @throws IllegalArgumentException if any parameter is null
     */
    public static ReleaseId of(FlowId flowId, ReleaseVersion version) {
        if (flowId == null) {
            throw new IllegalArgumentException("FlowId cannot be null");
        }
        if (version == null) {
            throw new IllegalArgumentException("Version cannot be null");
        }
        return new ReleaseId(flowId, version);
    }

    /**
     * Parses a canonical release ID string.
     *
     * @param canonicalId the canonical ID (e.g., "code-generation-flow@1.2.3")
     * @return a new ReleaseId instance
     * @throws IllegalArgumentException if the format is invalid
     */
    @JsonCreator
    public static ReleaseId parse(String canonicalId) {
        if (canonicalId == null || canonicalId.isBlank()) {
            throw new IllegalArgumentException("Canonical ID cannot be null or blank");
        }

        int atIndex = canonicalId.lastIndexOf('@');
        if (atIndex <= 0 || atIndex >= canonicalId.length() - 1) {
            throw new IllegalArgumentException(
                "Invalid canonical ID format. Expected: flowId@version. Got: " + canonicalId);
        }

        String flowIdStr = canonicalId.substring(0, atIndex);
        String versionStr = canonicalId.substring(atIndex + 1);

        // Validate length limits
        if (flowIdStr.length() > MAX_FLOW_ID_LENGTH) {
            throw new IllegalArgumentException(
                "Flow ID exceeds maximum length of " + MAX_FLOW_ID_LENGTH + " characters. Got: " + flowIdStr.length());
        }

        FlowId flowId = FlowId.of(flowIdStr);
        ReleaseVersion version = ReleaseVersion.of(versionStr);

        return new ReleaseId(flowId, version);
    }

    /**
     * Returns the flow identifier.
     */
    public FlowId flowId() {
        return flowId;
    }

    /**
     * Returns the release version.
     */
    public ReleaseVersion version() {
        return version;
    }

    /**
     * Returns the canonical string representation.
     * Format: "flowId@version"
     *
     * @return the canonical ID string
     */
    @JsonValue
    public String canonicalId() {
        return canonicalId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReleaseId releaseId = (ReleaseId) o;
        return Objects.equals(flowId, releaseId.flowId)
            && Objects.equals(version, releaseId.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flowId, version);
    }

    @Override
    public String toString() {
        return "ReleaseId{" + canonicalId + "}";
    }
}

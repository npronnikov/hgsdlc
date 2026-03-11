package ru.hgd.sdlc.compiler.domain.model.authored;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a binding between an artifact template and a node's input or output.
 */
public final class ArtifactBinding {

    private final ArtifactTemplateId artifactId;
    private final String bindingName;
    private final boolean required;

    private ArtifactBinding(ArtifactTemplateId artifactId, String bindingName, boolean required) {
        this.artifactId = artifactId;
        this.bindingName = bindingName;
        this.required = required;
    }

    /**
     * Creates a required artifact binding.
     *
     * @param artifactId the artifact template ID
     * @return a new ArtifactBinding instance
     */
    public static ArtifactBinding required(ArtifactTemplateId artifactId) {
        return new ArtifactBinding(artifactId, null, true);
    }

    /**
     * Creates a required artifact binding with a custom name.
     *
     * @param artifactId the artifact template ID
     * @param bindingName the name used to reference this artifact in the node
     * @return a new ArtifactBinding instance
     */
    public static ArtifactBinding required(ArtifactTemplateId artifactId, String bindingName) {
        return new ArtifactBinding(artifactId, bindingName, true);
    }

    /**
     * Creates an optional artifact binding.
     *
     * @param artifactId the artifact template ID
     * @return a new ArtifactBinding instance
     */
    public static ArtifactBinding optional(ArtifactTemplateId artifactId) {
        return new ArtifactBinding(artifactId, null, false);
    }

    /**
     * Creates an optional artifact binding with a custom name.
     *
     * @param artifactId the artifact template ID
     * @param bindingName the name used to reference this artifact in the node
     * @return a new ArtifactBinding instance
     */
    public static ArtifactBinding optional(ArtifactTemplateId artifactId, String bindingName) {
        return new ArtifactBinding(artifactId, bindingName, false);
    }

    public ArtifactTemplateId artifactId() {
        return artifactId;
    }

    public Optional<String> bindingName() {
        return Optional.ofNullable(bindingName);
    }

    public boolean isRequired() {
        return required;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArtifactBinding that = (ArtifactBinding) o;
        return required == that.required
            && Objects.equals(artifactId, that.artifactId)
            && Objects.equals(bindingName, that.bindingName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifactId, bindingName, required);
    }

    @Override
    public String toString() {
        String name = bindingName != null ? bindingName + "=" : "";
        String req = required ? "" : "?";
        return "ArtifactBinding{" + name + artifactId + req + "}";
    }
}

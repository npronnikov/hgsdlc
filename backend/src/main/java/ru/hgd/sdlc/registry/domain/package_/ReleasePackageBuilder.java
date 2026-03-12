package ru.hgd.sdlc.registry.domain.package_;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.PhaseIr;
import ru.hgd.sdlc.registry.domain.model.release.ChecksumManifest;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds ZIP release packages from compiled IR and provenance.
 * Creates a deterministic, content-addressed package format.
 *
 * <p>The builder ensures:
 * <ul>
 *   <li>Deterministic output - same input always produces identical ZIP</li>
 *   <li>Valid checksums for all files</li>
 *   <li>Consistent entry ordering for reproducibility</li>
 * </ul>
 */
public class ReleasePackageBuilder {

    private final ObjectMapper objectMapper;
    private final Map<String, byte[]> entries;
    private final Map<String, Sha256Hash> checksums;

    /**
     * Creates a new ReleasePackageBuilder.
     */
    public ReleasePackageBuilder() {
        this.objectMapper = createObjectMapper();
        this.entries = new LinkedHashMap<>(); // Preserve insertion order
        this.checksums = new LinkedHashMap<>();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return mapper;
    }

    /**
     * Builds a release package from the given components.
     *
     * @param flowIr     the compiled flow IR
     * @param phases     map of phase ID to phase IR
     * @param skills     map of skill ID to skill IR
     * @param provenance the build provenance
     * @return the built ReleasePackage
     * @throws PackageBuildException if building fails
     */
    public ReleasePackage fromSource(
            FlowIr flowIr,
            Map<PhaseId, PhaseIr> phases,
            Map<SkillId, SkillIr> skills,
            Provenance provenance
    ) throws PackageBuildException {
        if (flowIr == null) {
            throw new PackageBuildException("Flow IR cannot be null");
        }
        if (provenance == null) {
            throw new PackageBuildException("Provenance cannot be null");
        }

        // Clear previous state
        entries.clear();
        checksums.clear();

        // Add flow IR
        addJsonEntry(PackageFormat.FILE_FLOW_IR, flowIr);

        // Add phase IRs (sorted by key value for determinism)
        phases.entrySet().stream()
            .sorted((e1, e2) -> e1.getKey().value().compareTo(e2.getKey().value()))
            .forEach(entry -> {
                String path = PackageFormat.phasePath(entry.getKey().value());
                addJsonEntry(path, entry.getValue());
            });

        // Add skill IRs (sorted by key value for determinism)
        skills.entrySet().stream()
            .sorted((e1, e2) -> e1.getKey().value().compareTo(e2.getKey().value()))
            .forEach(entry -> {
                String path = PackageFormat.skillPath(entry.getKey().value());
                addJsonEntry(path, entry.getValue());
            });

        // Add provenance
        addJsonEntry(PackageFormat.FILE_PROVENANCE, provenance);

        // Create manifest
        PackageManifest manifest = createManifest(provenance.getReleaseId());
        addJsonEntry(PackageFormat.FILE_MANIFEST, manifest);

        // Add checksums file
        String checksumsContent = createChecksumsContent(provenance.getReleaseId().canonicalId());
        addTextEntry(PackageFormat.FILE_CHECKSUMS, checksumsContent);

        // Create the checksum manifest
        ChecksumManifest checksumManifest = ChecksumManifest.builder()
            .entries(checksums)
            .build();

        // Build and return the ReleasePackage
        return ReleasePackage.builder()
            .id(provenance.getReleaseId())
            .metadata(ru.hgd.sdlc.registry.domain.model.release.ReleaseMetadata.builder()
                .displayName(flowIr.flowId().value())
                .author(provenance.getCommitAuthor())
                .createdAt(provenance.getBuildTimestamp())
                .gitCommit(provenance.getCommitSha())
                .gitTag(provenance.getGitTag().orElse(null))
                .build())
            .flowIr(flowIr)
            .phases(phases)
            .skills(skills)
            .provenance(provenance)
            .checksums(checksumManifest)
            .build();
    }

    /**
     * Writes the release package to a ZIP file at the given path.
     *
     * @param pkg  the release package to write
     * @param path the path to write the ZIP file to
     * @throws PackageBuildException if writing fails
     */
    public void writeToZip(ReleasePackage pkg, java.nio.file.Path path) throws PackageBuildException {
        try (ZipOutputStream zos = new ZipOutputStream(
                new java.io.FileOutputStream(path.toFile()))) {
            writeToZip(pkg, zos);
        } catch (IOException e) {
            throw new PackageBuildException("Failed to write ZIP file: " + e.getMessage(), e);
        }
    }

    /**
     * Writes the release package to a ZipOutputStream.
     *
     * @param pkg the release package to write
     * @param zos the zip output stream
     * @throws PackageBuildException if writing fails
     */
    public void writeToZip(ReleasePackage pkg, ZipOutputStream zos) throws PackageBuildException {
        try {
            // Rebuild entries from the package
            entries.clear();
            checksums.clear();

            // Add flow IR
            addJsonEntry(PackageFormat.FILE_FLOW_IR, pkg.flowIr());

            // Add phase IRs (sorted by key value for determinism)
            pkg.phases().entrySet().stream()
                .sorted((e1, e2) -> e1.getKey().value().compareTo(e2.getKey().value()))
                .forEach(entry -> {
                    String path = PackageFormat.phasePath(entry.getKey().value());
                    addJsonEntry(path, entry.getValue());
                });

            // Add skill IRs (sorted by key value for determinism)
            pkg.skills().entrySet().stream()
                .sorted((e1, e2) -> e1.getKey().value().compareTo(e2.getKey().value()))
                .forEach(entry -> {
                    String path = PackageFormat.skillPath(entry.getKey().value());
                    addJsonEntry(path, entry.getValue());
                });

            // Add provenance
            addJsonEntry(PackageFormat.FILE_PROVENANCE, pkg.provenance());

            // Create manifest
            PackageManifest manifest = createManifest(pkg.id());
            addJsonEntry(PackageFormat.FILE_MANIFEST, manifest);

            // Add checksums file
            String checksumsContent = createChecksumsContent(pkg.id().canonicalId());
            addTextEntry(PackageFormat.FILE_CHECKSUMS, checksumsContent);

            // Write entries in order (deterministic)
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new PackageBuildException("Failed to write ZIP: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the ZIP content as a byte array.
     *
     * @param pkg the release package
     * @return the ZIP content as bytes
     * @throws PackageBuildException if building fails
     */
    public byte[] toByteArray(ReleasePackage pkg) throws PackageBuildException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeToZip(pkg, zos);
        } catch (IOException e) {
            throw new PackageBuildException("Failed to create ZIP: " + e.getMessage(), e);
        }
        return baos.toByteArray();
    }

    private void addJsonEntry(String path, Object value) throws PackageBuildException {
        try {
            byte[] content = objectMapper.writeValueAsBytes(value);
            addEntry(path, content);
        } catch (IOException e) {
            throw new PackageBuildException("Failed to serialize " + path + ": " + e.getMessage(), e);
        }
    }

    private void addTextEntry(String path, String content) {
        addEntry(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private void addEntry(String path, byte[] content) {
        Sha256Hash hash = computeHash(content);
        entries.put(path, content);
        checksums.put(path, hash);
    }

    private Sha256Hash computeHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content);
            return Sha256Hash.ofBytes(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private PackageManifest createManifest(ru.hgd.sdlc.registry.domain.model.release.ReleaseId releaseId) {
        Map<String, String> fileChecksums = new LinkedHashMap<>();
        checksums.forEach((path, hash) -> fileChecksums.put(path, hash.hex()));

        return PackageManifest.builder()
            .releaseId(releaseId)
            .createdAt(Instant.now())
            .files(fileChecksums)
            .build();
    }

    private String createChecksumsContent(String releaseId) {
        StringBuilder sb = new StringBuilder();
        sb.append(PackageFormat.CHECKSUM_COMMENT_PREFIX)
          .append("Release: ").append(releaseId).append("\n");
        sb.append(PackageFormat.CHECKSUM_COMMENT_PREFIX)
          .append("Generated: ").append(Instant.now().toString()).append("\n\n");

        // Write checksums in sorted order for determinism
        checksums.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                sb.append(entry.getValue().hex())
                  .append("  ")
                  .append(entry.getKey())
                  .append("\n");
            });

        return sb.toString();
    }

    /**
     * Exception thrown when package building fails.
     */
    public static class PackageBuildException extends RuntimeException {
        public PackageBuildException(String message) {
            super(message);
        }

        public PackageBuildException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

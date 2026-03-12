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
import ru.hgd.sdlc.registry.domain.model.release.ReleaseMetadata;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads ZIP release packages and validates their integrity.
 * Ensures all checksums match before returning the package contents.
 *
 * <p>The reader performs the following validations:
 * <ul>
 *   <li>Required files are present (manifest, flow IR, provenance, checksums)</li>
 *   <li>All file checksums match those in checksums.sha256</li>
 *   <li>Manifest checksums match actual file hashes</li>
 * </ul>
 */
public class ReleasePackageReader {

    private static final Pattern CHECKSUM_LINE_PATTERN =
        Pattern.compile("^([a-f0-9]{64})\\s+\\s*(.+)$");

    private final ObjectMapper objectMapper;

    /**
     * Creates a new ReleasePackageReader.
     */
    public ReleasePackageReader() {
        this.objectMapper = createObjectMapper();
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
     * Reads a release package from a ZIP file at the given path.
     *
     * @param path the path to the ZIP file
     * @return the ReleasePackage with validated contents
     * @throws PackageReadException if reading or validation fails
     */
    public ReleasePackage readFromZip(Path path) throws PackageReadException {
        if (!Files.exists(path)) {
            throw new PackageReadException("Package file not found: " + path);
        }

        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            return readFromZipFile(zipFile);
        } catch (IOException e) {
            throw new PackageReadException("Failed to open ZIP file: " + e.getMessage(), e);
        }
    }

    /**
     * Reads a release package from an open ZipFile.
     *
     * @param zipFile the open ZIP file
     * @return the ReleasePackage with validated contents
     * @throws PackageReadException if reading or validation fails
     */
    public ReleasePackage readFromZipFile(ZipFile zipFile) throws PackageReadException {
        // Read all entries into memory
        Map<String, byte[]> entries = readAllEntries(zipFile);

        // Validate required files exist
        validateRequiredFiles(entries);

        // Parse checksums file
        Map<String, Sha256Hash> expectedChecksums = parseChecksumsFile(entries);

        // Validate all checksums
        validateChecksums(entries, expectedChecksums);

        // Parse manifest
        PackageManifest manifest = parseManifest(entries);

        // Validate manifest checksums match
        validateManifestChecksums(manifest, expectedChecksums);

        // Parse components
        FlowIr flowIr = parseFlowIr(entries);
        Provenance provenance = parseProvenance(entries);
        Map<PhaseId, PhaseIr> phases = parsePhases(entries);
        Map<SkillId, SkillIr> skills = parseSkills(entries);

        // Build checksum manifest
        ChecksumManifest checksumManifest = ChecksumManifest.builder()
            .entries(expectedChecksums)
            .build();

        // Build metadata
        ReleaseMetadata metadata = ReleaseMetadata.builder()
            .displayName(flowIr.flowId().value())
            .author(provenance.getCommitAuthor())
            .createdAt(provenance.getBuildTimestamp())
            .gitCommit(provenance.getCommitSha())
            .gitTag(provenance.getGitTag().orElse(null))
            .build();

        // Build and return the package
        return ReleasePackage.builder()
            .id(manifest.releaseId())
            .metadata(metadata)
            .flowIr(flowIr)
            .phases(phases)
            .skills(skills)
            .provenance(provenance)
            .checksums(checksumManifest)
            .build();
    }

    private Map<String, byte[]> readAllEntries(ZipFile zipFile) throws PackageReadException {
        Map<String, byte[]> entries = new TreeMap<>(); // Sorted for deterministic processing

        try {
            var enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (!entry.isDirectory()) {
                    try (var is = zipFile.getInputStream(entry)) {
                        entries.put(entry.getName(), is.readAllBytes());
                    }
                }
            }
        } catch (IOException e) {
            throw new PackageReadException("Failed to read ZIP entries: " + e.getMessage(), e);
        }

        return entries;
    }

    private void validateRequiredFiles(Map<String, byte[]> entries) throws PackageReadException {
        if (!entries.containsKey(PackageFormat.FILE_MANIFEST)) {
            throw new PackageReadException("Missing required file: " + PackageFormat.FILE_MANIFEST);
        }
        if (!entries.containsKey(PackageFormat.FILE_FLOW_IR)) {
            throw new PackageReadException("Missing required file: " + PackageFormat.FILE_FLOW_IR);
        }
        if (!entries.containsKey(PackageFormat.FILE_PROVENANCE)) {
            throw new PackageReadException("Missing required file: " + PackageFormat.FILE_PROVENANCE);
        }
        if (!entries.containsKey(PackageFormat.FILE_CHECKSUMS)) {
            throw new PackageReadException("Missing required file: " + PackageFormat.FILE_CHECKSUMS);
        }
    }

    private Map<String, Sha256Hash> parseChecksumsFile(Map<String, byte[]> entries)
            throws PackageReadException {
        byte[] content = entries.get(PackageFormat.FILE_CHECKSUMS);
        if (content == null) {
            throw new PackageReadException("Checksums file not found");
        }

        String checksumsText = new String(content, StandardCharsets.UTF_8);
        Map<String, Sha256Hash> checksums = new LinkedHashMap<>();

        for (String line : checksumsText.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith(PackageFormat.CHECKSUM_COMMENT_PREFIX)) {
                continue; // Skip comments and empty lines
            }

            Matcher matcher = CHECKSUM_LINE_PATTERN.matcher(line);
            if (matcher.matches()) {
                String hash = matcher.group(1);
                String path = matcher.group(2);
                checksums.put(path, Sha256Hash.of(hash));
            }
        }

        return checksums;
    }

    private void validateChecksums(Map<String, byte[]> entries, Map<String, Sha256Hash> expected)
            throws PackageReadException {
        Map<String, String> mismatches = new LinkedHashMap<>();

        for (Map.Entry<String, Sha256Hash> entry : expected.entrySet()) {
            String path = entry.getKey();
            Sha256Hash expectedHash = entry.getValue();

            byte[] content = entries.get(path);
            if (content == null) {
                throw new PackageReadException("File listed in checksums not found: " + path);
            }

            Sha256Hash actualHash = computeHash(content);
            if (!expectedHash.equals(actualHash)) {
                mismatches.put(path,
                    "expected=" + expectedHash.hex() + ", actual=" + actualHash.hex());
            }
        }

        if (!mismatches.isEmpty()) {
            StringBuilder sb = new StringBuilder("Checksum validation failed:\n");
            mismatches.forEach((path, detail) ->
                sb.append("  ").append(path).append(": ").append(detail).append("\n"));
            throw new PackageReadException(sb.toString());
        }
    }

    private void validateManifestChecksums(PackageManifest manifest, Map<String, Sha256Hash> expected)
            throws PackageReadException {
        for (Map.Entry<String, String> entry : manifest.files().entrySet()) {
            String path = entry.getKey();
            String manifestHash = entry.getValue();
            Sha256Hash expectedHash = expected.get(path);

            if (expectedHash == null) {
                throw new PackageReadException("File in manifest not in checksums: " + path);
            }

            if (!manifestHash.equals(expectedHash.hex())) {
                throw new PackageReadException(
                    "Manifest checksum mismatch for " + path + ": " +
                    "manifest=" + manifestHash + ", checksums=" + expectedHash.hex());
            }
        }
    }

    private PackageManifest parseManifest(Map<String, byte[]> entries) throws PackageReadException {
        byte[] content = entries.get(PackageFormat.FILE_MANIFEST);
        try {
            return objectMapper.readValue(content, PackageManifest.class);
        } catch (IOException e) {
            throw new PackageReadException("Failed to parse manifest: " + e.getMessage(), e);
        }
    }

    private FlowIr parseFlowIr(Map<String, byte[]> entries) throws PackageReadException {
        byte[] content = entries.get(PackageFormat.FILE_FLOW_IR);
        try {
            return objectMapper.readValue(content, FlowIr.class);
        } catch (IOException e) {
            throw new PackageReadException("Failed to parse flow IR: " + e.getMessage(), e);
        }
    }

    private Provenance parseProvenance(Map<String, byte[]> entries) throws PackageReadException {
        byte[] content = entries.get(PackageFormat.FILE_PROVENANCE);
        try {
            return objectMapper.readValue(content, Provenance.class);
        } catch (IOException e) {
            throw new PackageReadException("Failed to parse provenance: " + e.getMessage(), e);
        }
    }

    private Map<PhaseId, PhaseIr> parsePhases(Map<String, byte[]> entries) throws PackageReadException {
        Map<PhaseId, PhaseIr> phases = new HashMap<>();

        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String path = entry.getKey();
            if (PackageFormat.isPhasePath(path)) {
                String phaseIdStr = PackageFormat.extractPhaseId(path);
                if (phaseIdStr == null) {
                    throw new PackageReadException("Invalid phase path: " + path);
                }

                try {
                    PhaseIr phaseIr = objectMapper.readValue(entry.getValue(), PhaseIr.class);
                    phases.put(PhaseId.of(phaseIdStr), phaseIr);
                } catch (IOException e) {
                    throw new PackageReadException(
                        "Failed to parse phase IR: " + path + ": " + e.getMessage(), e);
                }
            }
        }

        return phases;
    }

    private Map<SkillId, SkillIr> parseSkills(Map<String, byte[]> entries) throws PackageReadException {
        Map<SkillId, SkillIr> skills = new HashMap<>();

        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String path = entry.getKey();
            if (PackageFormat.isSkillPath(path)) {
                String skillIdStr = PackageFormat.extractSkillId(path);
                if (skillIdStr == null) {
                    throw new PackageReadException("Invalid skill path: " + path);
                }

                try {
                    SkillIr skillIr = objectMapper.readValue(entry.getValue(), SkillIr.class);
                    skills.put(SkillId.of(skillIdStr), skillIr);
                } catch (IOException e) {
                    throw new PackageReadException(
                        "Failed to parse skill IR: " + path + ": " + e.getMessage(), e);
                }
            }
        }

        return skills;
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

    /**
     * Exception thrown when package reading or validation fails.
     */
    public static class PackageReadException extends Exception {
        public PackageReadException(String message) {
            super(message);
        }

        public PackageReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

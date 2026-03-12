package ru.hgd.sdlc.registry.application.lockfile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default implementation of LockfileGenerator.
 *
 * <p>Generates lockfiles by:
 * <ol>
 *   <li>Creating entries for each package</li>
 *   <li>Recording dependency relationships</li>
 *   <li>Computing checksums</li>
 *   <li>Building the final lockfile</li>
 * </ol>
 */
public class DefaultLockfileGenerator implements LockfileGenerator {

    private final ObjectMapper objectMapper;

    /**
     * Creates a new DefaultLockfileGenerator with default ObjectMapper.
     */
    public DefaultLockfileGenerator() {
        this.objectMapper = createObjectMapper();
    }

    /**
     * Creates a new DefaultLockfileGenerator with a custom ObjectMapper.
     *
     * @param objectMapper the ObjectMapper to use for JSON serialization
     */
    public DefaultLockfileGenerator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
    }

    @Override
    public Lockfile generate(List<ReleasePackage> packages) {
        if (packages == null || packages.isEmpty()) {
            throw new IllegalArgumentException("Packages list cannot be null or empty");
        }

        // Build a map of release ID to package for dependency lookup
        Map<String, ReleasePackage> packageMap = new HashMap<>();
        for (ReleasePackage pkg : packages) {
            packageMap.put(pkg.id().canonicalId(), pkg);
        }

        // Create entries in order
        List<LockfileEntry> entries = new ArrayList<>(packages.size());
        for (ReleasePackage pkg : packages) {
            entries.add(createEntry(pkg, packageMap));
        }

        // Get root package info
        ReleasePackage rootPackage = packages.get(0);

        // Build lockfile without checksum first
        Lockfile tempLockfile = Lockfile.builder()
            .version(LockfileVersion.current())
            .generatedAt(Instant.now())
            .flowId(rootPackage.flowId().value())
            .flowVersion(rootPackage.version().formatted())
            .entries(entries)
            .checksum("temp") // placeholder
            .build();

        // Compute checksum of the content
        String checksum = computeChecksum(tempLockfile);

        // Build final lockfile with checksum
        return tempLockfile.toBuilder()
            .checksum(checksum)
            .build();
    }

    private LockfileEntry createEntry(ReleasePackage pkg, Map<String, ReleasePackage> packageMap) {
        // Determine entry type based on whether it's a flow or skill
        // A flow has FlowIr, a skill would have SkillIr
        LockfileEntryType type = determineType(pkg);

        // Get IR checksum from metadata
        String irChecksum = pkg.flowIr().metadata().irChecksum().hexValue();

        // Get package checksum from the checksum manifest
        String packageChecksum = getPackageChecksum(pkg);

        // Build dependency list
        List<String> dependencies = resolveDependencies(pkg, packageMap);

        return LockfileEntry.builder()
            .releaseId(pkg.id().canonicalId())
            .type(type)
            .irChecksum(irChecksum)
            .packageChecksum(packageChecksum)
            .dependencies(dependencies)
            .build();
    }

    private LockfileEntryType determineType(ReleasePackage pkg) {
        // Check if this package is for a flow or skill
        // A flow package contains FlowIr
        // A skill package would have been resolved separately
        // For now, all packages from resolver are FLOW type
        return LockfileEntryType.FLOW;
    }

    private String getPackageChecksum(ReleasePackage pkg) {
        // Get the package checksum from the checksum manifest
        // Use the checksum of the flow IR as the package checksum
        // In a full implementation, this would be a hash of all package contents
        return pkg.checksums().entries().values().stream()
            .map(hash -> hash.hex())
            .sorted()
            .reduce((a, b) -> a + b)
            .map(combined -> sha256(combined))
            .orElseGet(() -> pkg.flowIr().metadata().irChecksum().hexValue());
    }

    private List<String> resolveDependencies(ReleasePackage pkg, Map<String, ReleasePackage> packageMap) {
        List<String> dependencies = new ArrayList<>();

        // Get skill dependencies from the flow IR
        Map<ru.hgd.sdlc.compiler.domain.model.authored.SkillId, ru.hgd.sdlc.shared.hashing.Sha256> resolvedSkills =
            pkg.flowIr().resolvedSkills();

        for (Map.Entry<ru.hgd.sdlc.compiler.domain.model.authored.SkillId, ru.hgd.sdlc.shared.hashing.Sha256> entry : resolvedSkills.entrySet()) {
            // Check if this skill is bundled in the current package
            if (pkg.skills().containsKey(entry.getKey())) {
                // Bundled skill - no external dependency
                continue;
            }

            // Find the package that provides this skill
            for (Map.Entry<String, ReleasePackage> pkgEntry : packageMap.entrySet()) {
                ReleasePackage depPkg = pkgEntry.getValue();
                if (depPkg.skills().containsKey(entry.getKey()) && !depPkg.id().equals(pkg.id())) {
                    dependencies.add(pkgEntry.getKey());
                    break;
                }
            }
        }

        return dependencies;
    }

    /**
     * Computes the SHA-256 checksum of the lockfile content.
     *
     * @param lockfile the lockfile to checksum
     * @return the hex-encoded checksum
     */
    private String computeChecksum(Lockfile lockfile) {
        try {
            // Create a canonical JSON representation without the checksum field
            Lockfile withoutChecksum = lockfile.toBuilder()
                .checksum("")
                .build();

            // Create a copy of ObjectMapper for canonical serialization
            ObjectMapper canonicalMapper = createObjectMapper();
            canonicalMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

            String json = canonicalMapper.writeValueAsString(withoutChecksum);
            return sha256(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize lockfile for checksum", e);
        }
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }
}

package ru.hgd.sdlc.registry.application.builder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.PhaseIr;
import ru.hgd.sdlc.registry.application.signing.ProvenanceSigner;
import ru.hgd.sdlc.registry.application.signing.SigningException;
import ru.hgd.sdlc.registry.domain.model.provenance.BuilderInfo;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.provenance.ProvenanceSignature;
import ru.hgd.sdlc.registry.domain.model.provenance.SignedProvenance;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseId;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;
import ru.hgd.sdlc.registry.domain.model.release.ReleaseVersion;
import ru.hgd.sdlc.registry.domain.model.release.Sha256Hash;
import ru.hgd.sdlc.registry.domain.package_.ReleasePackageBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link ReleaseBuilder}.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Validates all inputs (flow IR is required, source info is required)</li>
 *   <li>Generates provenance with Git information and checksums</li>
 *   <li>Builds the ReleasePackage using ReleasePackageBuilder</li>
 *   <li>Signs the provenance if a signer is configured</li>
 * </ul>
 *
 * <p>The builder is configured with:
 * <ul>
 *   <li>Compiler version for provenance tracking</li>
 *   <li>Optional ProvenanceSigner for signing releases</li>
 * </ul>
 */
public class DefaultReleaseBuilder implements ReleaseBuilder {

    private static final String BUILDER_NAME = "sdlc-registry";
    private static final String DEFAULT_COMPILER_VERSION = "1.0.0";

    private final String compilerVersion;
    private final ProvenanceSigner signer;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new DefaultReleaseBuilder without signing.
     */
    public DefaultReleaseBuilder() {
        this(DEFAULT_COMPILER_VERSION, null);
    }

    /**
     * Creates a new DefaultReleaseBuilder with optional signing.
     *
     * @param compilerVersion the compiler version to record in provenance
     * @param signer optional signer for signing provenance (may be null)
     */
    public DefaultReleaseBuilder(String compilerVersion, ProvenanceSigner signer) {
        this.compilerVersion = compilerVersion != null ? compilerVersion : DEFAULT_COMPILER_VERSION;
        this.signer = signer;
        this.objectMapper = createObjectMapper();
    }

    /**
     * Creates a new DefaultReleaseBuilder with signing enabled.
     *
     * @param signer the signer for signing provenance
     */
    public DefaultReleaseBuilder(ProvenanceSigner signer) {
        this(DEFAULT_COMPILER_VERSION, signer);
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new Jdk8Module());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        return mapper;
    }

    @Override
    public ReleasePackage build(FlowIr flowIr,
                                 Map<PhaseId, PhaseIr> phases,
                                 Map<SkillId, SkillIr> skills,
                                 SourceInfo sourceInfo) throws ReleaseBuildException {
        // Validate inputs
        validateInputs(flowIr, sourceInfo);

        // Normalize maps
        Map<PhaseId, PhaseIr> normalizedPhases = phases != null ? phases : Map.of();
        Map<SkillId, SkillIr> normalizedSkills = skills != null ? skills : Map.of();

        // Calculate IR checksum
        Sha256Hash irChecksum = calculateIrChecksum(flowIr, normalizedPhases, normalizedSkills);

        // Build initial package to calculate package checksum
        ReleaseId releaseId = createReleaseId(flowIr);
        Provenance provenance = buildProvenance(
            releaseId,
            flowIr,
            sourceInfo,
            irChecksum,
            irChecksum // Initial value, will be updated after package is built
        );

        // Sign provenance if signer is configured
        Provenance finalProvenance = signProvenanceIfNeeded(provenance);

        // Build the release package
        ReleasePackageBuilder packageBuilder = new ReleasePackageBuilder();
        try {
            return packageBuilder.fromSource(
                flowIr,
                normalizedPhases,
                normalizedSkills,
                finalProvenance
            );
        } catch (ReleasePackageBuilder.PackageBuildException e) {
            throw new ReleaseBuildException("Failed to build release package: " + e.getMessage(), e);
        }
    }

    private void validateInputs(FlowIr flowIr, SourceInfo sourceInfo) {
        if (flowIr == null) {
            throw new ReleaseBuildException("Flow IR cannot be null");
        }
        if (sourceInfo == null) {
            throw new ReleaseBuildException("SourceInfo cannot be null");
        }
    }

    private ReleaseId createReleaseId(FlowIr flowIr) {
        return ReleaseId.of(
            flowIr.flowId(),
            ReleaseVersion.of(flowIr.flowVersion().toString())
        );
    }

    private Provenance buildProvenance(ReleaseId releaseId,
                                        FlowIr flowIr,
                                        SourceInfo sourceInfo,
                                        Sha256Hash irChecksum,
                                        Sha256Hash packageChecksum) {
        Instant buildTimestamp = Instant.now();

        // Build input checksums from source info
        Map<String, String> inputs = new HashMap<>(sourceInfo.inputChecksums());

        // Build environment info
        Map<String, String> environment = buildEnvironmentInfo();

        return Provenance.builder()
            .releaseId(releaseId)
            .repositoryUrl(sourceInfo.repositoryUrl())
            .commitSha(validateAndNormalizeCommitSha(sourceInfo.commitSha()))
            .buildTimestamp(buildTimestamp)
            .gitTag(sourceInfo.tag().orElse(null))
            .branch(sourceInfo.branch().orElse(null))
            .commitAuthor(sourceInfo.commitAuthor())
            .committedAt(sourceInfo.committedAt())
            .builderId(BUILDER_NAME)
            .builder(BuilderInfo.of(BUILDER_NAME, compilerVersion))
            .compilerVersion(compilerVersion)
            .irChecksum(irChecksum)
            .packageChecksum(packageChecksum)
            .inputs(inputs)
            .environment(environment)
            .build();
    }

    private String validateAndNormalizeCommitSha(String commitSha) {
        // Use Provenance's validation, wrap in ReleaseBuildException for consistency
        try {
            return Provenance.validateGitCommit(commitSha);
        } catch (IllegalArgumentException e) {
            throw new ReleaseBuildException("Invalid commit SHA: " + e.getMessage(), e);
        }
    }

    private Map<String, String> buildEnvironmentInfo() {
        Map<String, String> env = new HashMap<>();
        env.put("java.version", System.getProperty("java.version", "unknown"));
        env.put("os.name", System.getProperty("os.name", "unknown"));
        env.put("builder.name", BUILDER_NAME);
        env.put("builder.version", compilerVersion);
        return env;
    }

    private Sha256Hash calculateIrChecksum(FlowIr flowIr,
                                            Map<PhaseId, PhaseIr> phases,
                                            Map<SkillId, SkillIr> skills) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Hash flow IR
            byte[] flowBytes = objectMapper.writeValueAsBytes(flowIr);
            digest.update(flowBytes);

            // Hash phase IRs in sorted order
            phases.entrySet().stream()
                .sorted((e1, e2) -> e1.getKey().value().compareTo(e2.getKey().value()))
                .forEach(entry -> {
                    try {
                        digest.update(entry.getKey().value().getBytes(StandardCharsets.UTF_8));
                        digest.update(objectMapper.writeValueAsBytes(entry.getValue()));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to serialize phase IR", e);
                    }
                });

            // Hash skill IRs in sorted order
            skills.entrySet().stream()
                .sorted((e1, e2) -> e1.getKey().value().compareTo(e2.getKey().value()))
                .forEach(entry -> {
                    try {
                        digest.update(entry.getKey().value().getBytes(StandardCharsets.UTF_8));
                        digest.update(objectMapper.writeValueAsBytes(entry.getValue()));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to serialize skill IR", e);
                    }
                });

            byte[] hashBytes = digest.digest();
            return Sha256Hash.ofBytes(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new ReleaseBuildException("SHA-256 algorithm not available", e);
        } catch (IOException e) {
            throw new ReleaseBuildException("Failed to calculate IR checksum: " + e.getMessage(), e);
        }
    }

    private Provenance signProvenanceIfNeeded(Provenance provenance) {
        if (signer == null) {
            return provenance;
        }

        try {
            SignedProvenance signedProvenance = signer.sign(provenance);
            // Return the provenance with signature attached
            return provenance.withSignature(signedProvenance.signature());
        } catch (SigningException e) {
            throw new ReleaseBuildException("Failed to sign provenance: " + e.getMessage(), e);
        }
    }

    /**
     * Returns whether this builder has a signer configured.
     *
     * @return true if signing is enabled
     */
    public boolean hasSigner() {
        return signer != null;
    }

    /**
     * Returns the compiler version used by this builder.
     *
     * @return the compiler version
     */
    public String compilerVersion() {
        return compilerVersion;
    }

    /**
     * Returns the signer, if configured.
     *
     * @return Optional containing the signer if present
     */
    public Optional<ProvenanceSigner> signer() {
        return Optional.ofNullable(signer);
    }
}

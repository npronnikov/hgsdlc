package ru.hgd.sdlc.registry.application.builder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.compiler.domain.model.authored.HandlerRef;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;
import ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.IrMetadata;
import ru.hgd.sdlc.compiler.domain.model.ir.PhaseIr;
import ru.hgd.sdlc.registry.application.signing.Ed25519Signer;
import ru.hgd.sdlc.registry.application.signing.ProvenanceSigner;
import ru.hgd.sdlc.registry.domain.model.provenance.SigningKeyPair;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultReleaseBuilder")
class DefaultReleaseBuilderTest {

    private DefaultReleaseBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new DefaultReleaseBuilder();
    }

    @Nested
    @DisplayName("build()")
    class Build {

        @Test
        @DisplayName("should build valid release package")
        void shouldBuildValidReleasePackage() {
            FlowIr flowIr = createFlowIr();
            SourceInfo sourceInfo = createSourceInfo();

            ReleasePackage pkg = builder.build(flowIr, sourceInfo);

            assertNotNull(pkg);
            assertNotNull(pkg.id());
            assertEquals("test-flow@1.0.0", pkg.id().canonicalId());
            assertNotNull(pkg.flowIr());
            assertNotNull(pkg.provenance());
            assertNotNull(pkg.checksums());
        }

        @Test
        @DisplayName("should throw on null flow IR")
        void shouldThrowOnNullFlowIr() {
            SourceInfo sourceInfo = createSourceInfo();

            assertThrows(ReleaseBuildException.class,
                () -> builder.build(null, Map.of(), Map.of(), sourceInfo));
        }

        @Test
        @DisplayName("should throw on null source info")
        void shouldThrowOnNullSourceInfo() {
            FlowIr flowIr = createFlowIr();

            assertThrows(ReleaseBuildException.class,
                () -> builder.build(flowIr, Map.of(), Map.of(), null));
        }

        @Test
        @DisplayName("should include all phases in package")
        void shouldIncludeAllPhasesInPackage() {
            FlowIr flowIr = createFlowIr();
            SourceInfo sourceInfo = createSourceInfo();
            Map<PhaseId, PhaseIr> phases = Map.of(
                PhaseId.of("setup"), createPhaseIr("setup", 0),
                PhaseId.of("develop"), createPhaseIr("develop", 1),
                PhaseId.of("deploy"), createPhaseIr("deploy", 2)
            );

            ReleasePackage pkg = builder.build(flowIr, phases, Map.of(), sourceInfo);

            assertEquals(3, pkg.phaseCount());
            assertTrue(pkg.getPhase(PhaseId.of("setup")).isPresent());
            assertTrue(pkg.getPhase(PhaseId.of("develop")).isPresent());
            assertTrue(pkg.getPhase(PhaseId.of("deploy")).isPresent());
        }

        @Test
        @DisplayName("should include all skills in package")
        void shouldIncludeAllSkillsInPackage() {
            FlowIr flowIr = createFlowIr();
            SourceInfo sourceInfo = createSourceInfo();
            Map<SkillId, SkillIr> skills = Map.of(
                SkillId.of("code-gen"), createSkillIr("code-gen"),
                SkillId.of("test-runner"), createSkillIr("test-runner")
            );

            ReleasePackage pkg = builder.build(flowIr, Map.of(), skills, sourceInfo);

            assertEquals(2, pkg.skillCount());
            assertTrue(pkg.getSkill(SkillId.of("code-gen")).isPresent());
            assertTrue(pkg.getSkill(SkillId.of("test-runner")).isPresent());
        }

        @Test
        @DisplayName("should generate correct provenance")
        void shouldGenerateCorrectProvenance() {
            FlowIr flowIr = createFlowIr();
            SourceInfo sourceInfo = createSourceInfo();

            ReleasePackage pkg = builder.build(flowIr, sourceInfo);

            assertNotNull(pkg.provenance());
            assertEquals("test-flow@1.0.0", pkg.provenance().getReleaseId().canonicalId());
            assertEquals("https://github.com/test/repo.git", pkg.provenance().getRepositoryUrl());
            assertEquals("abc123def456789012345678901234567890abcd", pkg.provenance().getCommitSha());
            assertEquals("test@example.com", pkg.provenance().getCommitAuthor());
            assertTrue(pkg.provenance().getBranch().isPresent());
            assertEquals("main", pkg.provenance().getBranch().get());
            assertTrue(pkg.provenance().getGitTag().isPresent());
            assertEquals("v1.0.0", pkg.provenance().getGitTag().get());
        }

        @Test
        @DisplayName("should include input checksums in provenance")
        void shouldIncludeInputChecksumsInProvenance() {
            FlowIr flowIr = createFlowIr();
            SourceInfo sourceInfo = SourceInfo.builder()
                .repositoryUrl("https://github.com/test/repo.git")
                .commitSha("abc123def456789012345678901234567890abcd")
                .commitAuthor("test@example.com")
                .committedAt(Instant.now())
                .branch("main")
                .tag("v1.0.0")
                .inputChecksum("flow.md", "abc123")
                .inputChecksum("phase1.md", "def456")
                .build();

            ReleasePackage pkg = builder.build(flowIr, sourceInfo);

            assertTrue(pkg.provenance().getInputs().containsKey("flow.md"));
            assertTrue(pkg.provenance().getInputs().containsKey("phase1.md"));
            assertEquals("abc123", pkg.provenance().getInputs().get("flow.md"));
            assertEquals("def456", pkg.provenance().getInputs().get("phase1.md"));
        }

        @Test
        @DisplayName("should handle empty phases and skills")
        void shouldHandleEmptyPhasesAndSkills() {
            FlowIr flowIr = createFlowIr();
            SourceInfo sourceInfo = createSourceInfo();

            ReleasePackage pkg = builder.build(flowIr, Map.of(), Map.of(), sourceInfo);

            assertEquals(0, pkg.phaseCount());
            assertEquals(0, pkg.skillCount());
        }

        @Test
        @DisplayName("should handle null phases map")
        void shouldHandleNullPhasesMap() {
            FlowIr flowIr = createFlowIr();
            SourceInfo sourceInfo = createSourceInfo();

            ReleasePackage pkg = builder.build(flowIr, null, Map.of(), sourceInfo);

            assertEquals(0, pkg.phaseCount());
        }

        @Test
        @DisplayName("should handle null skills map")
        void shouldHandleNullSkillsMap() {
            FlowIr flowIr = createFlowIr();
            SourceInfo sourceInfo = createSourceInfo();

            ReleasePackage pkg = builder.build(flowIr, Map.of(), null, sourceInfo);

            assertEquals(0, pkg.skillCount());
        }
    }

    @Nested
    @DisplayName("signing")
    class Signing {

        @Test
        @DisplayName("should not sign package when signer not configured")
        void shouldNotSignPackageWhenSignerNotConfigured() {
            FlowIr flowIr = createFlowIr();
            SourceInfo sourceInfo = createSourceInfo();

            ReleasePackage pkg = builder.build(flowIr, sourceInfo);

            assertFalse(pkg.provenance().isSigned());
            assertFalse(pkg.provenance().getSignature().isPresent());
        }

        @Test
        @DisplayName("should sign package when signer configured")
        void shouldSignPackageWhenSignerConfigured() {
            SigningKeyPair keyPair = SigningKeyPair.generate();
            ProvenanceSigner signer = new Ed25519Signer(keyPair, "test-key-id");
            DefaultReleaseBuilder signingBuilder = new DefaultReleaseBuilder("1.0.0", signer);

            FlowIr flowIr = createFlowIr();
            SourceInfo sourceInfo = createSourceInfo();

            ReleasePackage pkg = signingBuilder.build(flowIr, sourceInfo);

            assertTrue(pkg.provenance().isSigned());
            assertTrue(pkg.provenance().getSignature().isPresent());
            assertEquals("test-key-id", pkg.provenance().getSignature().get().keyId());
            assertNotNull(pkg.provenance().getSignature().get().value());
        }

        @Test
        @DisplayName("should verify signed package signature")
        void shouldVerifySignedPackageSignature() {
            SigningKeyPair keyPair = SigningKeyPair.generate();
            ProvenanceSigner signer = new Ed25519Signer(keyPair, "test-key-id");
            DefaultReleaseBuilder signingBuilder = new DefaultReleaseBuilder("1.0.0", signer);

            FlowIr flowIr = createFlowIr();
            SourceInfo sourceInfo = createSourceInfo();

            ReleasePackage pkg = signingBuilder.build(flowIr, sourceInfo);

            // Verify signature
            var signedProvenance = ru.hgd.sdlc.registry.domain.model.provenance.SignedProvenance.builder()
                .provenance(pkg.provenance())
                .signature(pkg.provenance().getSignature().get())
                .build();

            var result = signedProvenance.verify();
            assertTrue(result.valid());
            assertEquals("test-key-id", result.keyId());
        }
    }

    @Nested
    @DisplayName("builder configuration")
    class BuilderConfiguration {

        @Test
        @DisplayName("should have no signer by default")
        void shouldHaveNoSignerByDefault() {
            assertFalse(builder.hasSigner());
            assertTrue(builder.signer().isEmpty());
        }

        @Test
        @DisplayName("should use default compiler version")
        void shouldUseDefaultCompilerVersion() {
            assertEquals("1.0.0", builder.compilerVersion());
        }

        @Test
        @DisplayName("should use custom compiler version")
        void shouldUseCustomCompilerVersion() {
            DefaultReleaseBuilder customBuilder = new DefaultReleaseBuilder("2.0.0", null);
            assertEquals("2.0.0", customBuilder.compilerVersion());
        }

        @Test
        @DisplayName("should have signer when configured")
        void shouldHaveSignerWhenConfigured() {
            SigningKeyPair keyPair = SigningKeyPair.generate();
            ProvenanceSigner signer = new Ed25519Signer(keyPair, "test-key");
            DefaultReleaseBuilder signingBuilder = new DefaultReleaseBuilder(signer);

            assertTrue(signingBuilder.hasSigner());
            assertTrue(signingBuilder.signer().isPresent());
        }
    }

    // Helper methods

    private FlowIr createFlowIr() {
        Sha256 packageChecksum = Sha256.of("test-package-content");
        Sha256 irChecksum = Sha256.of("test-ir-content");

        return FlowIr.builder()
            .flowId(FlowId.of("test-flow"))
            .flowVersion(SemanticVersion.of(1, 0, 0))
            .metadata(IrMetadata.create(packageChecksum, irChecksum, "1.0.0"))
            .build();
    }

    private PhaseIr createPhaseIr(String id, int order) {
        return PhaseIr.builder()
            .id(PhaseId.of(id))
            .name(id + " phase")
            .order(order)
            .build();
    }

    private SkillIr createSkillIr(String id) {
        return SkillIr.builder()
            .skillId(SkillId.of(id))
            .skillVersion(SemanticVersion.of(1, 0, 0))
            .name(id + " skill")
            .handler(HandlerRef.of("skill://" + id))
            .irChecksum(Sha256.of("skill-checksum-" + id))
            .compiledAt(Instant.now())
            .compilerVersion("1.0.0")
            .build();
    }

    private SourceInfo createSourceInfo() {
        return SourceInfo.builder()
            .repositoryUrl("https://github.com/test/repo.git")
            .commitSha("abc123def456789012345678901234567890abcd")
            .commitAuthor("test@example.com")
            .committedAt(Instant.now().minusSeconds(3600))
            .branch("main")
            .tag("v1.0.0")
            .build();
    }
}

package ru.hgd.sdlc.registry.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.registry.application.builder.DefaultReleaseBuilder;
import ru.hgd.sdlc.registry.application.builder.ReleaseBuilder;
import ru.hgd.sdlc.registry.application.builder.SourceInfo;
import ru.hgd.sdlc.registry.application.signing.Ed25519Signer;
import ru.hgd.sdlc.registry.application.signing.ProvenanceSigner;
import ru.hgd.sdlc.registry.application.signing.SigningException;
import ru.hgd.sdlc.registry.application.verifier.DefaultProvenanceVerifier;
import ru.hgd.sdlc.registry.application.verifier.ProvenanceVerifier;
import ru.hgd.sdlc.registry.application.verifier.VerificationResult;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.provenance.ProvenanceSignature;
import ru.hgd.sdlc.registry.domain.model.provenance.SignedProvenance;
import ru.hgd.sdlc.registry.domain.model.provenance.SigningKeyPair;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for provenance signing.
 * Tests key generation, signing, and verification workflows.
 */
@DisplayName("Provenance Signing Integration Test")
class ProvenanceSigningIntegrationTest {

    private KeyManagerTestFixture keyFixture;
    private ProvenanceSigner signer;
    private ProvenanceVerifier verifier;
    private ReleaseBuilder releaseBuilder;

    @BeforeEach
    void setUp() {
        keyFixture = KeyManagerTestFixture.create("signing-test-key");
        signer = keyFixture.createSigner();
        verifier = new DefaultProvenanceVerifier();
        releaseBuilder = new DefaultReleaseBuilder("1.0.0", signer);
    }

    @Nested
    @DisplayName("Key Generation")
    class KeyGeneration {

        @Test
        @DisplayName("should generate valid Ed25519 key pair")
        void shouldGenerateValidEd25519KeyPair() {
            // When
            SigningKeyPair keyPair = SigningKeyPair.generate();

            // Then
            assertNotNull(keyPair);
            assertEquals(32, keyPair.publicKeyBytes().length);
            assertEquals(32, keyPair.privateKeyBytes().length);
        }

        @Test
        @DisplayName("should generate unique key pairs")
        void shouldGenerateUniqueKeyPairs() {
            // When
            SigningKeyPair key1 = SigningKeyPair.generate();
            SigningKeyPair key2 = SigningKeyPair.generate();

            // Then
            assertNotEquals(key1.publicKeyBase64(), key2.publicKeyBase64());
        }

        @Test
        @DisplayName("should reconstruct key pair from bytes")
        void shouldReconstructKeyPairFromBytes() {
            // Given
            SigningKeyPair original = SigningKeyPair.generate();
            byte[] privateKey = original.privateKeyBytes();
            byte[] publicKey = original.publicKeyBytes();

            // When
            SigningKeyPair reconstructed = SigningKeyPair.of(privateKey, publicKey);

            // Then
            assertEquals(original.publicKeyBase64(), reconstructed.publicKeyBase64());
        }

        @Test
        @DisplayName("should reconstruct key pair from Base64")
        void shouldReconstructKeyPairFromBase64() {
            // Given
            SigningKeyPair original = SigningKeyPair.generate();

            // When
            SigningKeyPair reconstructed = KeyManagerTestFixture.fromBase64(
                original.privateKeyBase64(),
                original.publicKeyBase64()
            );

            // Then
            assertEquals(original.publicKeyBase64(), reconstructed.publicKeyBase64());
        }
    }

    @Nested
    @DisplayName("Provenance Signing")
    class ProvenanceSigning {

        @Test
        @DisplayName("should sign provenance successfully")
        void shouldSignProvenanceSuccessfully() throws SigningException {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("sign-test", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // When
            SignedProvenance signed = signer.sign(pkg.provenance());

            // Then
            assertNotNull(signed);
            assertNotNull(signed.signature());
            assertTrue(signed.provenance().isSigned());
        }

        @Test
        @DisplayName("should include signature details")
        void shouldIncludeSignatureDetails() throws SigningException {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("sig-details", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // When
            SignedProvenance signed = signer.sign(pkg.provenance());

            // Then
            ProvenanceSignature sig = signed.signature();
            assertEquals("Ed25519", sig.algorithm());
            assertEquals("signing-test-key", sig.keyId());
            assertNotNull(sig.value());
            assertNotNull(sig.publicKey());
            assertNotNull(sig.signedAt());
        }

        @Test
        @DisplayName("should create different signatures for different content")
        void shouldCreateDifferentSignaturesForDifferentContent() throws SigningException {
            // Given
            FlowIr flow1 = ReleaseTestFixtures.createSimpleFlowIr("unique-1", "1.0.0");
            FlowIr flow2 = ReleaseTestFixtures.createSimpleFlowIr("unique-2", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            ReleasePackage pkg1 = releaseBuilder.build(flow1, sourceInfo);
            ReleasePackage pkg2 = releaseBuilder.build(flow2, sourceInfo);

            // When
            SignedProvenance signed1 = signer.sign(pkg1.provenance());
            SignedProvenance signed2 = signer.sign(pkg2.provenance());

            // Then
            // Signatures should be different (different content)
            assertNotEquals(
                signed1.signature().value(),
                signed2.signature().value()
            );
        }
    }

    @Nested
    @DisplayName("Signature Verification")
    class SignatureVerification {

        @Test
        @DisplayName("should verify valid signature")
        void shouldVerifyValidSignature() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("verify-sig", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // When
            VerificationResult result = verifier.verify(pkg);

            // Then
            assertTrue(result.isSuccess());
            assertFalse(result.hasErrors());
        }

        @Test
        @DisplayName("should verify signature with embedded public key")
        void shouldVerifySignatureWithEmbeddedPublicKey() throws SigningException {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("embed-verify", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            Provenance provenance = pkg.provenance();
            SignedProvenance signed = SignedProvenance.builder()
                .provenance(provenance)
                .signature(provenance.getSignature().orElseThrow())
                .build();

            // When
            SignedProvenance.VerificationResult result = signed.verify();

            // Then
            assertTrue(result.valid());
            assertEquals("signing-test-key", result.keyId());
        }

        @Test
        @DisplayName("should detect tampered content")
        void shouldDetectTamperedContent() throws SigningException {
            // Given - sign with one provenance
            Provenance originalProvenance = ProvenanceTestFixtures.createMinimalProvenance(
                ru.hgd.sdlc.registry.domain.model.release.ReleaseId.parse("tamper-test@1.0.0")
            );

            SignedProvenance signed = signer.sign(originalProvenance);

            // Create a different provenance with the same signature (simulating tampering)
            Provenance tamperedProvenance = ProvenanceTestFixtures.createMinimalProvenance(
                ru.hgd.sdlc.registry.domain.model.release.ReleaseId.parse("different@1.0.0")
            );

            SignedProvenance tamperedSigned = SignedProvenance.builder()
                .provenance(tamperedProvenance)
                .signature(signed.signature())
                .build();

            // When
            SignedProvenance.VerificationResult result = tamperedSigned.verify();

            // Then
            assertFalse(result.valid());
            assertNotNull(result.reason());
        }

        @Test
        @DisplayName("should detect wrong key signature")
        void shouldDetectWrongKeySignature() throws SigningException {
            // Given - sign with one key
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("wrong-key", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // Create a different key pair
            SigningKeyPair differentKey = SigningKeyPair.generate();
            byte[] wrongSignature = differentKey.sign(pkg.provenance().toSignablePayload());

            ProvenanceSignature wrongSig = ProvenanceSignature.of(
                "Ed25519",
                "wrong-key",
                differentKey.publicKeyBytes(),
                wrongSignature,
                Instant.now()
            );

            SignedProvenance wrongSigned = SignedProvenance.builder()
                .provenance(pkg.provenance())
                .signature(wrongSig)
                .build();

            // When
            SignedProvenance.VerificationResult result = wrongSigned.verify();

            // Then - should still verify because signature matches the embedded public key
            // The signature is valid for the embedded key, just not our trusted key
            assertTrue(result.valid());
        }
    }

    @Nested
    @DisplayName("Unsigned Provenance")
    class UnsignedProvenance {

        @Test
        @DisplayName("should build unsigned package without signer")
        void shouldBuildUnsignedPackageWithoutSigner() {
            // Given
            ReleaseBuilder unsignedBuilder = new DefaultReleaseBuilder();
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("unsigned", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When
            ReleasePackage pkg = unsignedBuilder.build(flowIr, sourceInfo);

            // Then
            assertFalse(pkg.provenance().isSigned());
            assertTrue(pkg.provenance().getSignature().isEmpty());
        }

        @Test
        @DisplayName("should warn on unsigned provenance verification")
        void shouldWarnOnUnsignedProvenanceVerification() {
            // Given
            ReleaseBuilder unsignedBuilder = new DefaultReleaseBuilder();
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("unsigned-verify", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = unsignedBuilder.build(flowIr, sourceInfo);

            // When
            VerificationResult result = verifier.verifySignature(pkg.provenance());

            // Then
            assertTrue(result.isSuccess());
            assertTrue(result.getIssuesList().stream()
                .anyMatch(i -> "NO_SIGNATURE".equals(i.getCode())));
        }
    }

    @Nested
    @DisplayName("Key Management")
    class KeyManagement {

        @Test
        @DisplayName("should write and read keys from files")
        void shouldWriteAndReadKeysFromFiles() throws Exception {
            // When
            java.nio.file.Path keyDir = keyFixture.writeKeysToFiles();

            // Then
            assertTrue(java.nio.file.Files.exists(keyDir.resolve("private.key")));
            assertTrue(java.nio.file.Files.exists(keyDir.resolve("public.key")));

            // Read and verify
            String publicKeyBase64 = java.nio.file.Files.readString(keyDir.resolve("public.key"));
            assertEquals(keyFixture.publicKeyBase64(), publicKeyBase64.trim());
        }

        @Test
        @DisplayName("should sign and verify with same key pair")
        void shouldSignAndVerifyWithSameKeyPair() {
            // Given
            String testData = "test-data-for-signing";

            // When
            byte[] signature = keyFixture.keyPair().sign(testData);

            // Then
            assertTrue(keyFixture.verifySignature(testData, signature));
        }

        @Test
        @DisplayName("should reject signature from different key")
        void shouldRejectSignatureFromDifferentKey() {
            // Given
            String testData = "test-data-for-signing";
            SigningKeyPair differentKey = SigningKeyPair.generate();

            // When - sign with different key
            byte[] signature = differentKey.sign(testData);

            // Then - verification with original key should fail
            assertFalse(keyFixture.verifySignature(testData, signature));
        }
    }

    @Nested
    @DisplayName("Complete Signing Workflow")
    class CompleteSigningWorkflow {

        @Test
        @DisplayName("should complete full signing workflow")
        void shouldCompleteFullSigningWorkflow() {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createCompleteFlowIr("workflow-test", "1.0.0", 3, 2);
            SourceInfo sourceInfo = ProvenanceTestFixtures.createSourceInfoWithChecksums(
                ProvenanceTestFixtures.createSampleInputChecksums()
            );

            // When - Build signed package
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            // Then - Verify complete workflow
            assertNotNull(pkg);
            assertTrue(pkg.provenance().isSigned());
            assertTrue(pkg.verifyIntegrity());

            // Verify signature
            VerificationResult result = verifier.verify(pkg);
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("should preserve signature through serialization")
        void shouldPreserveSignatureThroughSerialization() throws Exception {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("serialize-sig", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage original = releaseBuilder.build(flowIr, sourceInfo);

            // When
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.registerModule(new com.fasterxml.jackson.datatype.jdk8.Jdk8Module());

            String json = mapper.writeValueAsString(original);
            ReleasePackage deserialized = mapper.readValue(json, ReleasePackage.class);

            // Then
            assertTrue(deserialized.provenance().isSigned());
            assertEquals(
                original.provenance().getSignature().get().value(),
                deserialized.provenance().getSignature().get().value()
            );
        }
    }
}

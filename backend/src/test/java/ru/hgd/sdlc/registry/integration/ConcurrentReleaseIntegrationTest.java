package ru.hgd.sdlc.registry.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.model.ir.PhaseIr;
import ru.hgd.sdlc.registry.application.builder.DefaultReleaseBuilder;
import ru.hgd.sdlc.registry.application.builder.ReleaseBuilder;
import ru.hgd.sdlc.registry.application.builder.SourceInfo;
import ru.hgd.sdlc.registry.application.lockfile.DefaultLockfileGenerator;
import ru.hgd.sdlc.registry.application.lockfile.Lockfile;
import ru.hgd.sdlc.registry.application.signing.Ed25519Signer;
import ru.hgd.sdlc.registry.application.signing.ProvenanceSigner;
import ru.hgd.sdlc.registry.application.verifier.DefaultProvenanceVerifier;
import ru.hgd.sdlc.registry.application.verifier.ProvenanceVerifier;
import ru.hgd.sdlc.registry.application.verifier.VerificationResult;
import ru.hgd.sdlc.registry.domain.model.provenance.SigningKeyPair;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for concurrent release operations.
 * Tests thread-safety and isolation of concurrent builds.
 */
@DisplayName("Concurrent Release Integration Test")
class ConcurrentReleaseIntegrationTest {

    private ReleaseBuilder releaseBuilder;
    private DefaultLockfileGenerator lockfileGenerator;
    private ProvenanceVerifier verifier;

    @BeforeEach
    void setUp() {
        SigningKeyPair keyPair = SigningKeyPair.generate();
        ProvenanceSigner signer = new Ed25519Signer(keyPair, "concurrent-test-key");
        releaseBuilder = new DefaultReleaseBuilder("1.0.0", signer);
        lockfileGenerator = new DefaultLockfileGenerator();
        verifier = new DefaultProvenanceVerifier();
    }

    @Nested
    @DisplayName("Concurrent Build Operations")
    class ConcurrentBuildOperations {

        @Test
        @DisplayName("should build multiple releases concurrently without conflicts")
        void shouldBuildMultipleReleasesConcurrentlyWithoutConflicts() throws Exception {
            // Given
            int numReleases = 10;
            ExecutorService executor = Executors.newFixedThreadPool(4);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numReleases);
            List<Future<ReleasePackage>> futures = new ArrayList<>();
            AtomicInteger successCount = new AtomicInteger(0);

            // When - submit concurrent build tasks
            for (int i = 0; i < numReleases; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready

                        FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr(
                            "concurrent-flow-" + index,
                            "1.0.0"
                        );
                        SourceInfo sourceInfo = SourceInfo.builder()
                            .repositoryUrl("https://github.com/test/repo.git")
                            .commitSha(String.format("%040d", index))
                            .commitAuthor("test@example.com")
                            .committedAt(Instant.now())
                            .build();

                        ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);
                        successCount.incrementAndGet();
                        return pkg;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                }));
            }

            startLatch.countDown(); // Start all threads
            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Then
            assertEquals(numReleases, successCount.get());

            List<ReleasePackage> packages = new ArrayList<>();
            for (Future<ReleasePackage> future : futures) {
                packages.add(future.get());
            }

            // Verify all packages are unique and valid
            assertEquals(numReleases, packages.size());
            for (ReleasePackage pkg : packages) {
                assertNotNull(pkg);
                assertTrue(pkg.provenance().isSigned());
            }
        }

        @Test
        @DisplayName("should maintain data integrity under concurrent load")
        void shouldMaintainDataIntegrityUnderConcurrentLoad() throws Exception {
            // Given
            int numThreads = 8;
            int iterationsPerThread = 5;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            List<Future<List<ReleasePackage>>> futures = new ArrayList<>();

            // When
            for (int t = 0; t < numThreads; t++) {
                final int threadId = t;
                futures.add(executor.submit(() -> {
                    List<ReleasePackage> packages = new ArrayList<>();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr(
                            "load-test-" + threadId + "-" + i,
                            "1.0.0"
                        );
                        SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
                        packages.add(releaseBuilder.build(flowIr, sourceInfo));
                    }
                    return packages;
                }));
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));

            // Then
            int totalPackages = 0;
            for (Future<List<ReleasePackage>> future : futures) {
                List<ReleasePackage> packages = future.get();
                for (ReleasePackage pkg : packages) {
                    totalPackages++;
                    assertNotNull(pkg);
                    assertNotNull(pkg.id());
                    assertTrue(pkg.provenance().isSigned());
                }
            }

            assertEquals(numThreads * iterationsPerThread, totalPackages);
        }
    }

    @Nested
    @DisplayName("Concurrent Verification")
    class ConcurrentVerification {

        @Test
        @DisplayName("should verify multiple packages concurrently")
        void shouldVerifyMultiplePackagesConcurrently() throws Exception {
            // Given
            int numPackages = 20;
            List<ReleasePackage> packages = new ArrayList<>();

            for (int i = 0; i < numPackages; i++) {
                FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("verify-concurrent-" + i, "1.0.0");
                SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
                packages.add(releaseBuilder.build(flowIr, sourceInfo));
            }

            // When - verify all concurrently
            ExecutorService executor = Executors.newFixedThreadPool(4);
            List<Future<VerificationResult>> futures = new ArrayList<>();

            for (ReleasePackage pkg : packages) {
                futures.add(executor.submit(() -> verifier.verify(pkg)));
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

            // Then
            int successCount = 0;
            for (Future<VerificationResult> future : futures) {
                VerificationResult result = future.get();
                if (result.isSuccess()) {
                    successCount++;
                }
            }

            assertEquals(numPackages, successCount);
        }
    }

    @Nested
    @DisplayName("Concurrent Lockfile Generation")
    class ConcurrentLockfileGeneration {

        @Test
        @DisplayName("should generate lockfiles concurrently")
        void shouldGenerateLockfilesConcurrently() throws Exception {
            // Given
            int numLockfiles = 10;
            List<List<ReleasePackage>> packageGroups = new ArrayList<>();

            for (int g = 0; g < numLockfiles; g++) {
                List<ReleasePackage> group = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr(
                        "lockfile-group-" + g + "-" + i, "1.0.0"
                    );
                    SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
                    group.add(releaseBuilder.build(flowIr, sourceInfo));
                }
                packageGroups.add(group);
            }

            // When
            ExecutorService executor = Executors.newFixedThreadPool(4);
            List<Future<Lockfile>> futures = new ArrayList<>();

            for (List<ReleasePackage> group : packageGroups) {
                futures.add(executor.submit(() -> lockfileGenerator.generate(group)));
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

            // Then
            assertEquals(numLockfiles, futures.size());
            for (Future<Lockfile> future : futures) {
                Lockfile lockfile = future.get();
                assertNotNull(lockfile);
                assertNotNull(lockfile.checksum());
                assertEquals(3, lockfile.entries().size());
            }
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {

        @Test
        @DisplayName("builder should be thread-safe")
        void builderShouldBeThreadSafe() throws Exception {
            // Given
            int numThreads = 10;
            FlowIr sharedFlowIr = ReleaseTestFixtures.createSimpleFlowIr("shared-flow", "1.0.0");
            SourceInfo sharedSourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

            // When - use the same builder instance concurrently
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            List<Future<ReleasePackage>> futures = new ArrayList<>();

            for (int i = 0; i < numThreads; i++) {
                futures.add(executor.submit(() ->
                    releaseBuilder.build(sharedFlowIr, sharedSourceInfo)
                ));
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

            // Then - all should succeed with consistent results
            for (Future<ReleasePackage> future : futures) {
                ReleasePackage pkg = future.get();
                assertNotNull(pkg);
                assertEquals("shared-flow@1.0.0", pkg.id().canonicalId());
            }
        }

        @Test
        @DisplayName("verifier should be thread-safe")
        void verifierShouldBeThreadSafe() throws Exception {
            // Given
            FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("verifier-threadsafe", "1.0.0");
            SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
            ReleasePackage pkg = releaseBuilder.build(flowIr, sourceInfo);

            int numThreads = 20;
            ExecutorService executor = Executors.newFixedThreadPool(4);
            List<Future<VerificationResult>> futures = new ArrayList<>();

            // When - verify the same package concurrently
            for (int i = 0; i < numThreads; i++) {
                futures.add(executor.submit(() -> verifier.verify(pkg)));
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

            // Then - all should return same result
            for (Future<VerificationResult> future : futures) {
                VerificationResult result = future.get();
                assertTrue(result.isSuccess());
            }
        }
    }

    @Nested
    @DisplayName("Race Condition Prevention")
    class RaceConditionPrevention {

        @Test
        @DisplayName("should handle simultaneous builds with same flow ID")
        void shouldHandleSimultaneousBuildsWithSameFlowId() throws Exception {
            // Given - two threads building the same flow concurrently
            int numThreads = 5;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<ReleasePackage>> futures = new ArrayList<>();

            // When
            for (int i = 0; i < numThreads; i++) {
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    FlowIr flowIr = ReleaseTestFixtures.createSimpleFlowIr("race-flow", "1.0.0");
                    SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();
                    return releaseBuilder.build(flowIr, sourceInfo);
                }));
            }

            startLatch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

            // Then - all should succeed
            for (Future<ReleasePackage> future : futures) {
                ReleasePackage pkg = future.get();
                assertNotNull(pkg);
                assertEquals("race-flow@1.0.0", pkg.id().canonicalId());
            }
        }

        @Test
        @DisplayName("should handle concurrent builds with phases and skills")
        void shouldHandleConcurrentBuildsWithPhasesAndSkills() throws Exception {
            // Given
            int numBuilds = 8;
            ExecutorService executor = Executors.newFixedThreadPool(4);
            List<Future<ReleasePackage>> futures = new ArrayList<>();

            // When
            for (int i = 0; i < numBuilds; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    FlowIr flowIr = ReleaseTestFixtures.createFlowIrWithPhases(
                        "complex-concurrent-" + index,
                        "1.0.0",
                        List.of("phase-a", "phase-b", "phase-c")
                    );

                    Map<PhaseId, PhaseIr> phases = ReleaseTestFixtures.createPhasesMap(
                        "phase-a", "phase-b", "phase-c"
                    );

                    Map<SkillId, SkillIr> skills = ReleaseTestFixtures.createSkillsMap(
                        "skill-x", "skill-y"
                    );

                    SourceInfo sourceInfo = ProvenanceTestFixtures.createDefaultSourceInfo();

                    return releaseBuilder.build(flowIr, phases, skills, sourceInfo);
                }));
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

            // Then
            for (Future<ReleasePackage> future : futures) {
                ReleasePackage pkg = future.get();
                assertNotNull(pkg);
                assertEquals(3, pkg.phaseCount());
                assertEquals(2, pkg.skillCount());
            }
        }
    }
}

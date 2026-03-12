package ru.hgd.sdlc.registry.integration;

import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for registry integration tests.
 * Provides common configuration using Testcontainers for PostgreSQL.
 *
 * <p>Subclasses should use the fixture classes:
 * <ul>
 *   <li>{@link ReleaseTestFixtures} - for creating test flow and skill IRs</li>
 *   <li>{@link ProvenanceTestFixtures} - for creating test provenance records</li>
 *   <li>{@link KeyManagerTestFixture} - for setting up test signing keys</li>
 * </ul>
 */
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Registry Integration Tests")
public abstract class RegistryIntegrationTest {

    // Common test constants
    protected static final String TEST_FLOW_ID = "test-flow";
    protected static final String TEST_VERSION = "1.0.0";
    protected static final String TEST_SKILL_ID = "test-skill";
    protected static final String TEST_REPO_URL = "https://github.com/test/repo.git";
    protected static final String TEST_COMMIT_SHA = "abc123def456789012345678901234567890abcd";

    /**
     * Creates a test flow IR with default settings.
     *
     * @return a test FlowIr
     */
    protected ru.hgd.sdlc.compiler.domain.model.ir.FlowIr createTestFlowIr() {
        return ReleaseTestFixtures.createSimpleFlowIr(TEST_FLOW_ID, TEST_VERSION);
    }

    /**
     * Creates a test source info with default settings.
     *
     * @return a test SourceInfo
     */
    protected ru.hgd.sdlc.registry.application.builder.SourceInfo createTestSourceInfo() {
        return ProvenanceTestFixtures.createDefaultSourceInfo();
    }
}

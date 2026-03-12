package ru.hgd.sdlc.registry.application.builder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReleaseBuilder interface")
class ReleaseBuilderTest {

    @Test
    @DisplayName("should provide default build method with empty maps")
    void shouldProvideDefaultBuildMethodWithEmptyMaps() {
        ReleaseBuilder builder = new TestReleaseBuilder();

        FlowIr flowIr = ru.hgd.sdlc.compiler.domain.model.ir.FlowIr.builder()
            .flowId(ru.hgd.sdlc.compiler.domain.model.authored.FlowId.of("test-flow"))
            .flowVersion(ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion.of(1, 0, 0))
            .metadata(ru.hgd.sdlc.compiler.domain.model.ir.IrMetadata.create(
                ru.hgd.sdlc.shared.hashing.Sha256.of("pkg"),
                ru.hgd.sdlc.shared.hashing.Sha256.of("ir"),
                "1.0.0"
            ))
            .build();
        SourceInfo sourceInfo = SourceInfo.of(
            "https://github.com/test/repo.git",
            "abc123def456789012345678901234567890abcd",
            "test@example.com",
            java.time.Instant.now()
        );

        // Default method should call the full build with empty maps
        ReleasePackage pkg = builder.build(flowIr, sourceInfo);

        assertNotNull(pkg);
        assertEquals(0, pkg.phaseCount());
        assertEquals(0, pkg.skillCount());
    }

    /**
     * Test implementation that delegates to DefaultReleaseBuilder.
     */
    private static class TestReleaseBuilder implements ReleaseBuilder {
        private final DefaultReleaseBuilder delegate = new DefaultReleaseBuilder();

        @Override
        public ReleasePackage build(FlowIr flowIr,
                                     Map<ru.hgd.sdlc.compiler.domain.model.authored.PhaseId,
                                         ru.hgd.sdlc.compiler.domain.model.ir.PhaseIr> phases,
                                     Map<ru.hgd.sdlc.compiler.domain.model.authored.SkillId,
                                         ru.hgd.sdlc.compiler.domain.compiler.SkillIr> skills,
                                     SourceInfo sourceInfo) throws ReleaseBuildException {
            return delegate.build(flowIr, phases, skills, sourceInfo);
        }
    }
}

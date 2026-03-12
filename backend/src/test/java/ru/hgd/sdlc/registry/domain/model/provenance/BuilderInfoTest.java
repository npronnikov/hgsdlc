package ru.hgd.sdlc.registry.domain.model.provenance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BuilderInfo.
 */
class BuilderInfoTest {

    @Test
    void shouldCreateBuilderInfoWithRequiredFields() {
        BuilderInfo info = BuilderInfo.of("sdlc-registry", "1.0.0");

        assertEquals("sdlc-registry", info.name());
        assertEquals("1.0.0", info.version());
        assertTrue(info.hostnameOptional().isEmpty());
    }

    @Test
    void shouldCreateBuilderInfoWithHostname() {
        BuilderInfo info = BuilderInfo.of("sdlc-registry", "1.0.0", "build-server-01");

        assertEquals("sdlc-registry", info.name());
        assertEquals("1.0.0", info.version());
        assertTrue(info.hostnameOptional().isPresent());
        assertEquals("build-server-01", info.hostnameOptional().get());
    }

    @Test
    void shouldCreateBuilderInfoUsingBuilder() {
        BuilderInfo info = BuilderInfo.builder()
            .name("ci-pipeline")
            .version("2.0.0")
            .hostname("ci-01.example.com")
            .build();

        assertEquals("ci-pipeline", info.name());
        assertEquals("2.0.0", info.version());
        assertEquals("ci-01.example.com", info.hostname());
    }

    @Test
    void shouldRejectNullName() {
        assertThrows(NullPointerException.class, () ->
            BuilderInfo.builder()
                .version("1.0.0")
                .build()
        );
    }

    @Test
    void shouldRejectNullVersion() {
        assertThrows(NullPointerException.class, () ->
            BuilderInfo.builder()
                .name("test")
                .build()
        );
    }

    @Test
    void shouldImplementEqualsAndHashCode() {
        BuilderInfo info1 = BuilderInfo.of("builder", "1.0.0", "host1");
        BuilderInfo info2 = BuilderInfo.of("builder", "1.0.0", "host1");
        BuilderInfo info3 = BuilderInfo.of("builder", "1.0.0", "host2");

        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
        assertNotEquals(info1, info3);
    }

    @Test
    void shouldCreateModifiedCopy() {
        BuilderInfo original = BuilderInfo.of("builder", "1.0.0");
        BuilderInfo modified = original.toBuilder()
            .hostname("new-host")
            .build();

        assertEquals("builder", modified.name());
        assertEquals("1.0.0", modified.version());
        assertEquals("new-host", modified.hostname());
        assertTrue(original.hostnameOptional().isEmpty());
    }
}

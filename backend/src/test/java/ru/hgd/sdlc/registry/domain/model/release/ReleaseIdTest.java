package ru.hgd.sdlc.registry.domain.model.release;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReleaseId")
class ReleaseIdTest {

    @Nested
    @DisplayName("of(FlowId, ReleaseVersion)")
    class OfFlowIdAndVersion {

        @Test
        @DisplayName("should create from flow ID and version")
        void shouldCreateFromFlowIdAndVersion() {
            FlowId flowId = FlowId.of("my-flow");
            ReleaseVersion version = ReleaseVersion.of("1.2.3");

            ReleaseId releaseId = ReleaseId.of(flowId, version);

            assertEquals(flowId, releaseId.flowId());
            assertEquals(version, releaseId.version());
        }

        @Test
        @DisplayName("should reject null flow ID")
        void shouldRejectNullFlowId() {
            ReleaseVersion version = ReleaseVersion.of("1.2.3");

            assertThrows(IllegalArgumentException.class, () -> ReleaseId.of(null, version));
        }

        @Test
        @DisplayName("should reject null version")
        void shouldRejectNullVersion() {
            FlowId flowId = FlowId.of("my-flow");

            assertThrows(IllegalArgumentException.class, () -> ReleaseId.of(flowId, null));
        }
    }

    @Nested
    @DisplayName("parse(String)")
    class ParseString {

        @Test
        @DisplayName("should parse canonical ID")
        void shouldParseCanonicalId() {
            ReleaseId releaseId = ReleaseId.parse("my-flow@1.2.3");

            assertEquals("my-flow", releaseId.flowId().value());
            assertEquals("1.2.3", releaseId.version().formatted());
        }

        @Test
        @DisplayName("should parse with prerelease version")
        void shouldParseWithPrerelease() {
            ReleaseId releaseId = ReleaseId.parse("my-flow@1.0.0-alpha");

            assertEquals("my-flow", releaseId.flowId().value());
            assertEquals("1.0.0-alpha", releaseId.version().formatted());
            assertTrue(releaseId.version().isPrerelease());
        }

        @Test
        @DisplayName("should parse flow ID with hyphens and underscores")
        void shouldParseComplexFlowId() {
            ReleaseId releaseId = ReleaseId.parse("my_complex-flow@2.0.0");

            assertEquals("my_complex-flow", releaseId.flowId().value());
        }

        @Test
        @DisplayName("should reject null")
        void shouldRejectNull() {
            assertThrows(IllegalArgumentException.class, () -> ReleaseId.parse(null));
        }

        @Test
        @DisplayName("should reject blank")
        void shouldRejectBlank() {
            assertThrows(IllegalArgumentException.class, () -> ReleaseId.parse("   "));
        }

        @Test
        @DisplayName("should reject missing @")
        void shouldRejectMissingAt() {
            assertThrows(IllegalArgumentException.class, () -> ReleaseId.parse("my-flow1.2.3"));
        }

        @Test
        @DisplayName("should reject @ at start")
        void shouldRejectAtStart() {
            assertThrows(IllegalArgumentException.class, () -> ReleaseId.parse("@1.2.3"));
        }

        @Test
        @DisplayName("should reject @ at end")
        void shouldRejectAtEnd() {
            assertThrows(IllegalArgumentException.class, () -> ReleaseId.parse("my-flow@"));
        }
    }

    @Nested
    @DisplayName("canonicalId()")
    class CanonicalId {

        @Test
        @DisplayName("should return flowId@version")
        void shouldReturnFlowIdAtVersion() {
            ReleaseId releaseId = ReleaseId.of(FlowId.of("my-flow"), ReleaseVersion.of("1.2.3"));

            assertEquals("my-flow@1.2.3", releaseId.canonicalId());
        }

        @Test
        @DisplayName("should include prerelease in canonical ID")
        void shouldIncludePrerelease() {
            ReleaseId releaseId = ReleaseId.of(FlowId.of("my-flow"), ReleaseVersion.of("1.0.0-beta"));

            assertEquals("my-flow@1.0.0-beta", releaseId.canonicalId());
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("should be equal for same flow ID and version")
        void shouldBeEqualForSameComponents() {
            ReleaseId id1 = ReleaseId.parse("my-flow@1.2.3");
            ReleaseId id2 = ReleaseId.parse("my-flow@1.2.3");

            assertEquals(id1, id2);
            assertEquals(id1.hashCode(), id2.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different flow ID")
        void shouldNotBeEqualForDifferentFlowId() {
            ReleaseId id1 = ReleaseId.parse("flow-a@1.2.3");
            ReleaseId id2 = ReleaseId.parse("flow-b@1.2.3");

            assertNotEquals(id1, id2);
        }

        @Test
        @DisplayName("should not be equal for different version")
        void shouldNotBeEqualForDifferentVersion() {
            ReleaseId id1 = ReleaseId.parse("my-flow@1.2.3");
            ReleaseId id2 = ReleaseId.parse("my-flow@1.2.4");

            assertNotEquals(id1, id2);
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToString {

        @Test
        @DisplayName("should include canonical ID")
        void shouldIncludeCanonicalId() {
            ReleaseId releaseId = ReleaseId.parse("my-flow@1.2.3");

            String str = releaseId.toString();

            assertTrue(str.contains("my-flow@1.2.3"));
        }
    }
}

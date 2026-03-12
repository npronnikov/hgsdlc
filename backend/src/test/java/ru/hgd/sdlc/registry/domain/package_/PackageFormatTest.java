package ru.hgd.sdlc.registry.domain.package_;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PackageFormat")
class PackageFormatTest {

    @Nested
    @DisplayName("constants")
    class Constants {

        @Test
        @DisplayName("should define correct format version")
        void shouldDefineCorrectFormatVersion() {
            assertEquals(1, PackageFormat.FORMAT_VERSION);
        }

        @Test
        @DisplayName("should define correct file names")
        void shouldDefineCorrectFileNames() {
            assertEquals("release-manifest.json", PackageFormat.FILE_MANIFEST);
            assertEquals("flow.ir.json", PackageFormat.FILE_FLOW_IR);
            assertEquals("provenance.json", PackageFormat.FILE_PROVENANCE);
            assertEquals("checksums.sha256", PackageFormat.FILE_CHECKSUMS);
        }

        @Test
        @DisplayName("should define correct directories")
        void shouldDefineCorrectDirectories() {
            assertEquals("phases/", PackageFormat.DIR_PHASES);
            assertEquals("skills/", PackageFormat.DIR_SKILLS);
        }

        @Test
        @DisplayName("should define correct IR extension")
        void shouldDefineCorrectIrExtension() {
            assertEquals(".ir.json", PackageFormat.IR_EXTENSION);
        }
    }

    @Nested
    @DisplayName("phasePath(String)")
    class PhasePath {

        @Test
        @DisplayName("should create correct phase path")
        void shouldCreateCorrectPhasePath() {
            assertEquals("phases/setup.ir.json", PackageFormat.phasePath("setup"));
            assertEquals("phases/develop.ir.json", PackageFormat.phasePath("develop"));
            assertEquals("phases/review.ir.json", PackageFormat.phasePath("review"));
        }
    }

    @Nested
    @DisplayName("skillPath(String)")
    class SkillPath {

        @Test
        @DisplayName("should create correct skill path")
        void shouldCreateCorrectSkillPath() {
            assertEquals("skills/code-gen.ir.json", PackageFormat.skillPath("code-gen"));
            assertEquals("skills/test-runner.ir.json", PackageFormat.skillPath("test-runner"));
        }
    }

    @Nested
    @DisplayName("extractPhaseId(String)")
    class ExtractPhaseId {

        @Test
        @DisplayName("should extract phase ID from valid path")
        void shouldExtractFromValidPath() {
            assertEquals("setup", PackageFormat.extractPhaseId("phases/setup.ir.json"));
            assertEquals("develop", PackageFormat.extractPhaseId("phases/develop.ir.json"));
        }

        @Test
        @DisplayName("should return null for invalid path")
        void shouldReturnNullForInvalidPath() {
            assertNull(PackageFormat.extractPhaseId(null));
            assertNull(PackageFormat.extractPhaseId(""));
            assertNull(PackageFormat.extractPhaseId("phases/setup.json"));
            assertNull(PackageFormat.extractPhaseId("skills/setup.ir.json"));
            assertNull(PackageFormat.extractPhaseId("setup.ir.json"));
        }
    }

    @Nested
    @DisplayName("extractSkillId(String)")
    class ExtractSkillId {

        @Test
        @DisplayName("should extract skill ID from valid path")
        void shouldExtractFromValidPath() {
            assertEquals("code-gen", PackageFormat.extractSkillId("skills/code-gen.ir.json"));
            assertEquals("test-runner", PackageFormat.extractSkillId("skills/test-runner.ir.json"));
        }

        @Test
        @DisplayName("should return null for invalid path")
        void shouldReturnNullForInvalidPath() {
            assertNull(PackageFormat.extractSkillId(null));
            assertNull(PackageFormat.extractSkillId(""));
            assertNull(PackageFormat.extractSkillId("skills/code-gen.json"));
            assertNull(PackageFormat.extractSkillId("phases/code-gen.ir.json"));
            assertNull(PackageFormat.extractSkillId("code-gen.ir.json"));
        }
    }

    @Nested
    @DisplayName("isPhasePath(String)")
    class IsPhasePath {

        @Test
        @DisplayName("should return true for valid phase paths")
        void shouldReturnTrueForValidPaths() {
            assertTrue(PackageFormat.isPhasePath("phases/setup.ir.json"));
            assertTrue(PackageFormat.isPhasePath("phases/develop.ir.json"));
        }

        @Test
        @DisplayName("should return false for invalid phase paths")
        void shouldReturnFalseForInvalidPaths() {
            assertFalse(PackageFormat.isPhasePath(null));
            assertFalse(PackageFormat.isPhasePath(""));
            assertFalse(PackageFormat.isPhasePath("phases/setup.json"));
            assertFalse(PackageFormat.isPhasePath("skills/setup.ir.json"));
        }
    }

    @Nested
    @DisplayName("isSkillPath(String)")
    class IsSkillPath {

        @Test
        @DisplayName("should return true for valid skill paths")
        void shouldReturnTrueForValidPaths() {
            assertTrue(PackageFormat.isSkillPath("skills/code-gen.ir.json"));
            assertTrue(PackageFormat.isSkillPath("skills/test-runner.ir.json"));
        }

        @Test
        @DisplayName("should return false for invalid skill paths")
        void shouldReturnFalseForInvalidPaths() {
            assertFalse(PackageFormat.isSkillPath(null));
            assertFalse(PackageFormat.isSkillPath(""));
            assertFalse(PackageFormat.isSkillPath("skills/code-gen.json"));
            assertFalse(PackageFormat.isSkillPath("phases/code-gen.ir.json"));
        }
    }
}

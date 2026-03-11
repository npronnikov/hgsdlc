package ru.hgd.sdlc.compiler.domain.model.authored;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HandlerRef")
class HandlerRefTest {

    @Nested
    @DisplayName("of(String)")
    class OfString {

        @Test
        @DisplayName("parses skill reference")
        void parsesSkillReference() {
            HandlerRef ref = HandlerRef.of("skill://my-skill");

            assertEquals(HandlerKind.SKILL, ref.kind());
            assertEquals("my-skill", ref.reference());
            assertEquals("skill://my-skill", ref.toString());
        }

        @Test
        @DisplayName("parses builtin reference")
        void parsesBuiltinReference() {
            HandlerRef ref = HandlerRef.of("builtin://validate");

            assertEquals(HandlerKind.BUILTIN, ref.kind());
            assertEquals("validate", ref.reference());
        }

        @Test
        @DisplayName("parses script reference")
        void parsesScriptReference() {
            HandlerRef ref = HandlerRef.of("script://scripts/deploy.sh");

            assertEquals(HandlerKind.SCRIPT, ref.kind());
            assertEquals("scripts/deploy.sh", ref.reference());
        }

        @ParameterizedTest
        @ValueSource(strings = {"skill://my-skill", "builtin://test", "script://path/to/script.sh", "skill://complex-skill-name-123"})
        @DisplayName("accepts valid references")
        void acceptsValidReferences(String value) {
            assertDoesNotThrow(() -> HandlerRef.of(value));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "invalid", "skill:", "//missing-kind", "unknown://value", "skill://"})
        @DisplayName("rejects invalid references")
        void rejectsInvalidReferences(String value) {
            assertThrows(IllegalArgumentException.class, () -> HandlerRef.of(value));
        }
    }

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("skill() creates skill reference")
        void skillCreatesSkillReference() {
            SkillId skillId = SkillId.of("my-skill");
            HandlerRef ref = HandlerRef.skill(skillId);

            assertEquals(HandlerKind.SKILL, ref.kind());
            assertEquals("skill://my-skill", ref.toString());
        }

        @Test
        @DisplayName("builtin() creates builtin reference")
        void builtinCreatesBuiltinReference() {
            HandlerRef ref = HandlerRef.builtin("validate");

            assertEquals(HandlerKind.BUILTIN, ref.kind());
            assertEquals("builtin://validate", ref.toString());
        }

        @Test
        @DisplayName("script() creates script reference")
        void scriptCreatesScriptReference() {
            HandlerRef ref = HandlerRef.script("scripts/deploy.sh");

            assertEquals(HandlerKind.SCRIPT, ref.kind());
            assertEquals("script://scripts/deploy.sh", ref.toString());
        }
    }

    @Nested
    @DisplayName("asSkillId()")
    class AsSkillId {

        @Test
        @DisplayName("returns SkillId for skill reference")
        void returnsSkillIdForSkillReference() {
            HandlerRef ref = HandlerRef.of("skill://my-skill");

            SkillId skillId = ref.asSkillId();

            assertNotNull(skillId);
            assertEquals("my-skill", skillId.value());
        }

        @Test
        @DisplayName("returns null for non-skill reference")
        void returnsNullForNonSkillReference() {
            HandlerRef ref = HandlerRef.of("builtin://test");

            assertNull(ref.asSkillId());
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("equal for same kind and reference")
        void equalForSameKindAndReference() {
            HandlerRef ref1 = HandlerRef.of("skill://my-skill");
            HandlerRef ref2 = HandlerRef.of("skill://my-skill");

            assertEquals(ref1, ref2);
            assertEquals(ref1.hashCode(), ref2.hashCode());
        }

        @Test
        @DisplayName("not equal for different kind")
        void notEqualForDifferentKind() {
            HandlerRef ref1 = HandlerRef.of("skill://test");
            HandlerRef ref2 = HandlerRef.of("builtin://test");

            assertNotEquals(ref1, ref2);
        }
    }
}

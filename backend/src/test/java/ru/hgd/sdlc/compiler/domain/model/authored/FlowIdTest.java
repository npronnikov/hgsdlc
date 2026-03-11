package ru.hgd.sdlc.compiler.domain.model.authored;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FlowId")
class FlowIdTest {

    @Nested
    @DisplayName("of()")
    class Of {

        @Test
        @DisplayName("creates FlowId with valid value")
        void createsWithValidValue() {
            FlowId id = FlowId.of("my-flow-123");

            assertEquals("my-flow-123", id.value());
        }

        @ParameterizedTest
        @ValueSource(strings = {"simple", "with-hyphen", "with_underscore", "CamelCase", "123numeric"})
        @DisplayName("accepts valid identifiers")
        void acceptsValidIdentifiers(String value) {
            assertDoesNotThrow(() -> FlowId.of(value));
        }

        @Test
        @DisplayName("rejects null value")
        void rejectsNull() {
            assertThrows(IllegalArgumentException.class, () -> FlowId.of(null));
        }

        @Test
        @DisplayName("rejects blank value")
        void rejectsBlank() {
            assertThrows(IllegalArgumentException.class, () -> FlowId.of(""));
            assertThrows(IllegalArgumentException.class, () -> FlowId.of("   "));
        }

        @ParameterizedTest
        @ValueSource(strings = {"with space", "with.dot", "with/slash", "with@symbol", ""})
        @DisplayName("rejects invalid characters")
        void rejectsInvalidCharacters(String value) {
            assertThrows(IllegalArgumentException.class, () -> FlowId.of(value));
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("equal for same value")
        void equalForSameValue() {
            FlowId id1 = FlowId.of("my-flow");
            FlowId id2 = FlowId.of("my-flow");

            assertEquals(id1, id2);
            assertEquals(id1.hashCode(), id2.hashCode());
        }

        @Test
        @DisplayName("not equal for different value")
        void notEqualForDifferentValue() {
            FlowId id1 = FlowId.of("flow-1");
            FlowId id2 = FlowId.of("flow-2");

            assertNotEquals(id1, id2);
        }
    }

    @Test
    @DisplayName("toString contains value")
    void toStringContainsValue() {
        FlowId id = FlowId.of("my-flow");

        assertTrue(id.toString().contains("my-flow"));
    }
}

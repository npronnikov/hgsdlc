package ru.hgd.sdlc.compiler.domain.model.authored;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Transition")
class TransitionTest {

    private final NodeId nodeA = NodeId.of("node-a");
    private final NodeId nodeB = NodeId.of("node-b");
    private final NodeId nodeC = NodeId.of("node-c");

    @Nested
    @DisplayName("forward()")
    class Forward {

        @Test
        @DisplayName("creates forward transition")
        void createsForwardTransition() {
            Transition t = Transition.forward(nodeA, nodeB);

            assertEquals(nodeA, t.from());
            assertEquals(nodeB, t.to());
            assertEquals(TransitionType.FORWARD, t.type());
            assertTrue(t.condition().isEmpty());
            assertFalse(t.isBackward());
        }

        @Test
        @DisplayName("creates forward transition with condition")
        void createsForwardTransitionWithCondition() {
            Transition t = Transition.forward(nodeA, nodeB, "status == 'success'");

            assertTrue(t.condition().isPresent());
            assertEquals("status == 'success'", t.condition().get());
        }
    }

    @Nested
    @DisplayName("rework()")
    class Rework {

        @Test
        @DisplayName("creates rework transition")
        void createsReworkTransition() {
            Transition t = Transition.rework(nodeC, nodeA);

            assertEquals(nodeC, t.from());
            assertEquals(nodeA, t.to());
            assertEquals(TransitionType.REWORK, t.type());
            assertTrue(t.isBackward());
        }
    }

    @Nested
    @DisplayName("skip()")
    class Skip {

        @Test
        @DisplayName("creates skip transition with condition")
        void createsSkipTransition() {
            Transition t = Transition.skip(nodeA, nodeC, "skip_optional");

            assertEquals(nodeA, t.from());
            assertEquals(nodeC, t.to());
            assertEquals(TransitionType.SKIP, t.type());
            assertEquals("skip_optional", t.condition().get());
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("equal for same from/to/type")
        void equalForSameFromToType() {
            Transition t1 = Transition.forward(nodeA, nodeB);
            Transition t2 = Transition.forward(nodeA, nodeB);

            assertEquals(t1, t2);
            assertEquals(t1.hashCode(), t2.hashCode());
        }

        @Test
        @DisplayName("not equal for different condition")
        void notEqualForDifferentCondition() {
            Transition t1 = Transition.forward(nodeA, nodeB, "cond1");
            Transition t2 = Transition.forward(nodeA, nodeB, "cond2");

            assertNotEquals(t1, t2);
        }

        @Test
        @DisplayName("not equal for different type")
        void notEqualForDifferentType() {
            Transition t1 = Transition.forward(nodeA, nodeB);
            Transition t2 = Transition.rework(nodeA, nodeB);

            assertNotEquals(t1, t2);
        }
    }

    @Test
    @DisplayName("toString contains type and nodes")
    void toStringContainsTypeAndNodes() {
        Transition t = Transition.forward(nodeA, nodeB);

        String str = t.toString();
        assertTrue(str.contains("node-a"));
        assertTrue(str.contains("node-b"));
        assertTrue(str.contains("FORWARD"));
    }
}

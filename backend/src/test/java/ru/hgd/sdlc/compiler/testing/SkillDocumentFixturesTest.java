package ru.hgd.sdlc.compiler.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.HandlerRef;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SkillDocumentFixtures.
 */
class SkillDocumentFixturesTest {

    @Nested
    @DisplayName("simpleSkill()")
    class SimpleSkillTests {

        @Test
        @DisplayName("should create valid minimal skill")
        void shouldCreateValidMinimalSkill() {
            SkillDocument skill = SkillDocumentFixtures.simpleSkill();

            assertNotNull(skill);
            assertEquals("simple-skill", skill.id().value());
            assertEquals("Simple Skill", skill.name());
            assertEquals("1.0.0", skill.version().toString());
            assertNotNull(skill.handler());
        }

        @Test
        @DisplayName("should have builtin handler")
        void shouldHaveBuiltinHandler() {
            SkillDocument skill = SkillDocumentFixtures.simpleSkill();

            assertEquals(HandlerRef.builtin("execute"), skill.handler());
        }
    }

    @Nested
    @DisplayName("skillWithParameters()")
    class SkillWithParametersTests {

        @Test
        @DisplayName("should have input schema")
        void shouldHaveInputSchema() {
            SkillDocument skill = SkillDocumentFixtures.skillWithParameters();

            assertFalse(skill.inputSchema().isEmpty());
            assertTrue(skill.inputSchema().containsKey("properties"));
        }

        @Test
        @DisplayName("should have output schema")
        void shouldHaveOutputSchema() {
            SkillDocument skill = SkillDocumentFixtures.skillWithParameters();

            assertFalse(skill.outputSchema().isEmpty());
            assertTrue(skill.outputSchema().containsKey("properties"));
        }

        @Test
        @DisplayName("should have tags")
        void shouldHaveTags() {
            SkillDocument skill = SkillDocumentFixtures.skillWithParameters();

            assertEquals(List.of("processing", "batch"), skill.tags());
            assertTrue(skill.hasTag("processing"));
            assertTrue(skill.hasTag("batch"));
        }
    }

    @Nested
    @DisplayName("templatedSkill()")
    class TemplatedSkillTests {

        @Test
        @DisplayName("should have skill handler")
        void shouldHaveSkillHandler() {
            SkillDocument skill = SkillDocumentFixtures.templatedSkill();

            assertEquals("skill://ai-processor", skill.handler().toString());
        }

        @Test
        @DisplayName("should have extensions")
        void shouldHaveExtensions() {
            SkillDocument skill = SkillDocumentFixtures.templatedSkill();

            assertFalse(skill.extensions().isEmpty());
            assertEquals("python", skill.extensions().get("runtime"));
        }
    }

    @Nested
    @DisplayName("scriptSkill()")
    class ScriptSkillTests {

        @Test
        @DisplayName("should have script handler")
        void shouldHaveScriptHandler() {
            SkillDocument skill = SkillDocumentFixtures.scriptSkill();

            assertEquals("script://skills/automation.sh", skill.handler().toString());
        }
    }

    @Nested
    @DisplayName("toRef()")
    class ToRefTests {

        @Test
        @DisplayName("should create handler reference from skill")
        void shouldCreateHandlerReferenceFromSkill() {
            SkillDocument skill = SkillDocumentFixtures.simpleSkill();

            HandlerRef ref = skill.toRef();

            assertEquals(HandlerRef.skill(skill.id()), ref);
            assertEquals("skill://simple-skill", ref.toString());
        }
    }

    @Nested
    @DisplayName("builder()")
    class BuilderTests {

        @Test
        @DisplayName("should provide configurable builder")
        void shouldProvideConfigurableBuilder() {
            SkillDocument skill = SkillDocumentFixtures.builder()
                .id(SkillId.of("my-custom-skill"))
                .name("My Custom Skill")
                .build();

            assertEquals("my-custom-skill", skill.id().value());
            assertEquals("My Custom Skill", skill.name());
        }
    }
}

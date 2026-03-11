package ru.hgd.sdlc.compiler.domain.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValidationContext")
class ValidationContextTest {

    private ValidationContext context;

    @BeforeEach
    void setUp() {
        context = ValidationContext.forFile("test.md");
    }

    @Nested
    @DisplayName("file path")
    class FilePathTest {

        @Test
        @DisplayName("returns file path")
        void returnsFilePath() {
            assertTrue(context.filePath().isPresent());
            assertEquals("test.md", context.filePath().get());
        }

        @Test
        @DisplayName("creates empty context without file")
        void createsEmptyContextWithoutFile() {
            ValidationContext empty = ValidationContext.empty();
            assertTrue(empty.filePath().isEmpty());
        }
    }

    @Nested
    @DisplayName("location creation")
    class LocationCreationTest {

        @Test
        @DisplayName("creates location with line")
        void createsLocationWithLine() {
            SourceLocation location = context.location(10);

            assertEquals("test.md", location.filePath().get());
            assertEquals(10, location.line());
        }

        @Test
        @DisplayName("creates location with line and column")
        void createsLocationWithLineAndColumn() {
            SourceLocation location = context.location(10, 5);

            assertEquals("test.md", location.filePath().get());
            assertEquals(10, location.line());
            assertEquals(5, location.column().get());
        }

        @Test
        @DisplayName("creates file location")
        void createsFileLocation() {
            SourceLocation location = context.fileLocation();

            assertEquals("test.md", location.filePath().get());
            assertEquals(1, location.line());
        }
    }

    @Nested
    @DisplayName("error accumulation")
    class ErrorAccumulationTest {

        @Test
        @DisplayName("adds errors")
        void addsErrors() {
            context.addError("E001", "Error 1", context.location(1));
            context.addError("E002", "Error 2", context.location(2));

            assertEquals(2, context.errors().size());
            assertTrue(context.hasErrors());
        }

        @Test
        @DisplayName("adds warnings")
        void addsWarnings() {
            context.addWarning("W001", "Warning 1", context.location(1));

            assertEquals(1, context.warnings().size());
            assertTrue(context.hasWarnings());
        }

        @Test
        @DisplayName("builds valid result when no errors")
        void buildsValidResultWhenNoErrors() {
            context.addWarning("W001", "Warning", context.location(1));

            ValidationResult result = context.toResult();

            assertTrue(result.isValid());
            assertEquals(1, result.warnings().size());
        }

        @Test
        @DisplayName("builds invalid result when errors present")
        void buildsInvalidResultWhenErrorsPresent() {
            context.addError("E001", "Error", context.location(1));
            context.addWarning("W001", "Warning", context.location(1));

            ValidationResult result = context.toResult();

            assertFalse(result.isValid());
            assertEquals(1, result.errors().size());
            assertEquals(1, result.warnings().size());
        }
    }

    @Nested
    @DisplayName("reference resolution")
    class ReferenceResolutionTest {

        @Test
        @DisplayName("resolves registered references")
        void resolvesRegisteredReferences() {
            context.registerResolver("phase", id -> "phase1".equals(id));

            assertTrue(context.canResolve("phase", "phase1"));
            assertFalse(context.canResolve("phase", "phase2"));
        }

        @Test
        @DisplayName("returns false for unregistered type")
        void returnsFalseForUnregisteredType() {
            assertFalse(context.canResolve("unknown", "anything"));
        }
    }

    @Nested
    @DisplayName("child context")
    class ChildContextTest {

        @Test
        @DisplayName("shares error lists with parent")
        void sharesErrorListsWithParent() {
            ValidationContext child = context.child();

            child.addError("E001", "Error in child", context.location(1));

            assertEquals(1, context.errors().size());
        }
    }
}

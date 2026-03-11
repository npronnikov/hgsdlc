package ru.hgd.sdlc.compiler.domain.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SourceLocation")
class SourceLocationTest {

    @Test
    @DisplayName("creates location with file and line")
    void createsLocationWithFileAndLine() {
        SourceLocation location = SourceLocation.of("test.md", 10);

        assertTrue(location.filePath().isPresent());
        assertEquals("test.md", location.filePath().get());
        assertEquals(10, location.line());
        assertTrue(location.column().isEmpty());
    }

    @Test
    @DisplayName("creates location with file, line, and column")
    void createsLocationWithFileLineAndColumn() {
        SourceLocation location = SourceLocation.of("test.md", 10, 5);

        assertEquals("test.md", location.filePath().get());
        assertEquals(10, location.line());
        assertTrue(location.column().isPresent());
        assertEquals(5, location.column().get());
    }

    @Test
    @DisplayName("creates file-only location")
    void createsFileOnlyLocation() {
        SourceLocation location = SourceLocation.file("test.md");

        assertEquals("test.md", location.filePath().get());
        assertEquals(1, location.line());
        assertTrue(location.column().isEmpty());
    }

    @Test
    @DisplayName("creates unknown location")
    void createsUnknownLocation() {
        SourceLocation location = SourceLocation.unknown();

        assertTrue(location.filePath().isEmpty());
        assertEquals(0, location.line());
        assertTrue(location.column().isEmpty());
        assertTrue(location.isUnknown());
    }

    @Test
    @DisplayName("throws for invalid line number")
    void throwsForInvalidLineNumber() {
        assertThrows(IllegalArgumentException.class, () -> SourceLocation.of("test.md", 0));
        assertThrows(IllegalArgumentException.class, () -> SourceLocation.of("test.md", -1));
    }

    @Test
    @DisplayName("throws for invalid column number")
    void throwsForInvalidColumnNumber() {
        assertThrows(IllegalArgumentException.class, () -> SourceLocation.of("test.md", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> SourceLocation.of("test.md", 1, -1));
    }

    @Test
    @DisplayName("creates location at different line")
    void createsLocationAtDifferentLine() {
        SourceLocation location = SourceLocation.of("test.md", 5);
        SourceLocation atLine10 = location.atLine(10);

        assertEquals(10, atLine10.line());
        assertEquals(location.filePath(), atLine10.filePath());
    }

    @Test
    @DisplayName("creates location with column")
    void createsLocationWithColumn() {
        SourceLocation location = SourceLocation.of("test.md", 5);
        SourceLocation withColumn = location.withColumn(10);

        assertEquals(5, withColumn.line());
        assertEquals(10, withColumn.column().get());
    }

    @Test
    @DisplayName("formats toString correctly")
    void formatsToStringCorrectly() {
        assertEquals("test.md:10", SourceLocation.of("test.md", 10).toString());
        assertEquals("test.md:10:5", SourceLocation.of("test.md", 10, 5).toString());
        assertEquals("unknown", SourceLocation.unknown().toString());
        assertEquals(":10", SourceLocation.of(null, 10).toString());
    }

    @Test
    @DisplayName("equals and hashCode work correctly")
    void equalsAndHashCodeWorkCorrectly() {
        SourceLocation loc1 = SourceLocation.of("test.md", 10, 5);
        SourceLocation loc2 = SourceLocation.of("test.md", 10, 5);
        SourceLocation loc3 = SourceLocation.of("test.md", 10);

        assertEquals(loc1, loc2);
        assertEquals(loc1.hashCode(), loc2.hashCode());
        assertNotEquals(loc1, loc3);
    }
}

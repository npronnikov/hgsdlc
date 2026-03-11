package ru.hgd.sdlc.compiler.domain.validation;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a location in source code for error reporting.
 * Includes file path, line number, and optional column number.
 */
public final class SourceLocation {

    private final String filePath;
    private final int line;
    private final Integer column;

    private SourceLocation(String filePath, int line, Integer column) {
        this.filePath = filePath;
        this.line = line;
        this.column = column;
    }

    /**
     * Creates a source location with file, line, and column.
     *
     * @param filePath the file path (may be null for in-memory content)
     * @param line     the line number (1-based)
     * @param column   the column number (1-based, may be null)
     * @return a new SourceLocation instance
     */
    public static SourceLocation of(String filePath, int line, Integer column) {
        if (line < 1) {
            throw new IllegalArgumentException("Line number must be positive");
        }
        if (column != null && column < 1) {
            throw new IllegalArgumentException("Column number must be positive");
        }
        return new SourceLocation(filePath, line, column);
    }

    /**
     * Creates a source location with file and line only.
     *
     * @param filePath the file path (may be null for in-memory content)
     * @param line     the line number (1-based)
     * @return a new SourceLocation instance
     */
    public static SourceLocation of(String filePath, int line) {
        return of(filePath, line, null);
    }

    /**
     * Creates a source location for a file without specific line/column.
     *
     * @param filePath the file path
     * @return a new SourceLocation instance at line 1
     */
    public static SourceLocation file(String filePath) {
        return of(filePath, 1, null);
    }

    /**
     * Creates an unknown source location.
     *
     * @return a SourceLocation indicating unknown position
     */
    public static SourceLocation unknown() {
        return new SourceLocation(null, 0, null);
    }

    /**
     * Returns the file path, if available.
     *
     * @return the file path, or empty if not specified
     */
    public Optional<String> filePath() {
        return Optional.ofNullable(filePath);
    }

    /**
     * Returns the line number.
     *
     * @return the line number (1-based), or 0 if unknown
     */
    public int line() {
        return line;
    }

    /**
     * Returns the column number, if available.
     *
     * @return the column number (1-based), or empty if not specified
     */
    public Optional<Integer> column() {
        return Optional.ofNullable(column);
    }

    /**
     * Checks if this location is unknown.
     *
     * @return true if the location is unknown
     */
    public boolean isUnknown() {
        return line == 0;
    }

    /**
     * Creates a new location at a different line.
     *
     * @param newLine the new line number
     * @return a new SourceLocation instance
     */
    public SourceLocation atLine(int newLine) {
        return of(filePath, newLine, column);
    }

    /**
     * Creates a new location with a column.
     *
     * @param newColumn the column number
     * @return a new SourceLocation instance
     */
    public SourceLocation withColumn(int newColumn) {
        return of(filePath, line, newColumn);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SourceLocation that = (SourceLocation) o;
        return line == that.line
            && Objects.equals(filePath, that.filePath)
            && Objects.equals(column, that.column);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, line, column);
    }

    @Override
    public String toString() {
        if (isUnknown()) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder();
        if (filePath != null) {
            sb.append(filePath);
        }
        sb.append(":").append(line);
        if (column != null) {
            sb.append(":").append(column);
        }
        return sb.toString();
    }
}

package ru.hgd.sdlc.compiler.cli;

/**
 * Helper class for console output with ANSI color support.
 */
final class ConsoleOutput {

    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";

    private final boolean colorEnabled;

    ConsoleOutput() {
        this(true);
    }

    ConsoleOutput(boolean colorEnabled) {
        this.colorEnabled = colorEnabled;
    }

    /**
     * Formats text with red color (for errors).
     */
    String error(String text) {
        return colorEnabled ? RED + text + RESET : text;
    }

    /**
     * Formats text with green color (for success).
     */
    String success(String text) {
        return colorEnabled ? GREEN + text + RESET : text;
    }

    /**
     * Formats text with yellow color (for warnings).
     */
    String warning(String text) {
        return colorEnabled ? YELLOW + text + RESET : text;
    }

    /**
     * Formats text with cyan color (for info).
     */
    String info(String text) {
        return colorEnabled ? CYAN + text + RESET : text;
    }

    /**
     * Formats text as bold.
     */
    String bold(String text) {
        return colorEnabled ? BOLD + text + RESET : text;
    }

    /**
     * Prints an error message to stderr.
     */
    void printError(String message) {
        System.err.println(error("ERROR: ") + message);
    }

    /**
     * Prints a warning message to stdout.
     */
    void printWarning(String message) {
        System.out.println(warning("WARNING: ") + message);
    }

    /**
     * Prints a success message to stdout.
     */
    void printSuccess(String message) {
        System.out.println(success("SUCCESS: ") + message);
    }

    /**
     * Prints an info message to stdout.
     */
    void printInfo(String message) {
        System.out.println(info("INFO: ") + message);
    }

    /**
     * Prints a blank line.
     */
    void println() {
        System.out.println();
    }

    /**
     * Prints text to stdout.
     */
    void println(String text) {
        System.out.println(text);
    }

    /**
     * Prints text to stdout without newline.
     */
    void print(String text) {
        System.out.print(text);
    }
}

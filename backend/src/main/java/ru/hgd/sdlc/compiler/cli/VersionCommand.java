package ru.hgd.sdlc.compiler.cli;

import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.standard.ShellComponent;

/**
 * CLI command to display compiler version information.
 */
@ShellComponent
@Command(group = "General Commands")
public class VersionCommand {

    private static final String COMPILER_VERSION = "1.0.0";
    private static final String IR_SCHEMA_VERSION = "1";
    private static final String JAVA_VERSION = System.getProperty("java.version");
    private static final String SPRING_BOOT_VERSION = "3.3.0";

    private final ConsoleOutput console;

    public VersionCommand() {
        this.console = new ConsoleOutput();
    }

    /**
     * Display compiler version information.
     *
     * @return version information
     */
    @Command(
        command = "version",
        description = "Display compiler version information",
        group = "General Commands"
    )
    public String version() {
        StringBuilder sb = new StringBuilder();
        sb.append(console.bold("Human-Guided SDLC Compiler")).append("\n\n");
        sb.append("Compiler Version:    ").append(console.info(COMPILER_VERSION)).append("\n");
        sb.append("IR Schema Version:   ").append(console.info(IR_SCHEMA_VERSION)).append("\n");
        sb.append("Java Version:        ").append(console.info(JAVA_VERSION)).append("\n");
        sb.append("Spring Boot Version: ").append(console.info(SPRING_BOOT_VERSION)).append("\n");
        return sb.toString().trim();
    }

    /**
     * Returns the compiler version string.
     *
     * @return the compiler version
     */
    public static String getCompilerVersion() {
        return COMPILER_VERSION;
    }

    /**
     * Returns the IR schema version.
     *
     * @return the IR schema version
     */
    public static String getIrSchemaVersion() {
        return IR_SCHEMA_VERSION;
    }
}

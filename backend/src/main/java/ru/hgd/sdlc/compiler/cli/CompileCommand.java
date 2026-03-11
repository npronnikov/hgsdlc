package ru.hgd.sdlc.compiler.cli;

import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.standard.ShellComponent;
import ru.hgd.sdlc.compiler.application.CompilerService;
import ru.hgd.sdlc.compiler.domain.ir.serialization.IRSerializer;
import ru.hgd.sdlc.compiler.domain.ir.serialization.JsonIRSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI commands for compiling flow and skill markdown files.
 * Uses CompilerService for the compilation pipeline.
 */
@ShellComponent
@Command(group = "Compile Commands")
public class CompileCommand {

    private final ConsoleOutput console;
    private final CompilerService compilerService;

    /**
     * Creates a CompileCommand with dependency injection.
     *
     * @param compilerService the compiler service for compilation pipeline
     */
    public CompileCommand(CompilerService compilerService) {
        this.console = new ConsoleOutput();
        this.compilerService = compilerService;
    }

    /**
     * Compile a flow markdown file to IR.
     *
     * @param file          the path to the flow markdown file
     * @param output        optional output file path
     * @param format        output format (json or yaml)
     * @param validateOnly  only validate without producing IR output
     * @return exit status message
     */
    @Command(
        command = "flow compile",
        description = "Compile a flow markdown file to IR",
        group = "Compile Commands"
    )
    public String flowCompile(
        @Option(shortNames = 'f', longNames = "file", description = "Path to the flow markdown file") String file,
        @Option(shortNames = 'o', longNames = "output", description = "Output file path (default: stdout)") String output,
        @Option(shortNames = 'F', longNames = "format", description = "Output format: json or yaml (default: json)") String format,
        @Option(shortNames = 'v', longNames = "validate-only", description = "Only validate without producing IR output", defaultValue = "false") boolean validateOnly
    ) {
        if (file == null || file.isBlank()) {
            return console.error("File path is required");
        }

        Path filePath = Paths.get(file);

        // Read file content
        String content;
        try {
            content = Files.readString(filePath);
        } catch (IOException e) {
            return console.error("Failed to read file: " + file + " - " + e.getMessage());
        }

        // Compile using CompilerService
        CompilerService.CompileFlowResult result = compilerService.compileFlow(content);

        if (result.isFailure()) {
            return formatFailureResult(result.getFailureStage(), result.getAllErrorMessages());
        }

        if (validateOnly) {
            return console.success("Validation passed for flow: " + result.getIr().flowId().value());
        }

        // Serialize IR
        String serialized;
        try {
            IRSerializer serializer = createSerializer(parseFormat(format), true);
            serialized = serializer.serialize(result.getIr());
        } catch (Exception e) {
            return console.error("Failed to serialize IR: " + e.getMessage());
        }

        // Output result
        return writeOutput(output, serialized, result.getIr().flowId().value());
    }

    /**
     * Compile a skill markdown file to IR.
     *
     * @param file          the path to the skill markdown file
     * @param output        optional output file path
     * @param format        output format (json or yaml)
     * @param validateOnly  only validate without producing IR output
     * @return exit status message
     */
    @Command(
        command = "skill compile",
        description = "Compile a skill markdown file to IR",
        group = "Compile Commands"
    )
    public String skillCompile(
        @Option(shortNames = 'f', longNames = "file", description = "Path to the skill markdown file") String file,
        @Option(shortNames = 'o', longNames = "output", description = "Output file path (default: stdout)") String output,
        @Option(shortNames = 'F', longNames = "format", description = "Output format: json or yaml (default: json)") String format,
        @Option(shortNames = 'v', longNames = "validate-only", description = "Only validate without producing IR output", defaultValue = "false") boolean validateOnly
    ) {
        if (file == null || file.isBlank()) {
            return console.error("File path is required");
        }

        Path filePath = Paths.get(file);

        // Read file content
        String content;
        try {
            content = Files.readString(filePath);
        } catch (IOException e) {
            return console.error("Failed to read file: " + file + " - " + e.getMessage());
        }

        // Compile using CompilerService
        CompilerService.CompileSkillResult result = compilerService.compileSkill(content);

        if (result.isFailure()) {
            return formatFailureResult(result.getFailureStage(), result.getAllErrorMessages());
        }

        if (validateOnly) {
            return console.success("Validation passed for skill: " + result.getIr().skillId().value());
        }

        // Serialize IR
        String serialized;
        try {
            IRSerializer serializer = createSerializer(parseFormat(format), true);
            serialized = serializer.serialize(result.getIr());
        } catch (Exception e) {
            return console.error("Failed to serialize IR: " + e.getMessage());
        }

        // Output result
        return writeOutput(output, serialized, result.getIr().skillId().value());
    }

    private String formatFailureResult(String stage, java.util.List<String> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append(console.error(stage + " failed with " + errors.size() + " error(s)")).append("\n");
        for (String error : errors) {
            sb.append(console.error(error)).append("\n");
        }
        return sb.toString().trim();
    }

    private String writeOutput(String output, String serialized, String id) {
        if (output != null && !output.isBlank()) {
            try {
                Files.writeString(Paths.get(output), serialized);
                return console.success("IR written to: " + output);
            } catch (IOException e) {
                return console.error("Failed to write output file: " + output + " - " + e.getMessage());
            }
        } else {
            return serialized;
        }
    }

    private OutputFormat parseFormat(String format) {
        if (format == null || format.isBlank()) {
            return OutputFormat.JSON;
        }
        try {
            return OutputFormat.valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OutputFormat.JSON;
        }
    }

    private IRSerializer createSerializer(OutputFormat format, boolean prettyPrint) {
        return switch (format) {
            case JSON -> new JsonIRSerializer(prettyPrint);
            case YAML -> new JsonIRSerializer(prettyPrint); // YAML not yet implemented, fallback to JSON
        };
    }

    /**
     * Supported output formats for compiled IR.
     */
    enum OutputFormat {
        JSON,
        YAML
    }
}

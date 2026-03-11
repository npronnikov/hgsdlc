package ru.hgd.sdlc.compiler.cli;

import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.standard.ShellComponent;
import ru.hgd.sdlc.compiler.domain.compiler.CompilerError;
import ru.hgd.sdlc.compiler.domain.compiler.CompilerResult;
import ru.hgd.sdlc.compiler.domain.compiler.FlowCompiler;
import ru.hgd.sdlc.compiler.domain.compiler.SkillCompiler;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.parser.FlowParser;
import ru.hgd.sdlc.compiler.domain.parser.FrontmatterExtractor;
import ru.hgd.sdlc.compiler.domain.parser.ParseError;
import ru.hgd.sdlc.compiler.domain.parser.ParseResult;
import ru.hgd.sdlc.compiler.domain.parser.ParsedMarkdown;
import ru.hgd.sdlc.compiler.domain.parser.SkillParser;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.shared.kernel.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI commands for validating flow and skill markdown files without compiling.
 */
@ShellComponent
@Command(group = "Validation Commands")
public class ValidateCommand {

    private final ConsoleOutput console;
    private final FrontmatterExtractor frontmatterExtractor;
    private final FlowParser flowParser;
    private final SkillParser skillParser;
    private final FlowCompiler flowCompiler;
    private final SkillCompiler skillCompiler;

    public ValidateCommand() {
        this.console = new ConsoleOutput();
        this.frontmatterExtractor = new FrontmatterExtractor();
        this.flowParser = new FlowParser();
        this.skillParser = new SkillParser();
        this.flowCompiler = new FlowCompiler();
        this.skillCompiler = new SkillCompiler();
    }

    /**
     * Validate a flow markdown file.
     *
     * @param file the path to the flow markdown file
     * @return validation result message
     */
    @Command(
        command = "flow validate",
        description = "Validate a flow markdown file without compiling",
        group = "Validation Commands"
    )
    public String flowValidate(
        @Option(shortNames = 'f', longNames = "file", description = "Path to the flow markdown file") String file
    ) {
        if (file == null || file.isBlank()) {
            return console.error("File path is required");
        }

        Path filePath = Paths.get(file);

        // Read file content
        byte[] content;
        try {
            content = Files.readAllBytes(filePath);
        } catch (IOException e) {
            return console.error("Failed to read file: " + file + " - " + e.getMessage());
        }

        // Extract frontmatter
        Result<ParsedMarkdown, ParseError> extractResult = frontmatterExtractor.extract(content);
        if (extractResult.isFailure()) {
            ParseError error = extractResult.getError();
            return console.error(formatError(error));
        }

        ParsedMarkdown parsed = extractResult.getValue();

        // Parse to FlowDocument
        ParseResult<FlowDocument> parseResult = flowParser.parse(parsed);
        if (parseResult.isFailure()) {
            StringBuilder sb = new StringBuilder();
            sb.append(console.error("Validation failed with " + parseResult.getFatalErrors().size() + " error(s)")).append("\n\n");
            for (ParseError error : parseResult.getFatalErrors()) {
                sb.append(console.error(formatError(error))).append("\n");
            }
            return sb.toString().trim();
        }

        // Print warnings if any
        if (parseResult.hasWarnings()) {
            console.println(console.bold("Warnings:"));
            for (ParseError warning : parseResult.getWarnings()) {
                console.printWarning(formatError(warning));
            }
            console.println();
        }

        FlowDocument document = parseResult.getDocument();

        // Compile to IR (to validate semantic correctness)
        CompilerResult<FlowIr> compileResult = flowCompiler.compile(document);
        if (compileResult.isFailure()) {
            StringBuilder sb = new StringBuilder();
            sb.append(console.error("Validation failed with " + compileResult.getFatalErrors().size() + " error(s)")).append("\n\n");
            for (CompilerError error : compileResult.getFatalErrors()) {
                sb.append(console.error(formatCompilerError(error))).append("\n");
            }
            return sb.toString().trim();
        }

        // Print compilation warnings if any
        if (compileResult.hasWarnings()) {
            console.println(console.bold("Warnings:"));
            for (CompilerError warning : compileResult.getWarnings()) {
                console.printWarning(formatCompilerError(warning));
            }
            console.println();
        }

        StringBuilder success = new StringBuilder();
        success.append(console.success("Validation passed")).append("\n\n");
        success.append("Flow ID: ").append(document.id().value()).append("\n");
        success.append("Version: ").append(document.version().toString()).append("\n");
        if (document.name() != null) {
            success.append("Name: ").append(document.name()).append("\n");
        }
        success.append("Phases: ").append(document.phaseOrder().size()).append("\n");
        success.append("Nodes: ").append(document.nodes().size());

        return success.toString();
    }

    /**
     * Validate a skill markdown file.
     *
     * @param file the path to the skill markdown file
     * @return validation result message
     */
    @Command(
        command = "skill validate",
        description = "Validate a skill markdown file without compiling",
        group = "Validation Commands"
    )
    public String skillValidate(
        @Option(shortNames = 'f', longNames = "file", description = "Path to the skill markdown file") String file
    ) {
        if (file == null || file.isBlank()) {
            return console.error("File path is required");
        }

        Path filePath = Paths.get(file);

        // Read file content
        byte[] content;
        try {
            content = Files.readAllBytes(filePath);
        } catch (IOException e) {
            return console.error("Failed to read file: " + file + " - " + e.getMessage());
        }

        // Extract frontmatter
        Result<ParsedMarkdown, ParseError> extractResult = frontmatterExtractor.extract(content);
        if (extractResult.isFailure()) {
            ParseError error = extractResult.getError();
            return console.error(formatError(error));
        }

        ParsedMarkdown parsed = extractResult.getValue();

        // Parse to SkillDocument
        ParseResult<SkillDocument> parseResult = skillParser.parse(parsed);
        if (parseResult.isFailure()) {
            StringBuilder sb = new StringBuilder();
            sb.append(console.error("Validation failed with " + parseResult.getFatalErrors().size() + " error(s)")).append("\n\n");
            for (ParseError error : parseResult.getFatalErrors()) {
                sb.append(console.error(formatError(error))).append("\n");
            }
            return sb.toString().trim();
        }

        // Print warnings if any
        if (parseResult.hasWarnings()) {
            console.println(console.bold("Warnings:"));
            for (ParseError warning : parseResult.getWarnings()) {
                console.printWarning(formatError(warning));
            }
            console.println();
        }

        SkillDocument document = parseResult.getDocument();

        // Compile to IR (to validate semantic correctness)
        CompilerResult<SkillIr> compileResult = skillCompiler.compile(document);
        if (compileResult.isFailure()) {
            StringBuilder sb = new StringBuilder();
            sb.append(console.error("Validation failed with " + compileResult.getFatalErrors().size() + " error(s)")).append("\n\n");
            for (CompilerError error : compileResult.getFatalErrors()) {
                sb.append(console.error(formatCompilerError(error))).append("\n");
            }
            return sb.toString().trim();
        }

        // Print compilation warnings if any
        if (compileResult.hasWarnings()) {
            console.println(console.bold("Warnings:"));
            for (CompilerError warning : compileResult.getWarnings()) {
                console.printWarning(formatCompilerError(warning));
            }
            console.println();
        }

        StringBuilder success = new StringBuilder();
        success.append(console.success("Validation passed")).append("\n\n");
        success.append("Skill ID: ").append(document.id().value()).append("\n");
        success.append("Version: ").append(document.version().toString()).append("\n");
        success.append("Name: ").append(document.name()).append("\n");
        success.append("Handler: ").append(document.handler().kind().name())
            .append("://").append(document.handler().reference()).append("\n");
        success.append("Tags: ").append(document.tags().size());

        return success.toString();
    }

    private String formatError(ParseError error) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(error.code()).append("] ");
        sb.append(error.message());
        error.location().ifPresent(loc -> sb.append(" at ").append(loc));
        return sb.toString();
    }

    private String formatCompilerError(CompilerError error) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(error.code()).append("] ");
        sb.append(error.message());
        error.location().ifPresent(loc -> sb.append(" at ").append(loc));
        return sb.toString();
    }
}

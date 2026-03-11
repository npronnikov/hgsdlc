package ru.hgd.sdlc.compiler.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.shell.standard.ShellComponent;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;
import ru.hgd.sdlc.compiler.domain.parser.FlowParser;
import ru.hgd.sdlc.compiler.domain.parser.FrontmatterExtractor;
import ru.hgd.sdlc.compiler.domain.parser.ParseError;
import ru.hgd.sdlc.compiler.domain.parser.ParseResult;
import ru.hgd.sdlc.compiler.domain.parser.ParsedMarkdown;
import ru.hgd.sdlc.compiler.domain.parser.SkillParser;
import ru.hgd.sdlc.shared.kernel.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * CLI commands for parsing flow and skill markdown files for debugging.
 * Shows the parsed structure without compiling.
 */
@ShellComponent
@Command(group = "Parse Commands")
public class ParseCommand {

    private final ConsoleOutput console;
    private final FrontmatterExtractor frontmatterExtractor;
    private final FlowParser flowParser;
    private final SkillParser skillParser;
    private final ObjectMapper objectMapper;

    public ParseCommand() {
        this.console = new ConsoleOutput();
        this.frontmatterExtractor = new FrontmatterExtractor();
        this.flowParser = new FlowParser();
        this.skillParser = new SkillParser();
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Parse a flow markdown file and show its structure.
     *
     * @param file the path to the flow markdown file
     * @return parsed structure or error message
     */
    @Command(
        command = "flow parse",
        description = "Parse a flow markdown file and show its structure",
        group = "Parse Commands"
    )
    public String flowParse(
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

        // Show raw frontmatter
        StringBuilder output = new StringBuilder();
        output.append(console.bold("=== Raw Frontmatter ===")).append("\n");
        output.append(formatYamlMap(parsed.frontmatter())).append("\n\n");

        // Show body preview
        output.append(console.bold("=== Body Preview ===")).append("\n");
        String bodyText = parsed.body().content();
        if (bodyText.length() > 200) {
            output.append(bodyText.substring(0, 200)).append("...\n\n");
        } else {
            output.append(bodyText.isEmpty() ? "(empty)" : bodyText).append("\n\n");
        }

        // Parse to FlowDocument
        ParseResult<FlowDocument> parseResult = flowParser.parse(parsed);
        if (parseResult.isFailure()) {
            output.append(console.error("Parsing failed with " + parseResult.getFatalErrors().size() + " error(s)")).append("\n\n");
            for (ParseError error : parseResult.getFatalErrors()) {
                output.append(console.error(formatError(error))).append("\n");
            }
            return output.toString().trim();
        }

        // Print warnings if any
        if (parseResult.hasWarnings()) {
            output.append(console.bold("Warnings:")).append("\n");
            for (ParseError warning : parseResult.getWarnings()) {
                output.append(console.warning(formatError(warning))).append("\n");
            }
            output.append("\n");
        }

        // Show parsed document structure
        output.append(console.bold("=== Parsed Flow Document ===")).append("\n");
        FlowDocument document = parseResult.getDocument();
        output.append(formatDocument(document));

        return output.toString().trim();
    }

    /**
     * Parse a skill markdown file and show its structure.
     *
     * @param file the path to the skill markdown file
     * @return parsed structure or error message
     */
    @Command(
        command = "skill parse",
        description = "Parse a skill markdown file and show its structure",
        group = "Parse Commands"
    )
    public String skillParse(
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

        // Show raw frontmatter
        StringBuilder output = new StringBuilder();
        output.append(console.bold("=== Raw Frontmatter ===")).append("\n");
        output.append(formatYamlMap(parsed.frontmatter())).append("\n\n");

        // Show body preview
        output.append(console.bold("=== Body Preview ===")).append("\n");
        String bodyText = parsed.body().content();
        if (bodyText.length() > 200) {
            output.append(bodyText.substring(0, 200)).append("...\n\n");
        } else {
            output.append(bodyText.isEmpty() ? "(empty)" : bodyText).append("\n\n");
        }

        // Parse to SkillDocument
        ParseResult<SkillDocument> parseResult = skillParser.parse(parsed);
        if (parseResult.isFailure()) {
            output.append(console.error("Parsing failed with " + parseResult.getFatalErrors().size() + " error(s)")).append("\n\n");
            for (ParseError error : parseResult.getFatalErrors()) {
                output.append(console.error(formatError(error))).append("\n");
            }
            return output.toString().trim();
        }

        // Print warnings if any
        if (parseResult.hasWarnings()) {
            output.append(console.bold("Warnings:")).append("\n");
            for (ParseError warning : parseResult.getWarnings()) {
                output.append(console.warning(formatError(warning))).append("\n");
            }
            output.append("\n");
        }

        // Show parsed document structure
        output.append(console.bold("=== Parsed Skill Document ===")).append("\n");
        SkillDocument document = parseResult.getDocument();
        output.append(formatDocument(document));

        return output.toString().trim();
    }

    private String formatYamlMap(Map<String, Object> map) {
        if (map.isEmpty()) {
            return "(empty)";
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return map.toString();
        }
    }

    private String formatDocument(FlowDocument document) {
        StringBuilder sb = new StringBuilder();
        sb.append(console.info("ID: ")).append(document.id().value()).append("\n");
        sb.append(console.info("Version: ")).append(document.version().toString()).append("\n");

        if (document.name() != null) {
            sb.append(console.info("Name: ")).append(document.name()).append("\n");
        }

        sb.append(console.info("Phase Order: ")).append("[");
        sb.append(String.join(", ", document.phaseOrder().stream().map(p -> p.value()).toList()));
        sb.append("]\n");

        sb.append(console.info("Start Roles: ")).append("[");
        sb.append(String.join(", ", document.startRoles().stream().map(r -> r.value()).toList()));
        sb.append("]\n");

        sb.append(console.info("Resume Policy: ")).append(document.resumePolicy().name()).append("\n");

        sb.append(console.info("Phases: ")).append(document.phases().size()).append("\n");
        document.phases().forEach((phaseId, phase) -> {
            sb.append("  - ").append(phaseId.value());
            if (phase.name() != null) {
                sb.append(" (").append(phase.name()).append(")");
            }
            sb.append("\n");
        });

        sb.append(console.info("Nodes: ")).append(document.nodes().size()).append("\n");
        document.nodes().forEach((nodeId, node) -> {
            sb.append("  - ").append(nodeId.value());
            sb.append(" [").append(node.type().name()).append("]");
            node.phaseId().ifPresent(p -> sb.append(" @ ").append(p.value()));
            sb.append("\n");
        });

        sb.append(console.info("Artifacts: ")).append(document.artifacts().size()).append("\n");

        if (document.description() != null && !document.description().content().isBlank()) {
            String desc = document.description().content();
            sb.append(console.info("Description: ")).append(desc.length() > 100 ? desc.substring(0, 100) + "..." : desc);
        }

        return sb.toString();
    }

    private String formatDocument(SkillDocument document) {
        StringBuilder sb = new StringBuilder();
        sb.append(console.info("ID: ")).append(document.id().value()).append("\n");
        sb.append(console.info("Version: ")).append(document.version().toString()).append("\n");
        sb.append(console.info("Name: ")).append(document.name()).append("\n");

        sb.append(console.info("Handler: ")).append(document.handler().kind().name())
            .append("://").append(document.handler().reference()).append("\n");

        sb.append(console.info("Tags: ")).append("[");
        sb.append(String.join(", ", document.tags()));
        sb.append("]\n");

        if (!document.inputSchema().isEmpty()) {
            sb.append(console.info("Input Schema: ")).append(formatYamlMap(document.inputSchema())).append("\n");
        }

        if (!document.outputSchema().isEmpty()) {
            sb.append(console.info("Output Schema: ")).append(formatYamlMap(document.outputSchema())).append("\n");
        }

        if (document.description() != null && !document.description().content().isBlank()) {
            String desc = document.description().content();
            sb.append(console.info("Description: ")).append(desc.length() > 100 ? desc.substring(0, 100) + "..." : desc);
        }

        return sb.toString();
    }

    private String formatError(ParseError error) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(error.code()).append("] ");
        sb.append(error.message());
        error.location().ifPresent(loc -> sb.append(" at ").append(loc));
        return sb.toString();
    }
}

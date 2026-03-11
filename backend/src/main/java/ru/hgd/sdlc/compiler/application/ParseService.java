package ru.hgd.sdlc.compiler.application;

import org.springframework.stereotype.Service;
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
import java.util.List;
import java.util.Objects;

/**
 * Application service for parsing Markdown documents.
 * Orchestrates frontmatter extraction and document parsing.
 *
 * <p>This service handles the first stage of the compilation pipeline:
 * raw Markdown -> ParsedMarkdown -> FlowDocument/SkillDocument
 */
@Service
public class ParseService {

    private final FrontmatterExtractor frontmatterExtractor;
    private final FlowParser flowParser;
    private final SkillParser skillParser;

    public ParseService(
            FrontmatterExtractor frontmatterExtractor,
            FlowParser flowParser,
            SkillParser skillParser) {
        this.frontmatterExtractor = Objects.requireNonNull(frontmatterExtractor, "frontmatterExtractor cannot be null");
        this.flowParser = Objects.requireNonNull(flowParser, "flowParser cannot be null");
        this.skillParser = Objects.requireNonNull(skillParser, "skillParser cannot be null");
    }

    /**
     * Parses flow Markdown content into a FlowDocument.
     *
     * @param content the Markdown content as a string
     * @return a ParseResult containing the FlowDocument or errors
     */
    public ParseResult<FlowDocument> parseFlowMarkdown(String content) {
        Objects.requireNonNull(content, "content cannot be null");

        // Extract frontmatter
        Result<ParsedMarkdown, ParseError> extractResult = frontmatterExtractor.extract(content);
        if (extractResult.isFailure()) {
            return ParseResult.failure(extractResult.getError());
        }

        // Parse flow document
        return flowParser.parse(extractResult.getValue());
    }

    /**
     * Parses flow Markdown content from a file into a FlowDocument.
     *
     * @param markdownFile the path to the Markdown file
     * @return a ParseResult containing the FlowDocument or errors
     * @throws IOException if the file cannot be read
     */
    public ParseResult<FlowDocument> parseFlowMarkdownFile(Path markdownFile) throws IOException {
        Objects.requireNonNull(markdownFile, "markdownFile cannot be null");

        byte[] content = Files.readAllBytes(markdownFile);
        Result<ParsedMarkdown, ParseError> extractResult = frontmatterExtractor.extract(content);
        if (extractResult.isFailure()) {
            return ParseResult.failure(extractResult.getError());
        }

        return flowParser.parse(extractResult.getValue());
    }

    /**
     * Parses skill Markdown content into a SkillDocument.
     *
     * @param content the Markdown content as a string
     * @return a ParseResult containing the SkillDocument or errors
     */
    public ParseResult<SkillDocument> parseSkillMarkdown(String content) {
        Objects.requireNonNull(content, "content cannot be null");

        // Extract frontmatter
        Result<ParsedMarkdown, ParseError> extractResult = frontmatterExtractor.extract(content);
        if (extractResult.isFailure()) {
            return ParseResult.failure(extractResult.getError());
        }

        // Parse skill document
        return skillParser.parse(extractResult.getValue());
    }

    /**
     * Parses skill Markdown content from a file into a SkillDocument.
     *
     * @param markdownFile the path to the Markdown file
     * @return a ParseResult containing the SkillDocument or errors
     * @throws IOException if the file cannot be read
     */
    public ParseResult<SkillDocument> parseSkillMarkdownFile(Path markdownFile) throws IOException {
        Objects.requireNonNull(markdownFile, "markdownFile cannot be null");

        byte[] content = Files.readAllBytes(markdownFile);
        Result<ParsedMarkdown, ParseError> extractResult = frontmatterExtractor.extract(content);
        if (extractResult.isFailure()) {
            return ParseResult.failure(extractResult.getError());
        }

        return skillParser.parse(extractResult.getValue());
    }

    /**
     * Extracts frontmatter from Markdown content without parsing.
     * Useful for determining document type before full parsing.
     *
     * @param content the Markdown content
     * @return a Result containing ParsedMarkdown or ParseError
     */
    public Result<ParsedMarkdown, ParseError> extractFrontmatter(String content) {
        Objects.requireNonNull(content, "content cannot be null");
        return frontmatterExtractor.extract(content);
    }

    /**
     * Extracts frontmatter from a Markdown file without parsing.
     *
     * @param markdownFile the path to the Markdown file
     * @return a Result containing ParsedMarkdown or ParseError
     * @throws IOException if the file cannot be read
     */
    public Result<ParsedMarkdown, ParseError> extractFrontmatterFromFile(Path markdownFile) throws IOException {
        Objects.requireNonNull(markdownFile, "markdownFile cannot be null");
        byte[] content = Files.readAllBytes(markdownFile);
        return frontmatterExtractor.extract(content);
    }

    /**
     * Determines if the parsed content is a flow document based on frontmatter.
     *
     * @param parsed the parsed markdown
     * @return true if the content appears to be a flow document
     */
    public boolean isFlowDocument(ParsedMarkdown parsed) {
        Object type = parsed.get("type");
        if (type != null) {
            return "flow".equalsIgnoreCase(type.toString());
        }
        // If no type specified, check for flow-specific fields
        return parsed.get("phase_order") != null || parsed.get("phases") != null;
    }

    /**
     * Determines if the parsed content is a skill document based on frontmatter.
     *
     * @param parsed the parsed markdown
     * @return true if the content appears to be a skill document
     */
    public boolean isSkillDocument(ParsedMarkdown parsed) {
        Object type = parsed.get("type");
        if (type != null) {
            return "skill".equalsIgnoreCase(type.toString());
        }
        // If no type specified, check for skill-specific fields
        return parsed.get("handler") != null;
    }

    /**
     * Converts a list of ParseError to human-readable messages.
     *
     * @param errors the parse errors
     * @return a list of formatted error messages
     */
    public List<String> formatErrors(List<ParseError> errors) {
        return errors.stream()
            .map(e -> {
                String loc = e.location().map(l -> " at " + l).orElse("");
                return "[" + e.code() + "] " + e.message() + loc;
            })
            .toList();
    }
}

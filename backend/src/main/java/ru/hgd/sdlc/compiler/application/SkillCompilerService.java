package ru.hgd.sdlc.compiler.application;

import org.springframework.stereotype.Service;
import ru.hgd.sdlc.compiler.domain.compiler.CompilerError;
import ru.hgd.sdlc.compiler.domain.compiler.CompilerResult;
import ru.hgd.sdlc.compiler.domain.compiler.SkillCompiler;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.ir.serialization.SerializationException;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;
import ru.hgd.sdlc.compiler.domain.validation.ValidationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for skill-specific compilation operations.
 * Provides a high-level API for parsing, validating, compiling, and serializing skills.
 *
 * <p>This service orchestrates the skill compilation pipeline:
 * Markdown -> SkillDocument -> ValidationResult -> SkillIr -> JSON
 */
@Service
public class SkillCompilerService {

    private final ParseService parseService;
    private final ValidationService validationService;
    private final SkillCompiler skillCompiler;
    private final SerializationService serializationService;

    public SkillCompilerService(
            ParseService parseService,
            ValidationService validationService,
            SkillCompiler skillCompiler,
            SerializationService serializationService) {
        this.parseService = Objects.requireNonNull(parseService, "parseService cannot be null");
        this.validationService = Objects.requireNonNull(validationService, "validationService cannot be null");
        this.skillCompiler = Objects.requireNonNull(skillCompiler, "skillCompiler cannot be null");
        this.serializationService = Objects.requireNonNull(serializationService, "serializationService cannot be null");
    }

    /**
     * Parses skill Markdown content into a SkillDocument.
     *
     * @param content the Markdown content
     * @return the parsed SkillDocument, or empty if parsing failed
     */
    public Optional<SkillDocument> parseSkill(String content) {
        var result = parseService.parseSkillMarkdown(content);
        return result.isSuccess() ? Optional.of(result.getDocument()) : Optional.empty();
    }

    /**
     * Parses skill Markdown from a file into a SkillDocument.
     *
     * @param markdownFile the path to the Markdown file
     * @return the parsed SkillDocument, or empty if parsing failed
     * @throws IOException if the file cannot be read
     */
    public Optional<SkillDocument> parseSkill(Path markdownFile) throws IOException {
        var result = parseService.parseSkillMarkdownFile(markdownFile);
        return result.isSuccess() ? Optional.of(result.getDocument()) : Optional.empty();
    }

    /**
     * Validates a SkillDocument.
     *
     * @param skill the skill document to validate
     * @return the validation result
     */
    public ValidationResult validateSkill(SkillDocument skill) {
        return validationService.validateSkill(skill);
    }

    /**
     * Validates a SkillDocument with file path context.
     *
     * @param skill    the skill document to validate
     * @param filePath the file path for error reporting
     * @return the validation result
     */
    public ValidationResult validateSkill(SkillDocument skill, String filePath) {
        return validationService.validateSkill(skill, filePath);
    }

    /**
     * Compiles a SkillDocument to SkillIr.
     *
     * @param skill the skill document to compile
     * @return the compilation result with IR or errors
     */
    public CompilerResult<SkillIr> compileSkill(SkillDocument skill) {
        return skillCompiler.compile(skill);
    }

    /**
     * Serializes a SkillIr to a JSON string.
     *
     * @param ir the compiled skill IR
     * @return the JSON string representation
     * @throws SerializationException if serialization fails
     */
    public String serializeIr(SkillIr ir) throws SerializationException {
        return serializationService.serialize(ir);
    }

    /**
     * Deserializes a JSON string to a SkillIr.
     *
     * @param data the JSON string data
     * @return the deserialized SkillIr
     * @throws SerializationException if deserialization fails
     */
    public SkillIr deserializeIr(String data) throws SerializationException {
        return serializationService.deserializeSkill(data);
    }

    /**
     * Full compilation pipeline: parse -> validate -> compile.
     * Returns a result containing the IR or all accumulated errors.
     *
     * @param content the Markdown content
     * @return a SkillCompilationResult with IR or errors
     */
    public SkillCompilationResult compile(String content) {
        Objects.requireNonNull(content, "content cannot be null");

        // Step 1: Parse
        var parseResult = parseService.parseSkillMarkdown(content);
        if (parseResult.isFailure()) {
            return SkillCompilationResult.parseFailure(parseResult.getErrors());
        }

        SkillDocument document = parseResult.getDocument();

        // Step 2: Validate
        var validationResult = validationService.validateSkill(document);
        if (!validationResult.isValid()) {
            return SkillCompilationResult.validationFailure(validationResult);
        }

        // Step 3: Compile
        var compileResult = skillCompiler.compile(document);
        if (compileResult.isFailure()) {
            return SkillCompilationResult.compileFailure(compileResult.getErrors());
        }

        return SkillCompilationResult.success(compileResult.getIr(), validationResult.warnings());
    }

    /**
     * Full compilation pipeline from file.
     *
     * @param markdownFile the path to the Markdown file
     * @return a SkillCompilationResult with IR or errors
     * @throws IOException if the file cannot be read
     */
    public SkillCompilationResult compile(Path markdownFile) throws IOException {
        Objects.requireNonNull(markdownFile, "markdownFile cannot be null");

        String content = Files.readString(markdownFile);
        SkillCompilationResult result = compile(content);

        // Add file path to error locations if not already present
        if (result.isFailure()) {
            return result.withFilePath(markdownFile.toString());
        }

        return result;
    }

    /**
     * Full pipeline with serialization: parse -> validate -> compile -> serialize.
     *
     * @param content the Markdown content
     * @return a SerializedSkillResult with JSON string or errors
     */
    public SerializedSkillResult compileAndSerialize(String content) {
        SkillCompilationResult result = compile(content);

        if (result.isFailure()) {
            return SerializedSkillResult.failure(result);
        }

        try {
            String json = serializationService.serialize(result.getIr());
            return SerializedSkillResult.success(json, result);
        } catch (SerializationException e) {
            return SerializedSkillResult.serializationFailure(
                CompilerError.serializationFailure(e.getMessage(), e)
            );
        }
    }

    /**
     * Gets parse errors from Markdown content.
     *
     * @param content the Markdown content
     * @return a list of parse errors, empty if parsing succeeded
     */
    public List<ru.hgd.sdlc.compiler.domain.parser.ParseError> getParseErrors(String content) {
        var result = parseService.parseSkillMarkdown(content);
        return result.isFailure() ? result.getErrors() : List.of();
    }

    /**
     * Checks if Markdown content can be successfully parsed as a skill.
     *
     * @param content the Markdown content
     * @return true if the content can be parsed as a skill
     */
    public boolean canParse(String content) {
        return parseService.parseSkillMarkdown(content).isSuccess();
    }

    /**
     * Result of skill compilation containing either the compiled IR or errors.
     */
    public static final class SkillCompilationResult {
        private final SkillIr ir;
        private final List<ru.hgd.sdlc.compiler.domain.parser.ParseError> parseErrors;
        private final ValidationResult validationResult;
        private final List<CompilerError> compileErrors;
        private final String filePath;

        private SkillCompilationResult(
                SkillIr ir,
                List<ru.hgd.sdlc.compiler.domain.parser.ParseError> parseErrors,
                ValidationResult validationResult,
                List<CompilerError> compileErrors,
                String filePath) {
            this.ir = ir;
            this.parseErrors = parseErrors;
            this.validationResult = validationResult;
            this.compileErrors = compileErrors;
            this.filePath = filePath;
        }

        public static SkillCompilationResult success(SkillIr ir, List<ru.hgd.sdlc.compiler.domain.validation.ValidationError> warnings) {
            return new SkillCompilationResult(ir, List.of(), ValidationResult.valid(warnings), List.of(), null);
        }

        public static SkillCompilationResult parseFailure(List<ru.hgd.sdlc.compiler.domain.parser.ParseError> errors) {
            return new SkillCompilationResult(null, errors, ValidationResult.valid(), List.of(), null);
        }

        public static SkillCompilationResult validationFailure(ValidationResult result) {
            return new SkillCompilationResult(null, List.of(), result, List.of(), null);
        }

        public static SkillCompilationResult compileFailure(List<CompilerError> errors) {
            return new SkillCompilationResult(null, List.of(), ValidationResult.valid(), errors, null);
        }

        public boolean isSuccess() {
            return ir != null;
        }

        public boolean isFailure() {
            return ir == null;
        }

        public boolean hasParseErrors() {
            return parseErrors != null && !parseErrors.isEmpty();
        }

        public boolean hasValidationErrors() {
            return validationResult != null && !validationResult.isValid();
        }

        public boolean hasCompileErrors() {
            return compileErrors != null && !compileErrors.isEmpty();
        }

        public SkillIr getIr() {
            return ir;
        }

        public List<ru.hgd.sdlc.compiler.domain.parser.ParseError> getParseErrors() {
            return parseErrors != null ? parseErrors : List.of();
        }

        public ValidationResult getValidationResult() {
            return validationResult;
        }

        public List<CompilerError> getCompileErrors() {
            return compileErrors != null ? compileErrors : List.of();
        }

        public String getFilePath() {
            return filePath;
        }

        public SkillCompilationResult withFilePath(String filePath) {
            return new SkillCompilationResult(ir, parseErrors, validationResult, compileErrors, filePath);
        }

        public String getFailureStage() {
            if (hasParseErrors()) return "PARSE";
            if (hasValidationErrors()) return "VALIDATION";
            if (hasCompileErrors()) return "COMPILE";
            return isSuccess() ? null : "UNKNOWN";
        }
    }

    /**
     * Result of skill compilation with serialization.
     */
    public static final class SerializedSkillResult {
        private final String json;
        private final SkillCompilationResult compilationResult;
        private final CompilerError serializationError;

        private SerializedSkillResult(
                String json,
                SkillCompilationResult compilationResult,
                CompilerError serializationError) {
            this.json = json;
            this.compilationResult = compilationResult;
            this.serializationError = serializationError;
        }

        public static SerializedSkillResult success(String json, SkillCompilationResult result) {
            return new SerializedSkillResult(json, result, null);
        }

        public static SerializedSkillResult failure(SkillCompilationResult result) {
            return new SerializedSkillResult(null, result, null);
        }

        public static SerializedSkillResult serializationFailure(CompilerError error) {
            return new SerializedSkillResult(null, null, error);
        }

        public boolean isSuccess() {
            return json != null;
        }

        public boolean isFailure() {
            return json == null;
        }

        public String getJson() {
            return json;
        }

        public SkillCompilationResult getCompilationResult() {
            return compilationResult;
        }

        public CompilerError getSerializationError() {
            return serializationError;
        }
    }
}

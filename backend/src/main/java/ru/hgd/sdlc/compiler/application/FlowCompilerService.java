package ru.hgd.sdlc.compiler.application;

import org.springframework.stereotype.Service;
import ru.hgd.sdlc.compiler.domain.compiler.CompilerError;
import ru.hgd.sdlc.compiler.domain.compiler.CompilerResult;
import ru.hgd.sdlc.compiler.domain.compiler.FlowCompiler;
import ru.hgd.sdlc.compiler.domain.ir.serialization.SerializationException;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.validation.ValidationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for flow-specific compilation operations.
 * Provides a high-level API for parsing, validating, compiling, and serializing flows.
 *
 * <p>This service orchestrates the flow compilation pipeline:
 * Markdown -> FlowDocument -> ValidationResult -> FlowIr -> JSON
 */
@Service
public class FlowCompilerService {

    private final ParseService parseService;
    private final ValidationService validationService;
    private final FlowCompiler flowCompiler;
    private final SerializationService serializationService;

    public FlowCompilerService(
            ParseService parseService,
            ValidationService validationService,
            FlowCompiler flowCompiler,
            SerializationService serializationService) {
        this.parseService = Objects.requireNonNull(parseService, "parseService cannot be null");
        this.validationService = Objects.requireNonNull(validationService, "validationService cannot be null");
        this.flowCompiler = Objects.requireNonNull(flowCompiler, "flowCompiler cannot be null");
        this.serializationService = Objects.requireNonNull(serializationService, "serializationService cannot be null");
    }

    /**
     * Parses flow Markdown content into a FlowDocument.
     *
     * @param content the Markdown content
     * @return the parsed FlowDocument, or empty if parsing failed
     */
    public Optional<FlowDocument> parseFlow(String content) {
        var result = parseService.parseFlowMarkdown(content);
        return result.isSuccess() ? Optional.of(result.getDocument()) : Optional.empty();
    }

    /**
     * Parses flow Markdown from a file into a FlowDocument.
     *
     * @param markdownFile the path to the Markdown file
     * @return the parsed FlowDocument, or empty if parsing failed
     * @throws IOException if the file cannot be read
     */
    public Optional<FlowDocument> parseFlow(Path markdownFile) throws IOException {
        var result = parseService.parseFlowMarkdownFile(markdownFile);
        return result.isSuccess() ? Optional.of(result.getDocument()) : Optional.empty();
    }

    /**
     * Validates a FlowDocument.
     *
     * @param flow the flow document to validate
     * @return the validation result
     */
    public ValidationResult validateFlow(FlowDocument flow) {
        return validationService.validateFlow(flow);
    }

    /**
     * Validates a FlowDocument with file path context.
     *
     * @param flow     the flow document to validate
     * @param filePath the file path for error reporting
     * @return the validation result
     */
    public ValidationResult validateFlow(FlowDocument flow, String filePath) {
        return validationService.validateFlow(flow, filePath);
    }

    /**
     * Compiles a FlowDocument to FlowIr.
     *
     * @param flow the flow document to compile
     * @return the compilation result with IR or errors
     */
    public CompilerResult<FlowIr> compileFlow(FlowDocument flow) {
        return flowCompiler.compile(flow);
    }

    /**
     * Serializes a FlowIr to a JSON string.
     *
     * @param ir the compiled flow IR
     * @return the JSON string representation
     * @throws SerializationException if serialization fails
     */
    public String serializeIr(FlowIr ir) throws SerializationException {
        return serializationService.serialize(ir);
    }

    /**
     * Deserializes a JSON string to a FlowIr.
     *
     * @param data the JSON string data
     * @return the deserialized FlowIr
     * @throws SerializationException if deserialization fails
     */
    public FlowIr deserializeIr(String data) throws SerializationException {
        return serializationService.deserializeFlow(data);
    }

    /**
     * Full compilation pipeline: parse -> validate -> compile.
     * Returns a result containing the IR or all accumulated errors.
     *
     * @param content the Markdown content
     * @return a CompilationResult with IR or errors
     */
    public FlowCompilationResult compile(String content) {
        Objects.requireNonNull(content, "content cannot be null");

        // Step 1: Parse
        var parseResult = parseService.parseFlowMarkdown(content);
        if (parseResult.isFailure()) {
            return FlowCompilationResult.parseFailure(parseResult.getErrors());
        }

        FlowDocument document = parseResult.getDocument();

        // Step 2: Validate
        var validationResult = validationService.validateFlow(document);
        if (!validationResult.isValid()) {
            return FlowCompilationResult.validationFailure(validationResult);
        }

        // Step 3: Compile
        var compileResult = flowCompiler.compile(document);
        if (compileResult.isFailure()) {
            return FlowCompilationResult.compileFailure(compileResult.getErrors());
        }

        return FlowCompilationResult.success(compileResult.getIr(), validationResult.warnings());
    }

    /**
     * Full compilation pipeline from file.
     *
     * @param markdownFile the path to the Markdown file
     * @return a CompilationResult with IR or errors
     * @throws IOException if the file cannot be read
     */
    public FlowCompilationResult compile(Path markdownFile) throws IOException {
        Objects.requireNonNull(markdownFile, "markdownFile cannot be null");

        String content = Files.readString(markdownFile);
        FlowCompilationResult result = compile(content);

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
     * @return a SerializedResult with JSON string or errors
     */
    public SerializedFlowResult compileAndSerialize(String content) {
        FlowCompilationResult result = compile(content);

        if (result.isFailure()) {
            return SerializedFlowResult.failure(result);
        }

        try {
            String json = serializationService.serialize(result.getIr());
            return SerializedFlowResult.success(json, result);
        } catch (SerializationException e) {
            return SerializedFlowResult.serializationFailure(
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
        var result = parseService.parseFlowMarkdown(content);
        return result.isFailure() ? result.getErrors() : List.of();
    }

    /**
     * Checks if Markdown content can be successfully parsed as a flow.
     *
     * @param content the Markdown content
     * @return true if the content can be parsed as a flow
     */
    public boolean canParse(String content) {
        return parseService.parseFlowMarkdown(content).isSuccess();
    }

    /**
     * Result of flow compilation containing either the compiled IR or errors.
     */
    public static final class FlowCompilationResult {
        private final FlowIr ir;
        private final List<ru.hgd.sdlc.compiler.domain.parser.ParseError> parseErrors;
        private final ValidationResult validationResult;
        private final List<CompilerError> compileErrors;
        private final String filePath;

        private FlowCompilationResult(
                FlowIr ir,
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

        public static FlowCompilationResult success(FlowIr ir, List<ru.hgd.sdlc.compiler.domain.validation.ValidationError> warnings) {
            return new FlowCompilationResult(ir, List.of(), ValidationResult.valid(warnings), List.of(), null);
        }

        public static FlowCompilationResult parseFailure(List<ru.hgd.sdlc.compiler.domain.parser.ParseError> errors) {
            return new FlowCompilationResult(null, errors, ValidationResult.valid(), List.of(), null);
        }

        public static FlowCompilationResult validationFailure(ValidationResult result) {
            return new FlowCompilationResult(null, List.of(), result, List.of(), null);
        }

        public static FlowCompilationResult compileFailure(List<CompilerError> errors) {
            return new FlowCompilationResult(null, List.of(), ValidationResult.valid(), errors, null);
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

        public FlowIr getIr() {
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

        public FlowCompilationResult withFilePath(String filePath) {
            return new FlowCompilationResult(ir, parseErrors, validationResult, compileErrors, filePath);
        }

        public String getFailureStage() {
            if (hasParseErrors()) return "PARSE";
            if (hasValidationErrors()) return "VALIDATION";
            if (hasCompileErrors()) return "COMPILE";
            return isSuccess() ? null : "UNKNOWN";
        }
    }

    /**
     * Result of flow compilation with serialization.
     */
    public static final class SerializedFlowResult {
        private final String json;
        private final FlowCompilationResult compilationResult;
        private final CompilerError serializationError;

        private SerializedFlowResult(
                String json,
                FlowCompilationResult compilationResult,
                CompilerError serializationError) {
            this.json = json;
            this.compilationResult = compilationResult;
            this.serializationError = serializationError;
        }

        public static SerializedFlowResult success(String json, FlowCompilationResult result) {
            return new SerializedFlowResult(json, result, null);
        }

        public static SerializedFlowResult failure(FlowCompilationResult result) {
            return new SerializedFlowResult(null, result, null);
        }

        public static SerializedFlowResult serializationFailure(CompilerError error) {
            return new SerializedFlowResult(null, null, error);
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

        public FlowCompilationResult getCompilationResult() {
            return compilationResult;
        }

        public CompilerError getSerializationError() {
            return serializationError;
        }
    }
}

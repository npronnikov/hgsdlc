package ru.hgd.sdlc.compiler.application;

import org.springframework.stereotype.Service;
import ru.hgd.sdlc.compiler.domain.compiler.CompiledIR;
import ru.hgd.sdlc.compiler.domain.ir.serialization.SerializationException;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.SkillDocument;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.compiler.domain.parser.ParsedMarkdown;
import ru.hgd.sdlc.compiler.domain.parser.ParseError;
import ru.hgd.sdlc.compiler.domain.compiler.SkillIr;
import ru.hgd.sdlc.compiler.domain.validation.ValidationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Main facade for the compiler module.
 * Provides a unified API for compiling flows and skills from Markdown to IR.
 *
 * <p>This service is the primary entry point for all compilation operations.
 * It orchestrates the full compilation pipeline:
 * Markdown -> Document -> Validation -> IR -> Serialization
 *
 * <p>Per ADR-002: Runtime executes compiled IR, not Markdown.
 * The compiler transforms authored documents into executable IR.
 */
@Service
public class CompilerService {

    private final ParseService parseService;
    private final ValidationService validationService;
    private final SerializationService serializationService;
    private final FlowCompilerService flowCompilerService;
    private final SkillCompilerService skillCompilerService;

    public CompilerService(
            ParseService parseService,
            ValidationService validationService,
            SerializationService serializationService,
            FlowCompilerService flowCompilerService,
            SkillCompilerService skillCompilerService) {
        this.parseService = Objects.requireNonNull(parseService, "parseService cannot be null");
        this.validationService = Objects.requireNonNull(validationService, "validationService cannot be null");
        this.serializationService = Objects.requireNonNull(serializationService, "serializationService cannot be null");
        this.flowCompilerService = Objects.requireNonNull(flowCompilerService, "flowCompilerService cannot be null");
        this.skillCompilerService = Objects.requireNonNull(skillCompilerService, "skillCompilerService cannot be null");
    }

    // ============================================================
    // Flow Compilation API
    // ============================================================

    /**
     * Compiles flow Markdown content to IR.
     * Full pipeline: parse -> validate -> compile.
     *
     * @param markdownContent the Markdown content
     * @return the compilation result
     */
    public CompileFlowResult compileFlow(String markdownContent) {
        Objects.requireNonNull(markdownContent, "markdownContent cannot be null");
        return CompileFlowResult.from(flowCompilerService.compile(markdownContent));
    }

    /**
     * Compiles flow Markdown from a file to IR.
     *
     * @param markdownFile the path to the Markdown file
     * @return the compilation result
     * @throws IOException if the file cannot be read
     */
    public CompileFlowResult compileFlow(Path markdownFile) throws IOException {
        Objects.requireNonNull(markdownFile, "markdownFile cannot be null");
        return CompileFlowResult.from(flowCompilerService.compile(markdownFile));
    }

    // ============================================================
    // Skill Compilation API
    // ============================================================

    /**
     * Compiles skill Markdown content to IR.
     * Full pipeline: parse -> validate -> compile.
     *
     * @param markdownContent the Markdown content
     * @return the compilation result
     */
    public CompileSkillResult compileSkill(String markdownContent) {
        Objects.requireNonNull(markdownContent, "markdownContent cannot be null");
        return CompileSkillResult.from(skillCompilerService.compile(markdownContent));
    }

    /**
     * Compiles skill Markdown from a file to IR.
     *
     * @param markdownFile the path to the Markdown file
     * @return the compilation result
     * @throws IOException if the file cannot be read
     */
    public CompileSkillResult compileSkill(Path markdownFile) throws IOException {
        Objects.requireNonNull(markdownFile, "markdownFile cannot be null");
        return CompileSkillResult.from(skillCompilerService.compile(markdownFile));
    }

    // ============================================================
    // Auto-detect API
    // ============================================================

    /**
     * Compiles Markdown content, auto-detecting whether it's a flow or skill.
     *
     * @param markdownContent the Markdown content
     * @return the compilation result
     * @throws IllegalArgumentException if document type cannot be determined
     */
    public CompileResult compile(String markdownContent) {
        Objects.requireNonNull(markdownContent, "markdownContent cannot be null");

        // Extract frontmatter to determine type
        var extractResult = parseService.extractFrontmatter(markdownContent);
        if (extractResult.isFailure()) {
            return CompileResult.parseFailure(extractResult.getError());
        }

        ParsedMarkdown parsed = extractResult.getValue();

        if (parseService.isFlowDocument(parsed)) {
            return CompileResult.fromFlow(compileFlow(markdownContent));
        } else if (parseService.isSkillDocument(parsed)) {
            return CompileResult.fromSkill(compileSkill(markdownContent));
        }

        return CompileResult.unknownType("Cannot determine document type: missing 'type' field or type-specific fields");
    }

    /**
     * Compiles Markdown from a file, auto-detecting whether it's a flow or skill.
     *
     * @param markdownFile the path to the Markdown file
     * @return the compilation result
     * @throws IOException if the file cannot be read
     */
    public CompileResult compile(Path markdownFile) throws IOException {
        Objects.requireNonNull(markdownFile, "markdownFile cannot be null");

        String content = Files.readString(markdownFile);
        CompileResult result = compile(content);

        if (result.isFailure()) {
            return result.withFilePath(markdownFile.toString());
        }

        return result;
    }

    // ============================================================
    // Serialization API
    // ============================================================

    /**
     * Serializes compiled IR to a JSON string.
     *
     * @param ir the compiled IR
     * @return the JSON string
     * @throws SerializationException if serialization fails
     */
    public String serialize(CompiledIR ir) throws SerializationException {
        return serializationService.serialize(ir);
    }

    /**
     * Deserializes a JSON string to compiled IR.
     *
     * @param json the JSON string
     * @return the deserialized IR
     * @throws SerializationException if deserialization fails
     */
    public CompiledIR deserialize(String json) throws SerializationException {
        return serializationService.deserialize(json);
    }

    // ============================================================
    // Utility Methods
    // ============================================================

    /**
     * Checks if Markdown content is a valid flow or skill.
     *
     * @param markdownContent the Markdown content
     * @return true if the content can be compiled
     */
    public boolean canCompile(String markdownContent) {
        var result = compile(markdownContent);
        return result.isSuccess();
    }

    /**
     * Gets all errors from a compilation attempt without throwing.
     *
     * @param markdownContent the Markdown content
     * @return a list of formatted error messages
     */
    public List<String> getErrors(String markdownContent) {
        var result = compile(markdownContent);
        return result.getAllErrorMessages();
    }

    /**
     * Determines the document type from Markdown content.
     *
     * @param markdownContent the Markdown content
     * @return the document type, or empty if unknown
     */
    public Optional<DocumentType> detectDocumentType(String markdownContent) {
        var extractResult = parseService.extractFrontmatter(markdownContent);
        if (extractResult.isFailure()) {
            return Optional.empty();
        }

        ParsedMarkdown parsed = extractResult.getValue();

        if (parseService.isFlowDocument(parsed)) {
            return Optional.of(DocumentType.FLOW);
        } else if (parseService.isSkillDocument(parsed)) {
            return Optional.of(DocumentType.SKILL);
        }

        return Optional.empty();
    }

    // ============================================================
    // Result Types
    // ============================================================

    /**
     * Document type enumeration.
     */
    public enum DocumentType {
        FLOW,
        SKILL
    }

    /**
     * Result of flow compilation.
     */
    public static final class CompileFlowResult {
        private final FlowIr ir;
        private final List<ParseError> parseErrors;
        private final ValidationResult validationResult;
        private final List<ru.hgd.sdlc.compiler.domain.compiler.CompilerError> compileErrors;
        private final String filePath;

        private CompileFlowResult(
                FlowIr ir,
                List<ParseError> parseErrors,
                ValidationResult validationResult,
                List<ru.hgd.sdlc.compiler.domain.compiler.CompilerError> compileErrors,
                String filePath) {
            this.ir = ir;
            this.parseErrors = parseErrors;
            this.validationResult = validationResult;
            this.compileErrors = compileErrors;
            this.filePath = filePath;
        }

        static CompileFlowResult from(FlowCompilerService.FlowCompilationResult result) {
            return new CompileFlowResult(
                result.getIr(),
                result.getParseErrors(),
                result.getValidationResult(),
                result.getCompileErrors(),
                result.getFilePath()
            );
        }

        public boolean isSuccess() {
            return ir != null;
        }

        public boolean isFailure() {
            return ir == null;
        }

        public FlowIr getIr() {
            return ir;
        }

        public List<ParseError> getParseErrors() {
            return parseErrors;
        }

        public ValidationResult getValidationResult() {
            return validationResult;
        }

        public List<ru.hgd.sdlc.compiler.domain.compiler.CompilerError> getCompileErrors() {
            return compileErrors;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getFailureStage() {
            if (parseErrors != null && !parseErrors.isEmpty()) return "PARSE";
            if (validationResult != null && !validationResult.isValid()) return "VALIDATION";
            if (compileErrors != null && !compileErrors.isEmpty()) return "COMPILE";
            return isSuccess() ? null : "UNKNOWN";
        }

        public List<String> getAllErrorMessages() {
            java.util.ArrayList<String> messages = new java.util.ArrayList<>();

            if (parseErrors != null) {
                for (ParseError e : parseErrors) {
                    messages.add("[" + e.code() + "] " + e.message());
                }
            }

            if (validationResult != null) {
                for (var e : validationResult.errors()) {
                    messages.add("[" + e.code() + "] " + e.message());
                }
            }

            if (compileErrors != null) {
                for (var e : compileErrors) {
                    messages.add("[" + e.code() + "] " + e.message());
                }
            }

            return messages;
        }
    }

    /**
     * Result of skill compilation.
     */
    public static final class CompileSkillResult {
        private final SkillIr ir;
        private final List<ParseError> parseErrors;
        private final ValidationResult validationResult;
        private final List<ru.hgd.sdlc.compiler.domain.compiler.CompilerError> compileErrors;
        private final String filePath;

        private CompileSkillResult(
                SkillIr ir,
                List<ParseError> parseErrors,
                ValidationResult validationResult,
                List<ru.hgd.sdlc.compiler.domain.compiler.CompilerError> compileErrors,
                String filePath) {
            this.ir = ir;
            this.parseErrors = parseErrors;
            this.validationResult = validationResult;
            this.compileErrors = compileErrors;
            this.filePath = filePath;
        }

        static CompileSkillResult from(SkillCompilerService.SkillCompilationResult result) {
            return new CompileSkillResult(
                result.getIr(),
                result.getParseErrors(),
                result.getValidationResult(),
                result.getCompileErrors(),
                result.getFilePath()
            );
        }

        public boolean isSuccess() {
            return ir != null;
        }

        public boolean isFailure() {
            return ir == null;
        }

        public SkillIr getIr() {
            return ir;
        }

        public List<ParseError> getParseErrors() {
            return parseErrors;
        }

        public ValidationResult getValidationResult() {
            return validationResult;
        }

        public List<ru.hgd.sdlc.compiler.domain.compiler.CompilerError> getCompileErrors() {
            return compileErrors;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getFailureStage() {
            if (parseErrors != null && !parseErrors.isEmpty()) return "PARSE";
            if (validationResult != null && !validationResult.isValid()) return "VALIDATION";
            if (compileErrors != null && !compileErrors.isEmpty()) return "COMPILE";
            return isSuccess() ? null : "UNKNOWN";
        }

        public List<String> getAllErrorMessages() {
            java.util.ArrayList<String> messages = new java.util.ArrayList<>();

            if (parseErrors != null) {
                for (ParseError e : parseErrors) {
                    messages.add("[" + e.code() + "] " + e.message());
                }
            }

            if (validationResult != null) {
                for (var e : validationResult.errors()) {
                    messages.add("[" + e.code() + "] " + e.message());
                }
            }

            if (compileErrors != null) {
                for (var e : compileErrors) {
                    messages.add("[" + e.code() + "] " + e.message());
                }
            }

            return messages;
        }
    }

    /**
     * Polymorphic compilation result.
     */
    public static final class CompileResult {
        private final CompiledIR ir;
        private final DocumentType type;
        private final List<ParseError> parseErrors;
        private final ValidationResult validationResult;
        private final List<ru.hgd.sdlc.compiler.domain.compiler.CompilerError> compileErrors;
        private final String errorMessage;
        private final String filePath;

        private CompileResult(
                CompiledIR ir,
                DocumentType type,
                List<ParseError> parseErrors,
                ValidationResult validationResult,
                List<ru.hgd.sdlc.compiler.domain.compiler.CompilerError> compileErrors,
                String errorMessage,
                String filePath) {
            this.ir = ir;
            this.type = type;
            this.parseErrors = parseErrors;
            this.validationResult = validationResult;
            this.compileErrors = compileErrors;
            this.errorMessage = errorMessage;
            this.filePath = filePath;
        }

        static CompileResult fromFlow(CompileFlowResult flowResult) {
            return new CompileResult(
                flowResult.getIr(),
                DocumentType.FLOW,
                flowResult.getParseErrors(),
                flowResult.getValidationResult(),
                flowResult.getCompileErrors(),
                null,
                flowResult.getFilePath()
            );
        }

        static CompileResult fromSkill(CompileSkillResult skillResult) {
            return new CompileResult(
                skillResult.getIr(),
                DocumentType.SKILL,
                skillResult.getParseErrors(),
                skillResult.getValidationResult(),
                skillResult.getCompileErrors(),
                null,
                skillResult.getFilePath()
            );
        }

        static CompileResult parseFailure(ParseError error) {
            return new CompileResult(
                null, null, List.of(error),
                ValidationResult.valid(), List.of(), null, null
            );
        }

        static CompileResult unknownType(String message) {
            return new CompileResult(
                null, null, List.of(),
                ValidationResult.valid(), List.of(), message, null
            );
        }

        public boolean isSuccess() {
            return ir != null;
        }

        public boolean isFailure() {
            return ir == null;
        }

        public CompiledIR getIr() {
            return ir;
        }

        public FlowIr getFlowIr() {
            return ir instanceof FlowIr ? (FlowIr) ir : null;
        }

        public SkillIr getSkillIr() {
            return ir instanceof SkillIr ? (SkillIr) ir : null;
        }

        public DocumentType getType() {
            return type;
        }

        public List<ParseError> getParseErrors() {
            return parseErrors;
        }

        public ValidationResult getValidationResult() {
            return validationResult;
        }

        public List<ru.hgd.sdlc.compiler.domain.compiler.CompilerError> getCompileErrors() {
            return compileErrors;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getFilePath() {
            return filePath;
        }

        public CompileResult withFilePath(String filePath) {
            return new CompileResult(
                ir, type, parseErrors, validationResult, compileErrors, errorMessage, filePath
            );
        }

        public String getFailureStage() {
            if (errorMessage != null) return "UNKNOWN_TYPE";
            if (parseErrors != null && !parseErrors.isEmpty()) return "PARSE";
            if (validationResult != null && !validationResult.isValid()) return "VALIDATION";
            if (compileErrors != null && !compileErrors.isEmpty()) return "COMPILE";
            return isSuccess() ? null : "UNKNOWN";
        }

        public List<String> getAllErrorMessages() {
            java.util.ArrayList<String> messages = new java.util.ArrayList<>();

            if (errorMessage != null) {
                messages.add(errorMessage);
            }

            if (parseErrors != null) {
                for (ParseError e : parseErrors) {
                    messages.add("[" + e.code() + "] " + e.message());
                }
            }

            if (validationResult != null) {
                for (var e : validationResult.errors()) {
                    messages.add("[" + e.code() + "] " + e.message());
                }
            }

            if (compileErrors != null) {
                for (var e : compileErrors) {
                    messages.add("[" + e.code() + "] " + e.message());
                }
            }

            return messages;
        }
    }
}

package ru.hgd.sdlc.compiler.domain.compiler;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Result of compilation operation that can contain multiple errors.
 * Collects all compilation errors instead of failing on the first one.
 *
 * @param <T> the type of the compiled IR
 */
public final class CompilerResult<T extends CompiledIR> {

    private final T ir;
    private final List<CompilerError> errors;

    private CompilerResult(T ir, List<CompilerError> errors) {
        this.ir = ir;
        this.errors = List.copyOf(errors);
    }

    /**
     * Creates a successful compilation result.
     */
    public static <T extends CompiledIR> CompilerResult<T> success(T ir) {
        Objects.requireNonNull(ir, "ir cannot be null");
        return new CompilerResult<>(ir, List.of());
    }

    /**
     * Creates a failed compilation result with errors.
     */
    public static <T extends CompiledIR> CompilerResult<T> failure(List<CompilerError> errors) {
        Objects.requireNonNull(errors, "errors cannot be null");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("errors cannot be empty for failure");
        }
        return new CompilerResult<>(null, errors);
    }

    /**
     * Creates a failed compilation result with a single error.
     */
    public static <T extends CompiledIR> CompilerResult<T> failure(CompilerError error) {
        Objects.requireNonNull(error, "error cannot be null");
        return new CompilerResult<>(null, List.of(error));
    }

    /**
     * Creates a result with both IR and warnings.
     * IR is present but has non-fatal warnings.
     */
    public static <T extends CompiledIR> CompilerResult<T> withWarnings(T ir, List<CompilerError> warnings) {
        Objects.requireNonNull(ir, "ir cannot be null");
        Objects.requireNonNull(warnings, "warnings cannot be null");
        // Verify all are warnings
        for (CompilerError w : warnings) {
            if (!w.isWarning()) {
                throw new IllegalArgumentException("All errors in withWarnings must be warnings (code starting with W)");
            }
        }
        return new CompilerResult<>(ir, warnings);
    }

    /**
     * Returns true if compilation succeeded without errors.
     */
    public boolean isSuccess() {
        return ir != null && (errors.isEmpty() || errors.stream().allMatch(CompilerError::isWarning));
    }

    /**
     * Returns true if compilation succeeded but has warnings.
     */
    public boolean hasWarnings() {
        return ir != null && !errors.isEmpty() && errors.stream().allMatch(CompilerError::isWarning);
    }

    /**
     * Returns true if compilation failed with errors.
     */
    public boolean isFailure() {
        return ir == null && !errors.isEmpty();
    }

    /**
     * Returns the compiled IR, or null if compilation failed.
     */
    public T getIr() {
        return ir;
    }

    /**
     * Returns all errors (both fatal errors and warnings).
     */
    public List<CompilerError> getErrors() {
        return errors;
    }

    /**
     * Returns only fatal errors (code starting with E).
     */
    public List<CompilerError> getFatalErrors() {
        return errors.stream()
            .filter(CompilerError::isError)
            .toList();
    }

    /**
     * Returns only warnings (code starting with W).
     */
    public List<CompilerError> getWarnings() {
        return errors.stream()
            .filter(CompilerError::isWarning)
            .toList();
    }

    /**
     * Returns the first error, or null if no errors.
     */
    public CompilerError getFirstError() {
        return errors.isEmpty() ? null : errors.get(0);
    }

    /**
     * Maps the IR to another type if successful.
     */
    @SuppressWarnings("unchecked")
    public <U extends CompiledIR> CompilerResult<U> map(Function<T, U> mapper) {
        if (ir == null) {
            return (CompilerResult<U>) this;
        }
        return new CompilerResult<>(mapper.apply(ir), errors);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompilerResult<?> that = (CompilerResult<?>) o;
        return Objects.equals(ir, that.ir) && errors.equals(that.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ir, errors);
    }

    @Override
    public String toString() {
        if (isSuccess()) {
            return "CompilerResult.success(" + ir + ")";
        }
        if (hasWarnings()) {
            return "CompilerResult.withWarnings(" + ir + ", " + errors.size() + " warnings)";
        }
        return "CompilerResult.failure(" + errors.size() + " errors)";
    }
}

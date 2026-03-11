package ru.hgd.sdlc.compiler.domain.compiler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.compiler.domain.model.authored.*;
import ru.hgd.sdlc.compiler.domain.model.ir.IrMetadata;
import ru.hgd.sdlc.compiler.domain.model.ir.FlowIr;
import ru.hgd.sdlc.shared.hashing.Sha256;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompilerResult")
class CompilerResultTest {

    private Sha256 hash(String value) {
        return Sha256.of(value);
    }

    private FlowIr createTestIr() {
        IrMetadata metadata = IrMetadata.builder()
            .packageChecksum(hash("package"))
            .irChecksum(hash("ir"))
            .compiledAt(Instant.now())
            .compilerVersion("1.0.0")
            .build();

        return FlowIr.builder()
            .flowId(FlowId.of("test-flow"))
            .flowVersion(SemanticVersion.of("1.0.0"))
            .metadata(metadata)
            .build();
    }

    @Nested
    @DisplayName("success")
    class SuccessTest {

        @Test
        @DisplayName("creates successful result")
        void createsSuccessfulResult() {
            FlowIr ir = createTestIr();

            CompilerResult<FlowIr> result = CompilerResult.success(ir);

            assertTrue(result.isSuccess());
            assertFalse(result.isFailure());
            assertFalse(result.hasWarnings());
            assertEquals(ir, result.getIr());
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("rejects null IR")
        void rejectsNullIr() {
            assertThrows(NullPointerException.class, () -> {
                CompilerResult.success(null);
            });
        }
    }

    @Nested
    @DisplayName("failure")
    class FailureTest {

        @Test
        @DisplayName("creates failed result with single error")
        void createsFailedResultWithSingleError() {
            CompilerError error = CompilerError.missingField("test", "location");

            CompilerResult<FlowIr> result = CompilerResult.failure(error);

            assertTrue(result.isFailure());
            assertFalse(result.isSuccess());
            assertNull(result.getIr());
            assertEquals(1, result.getErrors().size());
            assertEquals(error, result.getFirstError());
        }

        @Test
        @DisplayName("creates failed result with multiple errors")
        void createsFailedResultWithMultipleErrors() {
            List<CompilerError> errors = List.of(
                CompilerError.missingField("field1", "loc1"),
                CompilerError.invalidReference("ref", "id", "loc2")
            );

            CompilerResult<FlowIr> result = CompilerResult.failure(errors);

            assertTrue(result.isFailure());
            assertEquals(2, result.getErrors().size());
        }

        @Test
        @DisplayName("rejects empty error list")
        void rejectsEmptyErrorList() {
            assertThrows(IllegalArgumentException.class, () -> {
                CompilerResult.failure(List.of());
            });
        }

        @Test
        @DisplayName("rejects null error")
        void rejectsNullError() {
            assertThrows(NullPointerException.class, () -> {
                CompilerResult.failure((CompilerError) null);
            });
        }
    }

    @Nested
    @DisplayName("warnings")
    class WarningsTest {

        @Test
        @DisplayName("creates result with warnings")
        void createsResultWithWarnings() {
            FlowIr ir = createTestIr();
            List<CompilerError> warnings = List.of(
                CompilerError.of("W2001", "This is a warning")
            );

            CompilerResult<FlowIr> result = CompilerResult.withWarnings(ir, warnings);

            assertTrue(result.isSuccess());
            assertTrue(result.hasWarnings());
            assertFalse(result.isFailure());
            assertEquals(ir, result.getIr());
            assertEquals(1, result.getWarnings().size());
        }

        @Test
        @DisplayName("rejects non-warning errors in withWarnings")
        void rejectsNonWarningErrorsInWithWarnings() {
            FlowIr ir = createTestIr();
            List<CompilerError> errors = List.of(
                CompilerError.of("E2001", "This is an error")
            );

            assertThrows(IllegalArgumentException.class, () -> {
                CompilerResult.withWarnings(ir, errors);
            });
        }
    }

    @Nested
    @DisplayName("error categorization")
    class ErrorCategorizationTest {

        @Test
        @DisplayName("getFatalErrors returns only errors")
        void getFatalErrorsReturnsOnlyErrors() {
            List<CompilerError> errors = List.of(
                CompilerError.of("E2001", "Error 1"),
                CompilerError.of("W2001", "Warning 1"),
                CompilerError.of("E2002", "Error 2"),
                CompilerError.of("W2002", "Warning 2")
            );

            CompilerResult<FlowIr> result = CompilerResult.failure(errors);

            assertEquals(2, result.getFatalErrors().size());
            assertTrue(result.getFatalErrors().stream().allMatch(CompilerError::isError));
        }

        @Test
        @DisplayName("getWarnings returns only warnings")
        void getWarningsReturnsOnlyWarnings() {
            List<CompilerError> errors = List.of(
                CompilerError.of("E2001", "Error 1"),
                CompilerError.of("W2001", "Warning 1"),
                CompilerError.of("E2002", "Error 2")
            );

            CompilerResult<FlowIr> result = CompilerResult.failure(errors);

            assertEquals(1, result.getWarnings().size());
            assertTrue(result.getWarnings().stream().allMatch(CompilerError::isWarning));
        }
    }

    @Nested
    @DisplayName("map")
    class MapTest {

        @Test
        @DisplayName("maps successful result")
        void mapsSuccessfulResult() {
            FlowIr ir = createTestIr();

            CompilerResult<FlowIr> result = CompilerResult.success(ir);
            CompilerResult<SkillIr> mapped = result.map(flowIr -> {
                return SkillIr.builder()
                    .skillId(SkillId.of(flowIr.flowId().value()))
                    .skillVersion(flowIr.flowVersion())
                    .name("Mapped")
                    .handler(HandlerRef.builtin("test"))
                    .irChecksum(hash("skill"))
                    .compiledAt(Instant.now())
                    .compilerVersion("1.0")
                    .build();
            });

            assertTrue(mapped.isSuccess());
            assertEquals("test-flow", mapped.getIr().skillId().value());
        }

        @Test
        @DisplayName("preserves errors on failure")
        void preservesErrorsOnFailure() {
            List<CompilerError> errors = List.of(
                CompilerError.missingField("test", "location")
            );

            CompilerResult<FlowIr> result = CompilerResult.failure(errors);
            CompilerResult<SkillIr> mapped = result.map(ir -> {
                throw new RuntimeException("Should not be called");
            });

            assertTrue(mapped.isFailure());
            assertEquals(errors, mapped.getErrors());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("formats success correctly")
        void formatsSuccessCorrectly() {
            FlowIr ir = createTestIr();
            CompilerResult<FlowIr> result = CompilerResult.success(ir);

            String str = result.toString();

            assertTrue(str.contains("success"));
        }

        @Test
        @DisplayName("formats failure correctly")
        void formatsFailureCorrectly() {
            CompilerResult<FlowIr> result = CompilerResult.failure(
                CompilerError.missingField("test", "location")
            );

            String str = result.toString();

            assertTrue(str.contains("failure"));
            assertTrue(str.contains("1"));
        }
    }
}

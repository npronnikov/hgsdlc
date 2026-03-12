package ru.hgd.sdlc.registry.application.verifier;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VerificationResult.
 */
class VerificationResultTest {

    @Test
    void shouldCreateValidResult() {
        VerificationResult result = VerificationResult.valid();

        assertTrue(result.isValid());
        assertTrue(result.getIssuesList().isEmpty());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
        assertTrue(result.getInfos().isEmpty());
    }

    @Test
    void shouldCreateInvalidResultWithIssues() {
        List<VerificationIssue> issues = List.of(
            VerificationIssue.error("CODE1", "Error 1"),
            VerificationIssue.warning("CODE2", "Warning 1")
        );

        VerificationResult result = VerificationResult.invalid(issues);

        assertFalse(result.isValid());
        assertEquals(2, result.getIssuesList().size());
        assertEquals(1, result.getErrors().size());
        assertEquals(1, result.getWarnings().size());
    }

    @Test
    void shouldCreateInvalidResultWithSingleError() {
        VerificationResult result = VerificationResult.invalid("CODE", "Message");

        assertFalse(result.isValid());
        assertEquals(1, result.getIssuesList().size());
        assertEquals("CODE", result.getIssuesList().get(0).getCode());
    }

    @Test
    void shouldCreateInvalidResultWithDetails() {
        VerificationResult result = VerificationResult.invalid("CODE", "Message", "Details");

        assertFalse(result.isValid());
        assertEquals(1, result.getIssuesList().size());
        assertEquals("Details", result.getIssuesList().get(0).getDetails());
    }

    @Test
    void shouldFilterIssuesBySeverity() {
        VerificationResult result = VerificationResult.builder()
            .success(false)
            .issue(VerificationIssue.error("E1", "Error 1"))
            .issue(VerificationIssue.error("E2", "Error 2"))
            .issue(VerificationIssue.warning("W1", "Warning 1"))
            .issue(VerificationIssue.info("I1", "Info 1"))
            .issue(VerificationIssue.info("I2", "Info 2"))
            .build();

        assertEquals(2, result.getErrors().size());
        assertEquals(1, result.getWarnings().size());
        assertEquals(2, result.getInfos().size());
    }

    @Test
    void shouldCreateValidResultWithInfos() {
        List<VerificationIssue> infos = List.of(
            VerificationIssue.info("I1", "Info 1"),
            VerificationIssue.info("I2", "Info 2")
        );

        VerificationResult result = VerificationResult.validWithInfos(infos);

        assertTrue(result.isValid());
        assertEquals(2, result.getInfos().size());
    }

    @Test
    void shouldCombineResults() {
        VerificationResult result1 = VerificationResult.builder()
            .success(true)
            .issue(VerificationIssue.warning("W1", "Warning 1"))
            .build();

        VerificationResult result2 = VerificationResult.builder()
            .success(true)
            .issue(VerificationIssue.info("I1", "Info 1"))
            .build();

        VerificationResult combined = result1.combine(result2);

        assertTrue(combined.isValid());
        assertEquals(2, combined.getIssuesList().size());
    }

    @Test
    void combinedResultShouldBeInvalidIfEitherIsInvalid() {
        VerificationResult validResult = VerificationResult.valid();
        VerificationResult invalid = VerificationResult.invalid("CODE", "Message");

        VerificationResult combined = validResult.combine(invalid);

        assertFalse(combined.isValid());
    }

    @Test
    void shouldSupportBuilder() {
        VerificationResult result = VerificationResult.builder()
            .success(true)
            .issue(VerificationIssue.info("I1", "Info 1"))
            .issue(VerificationIssue.info("I2", "Info 2"))
            .build();

        assertTrue(result.isValid());
        assertEquals(2, result.getIssuesList().size());
    }

    @Test
    void shouldSupportToBuilder() {
        VerificationResult original = VerificationResult.invalid("CODE", "Message");
        VerificationResult modified = original.toBuilder()
            .success(true)
            .build();

        assertTrue(modified.isValid());
        assertEquals(1, modified.getIssuesList().size());
    }

    @Test
    void shouldImplementEqualsAndHashCode() {
        VerificationResult result1 = VerificationResult.invalid("CODE", "Message");
        VerificationResult result2 = VerificationResult.invalid("CODE", "Message");
        VerificationResult result3 = VerificationResult.valid();

        assertEquals(result1, result2);
        assertEquals(result1.hashCode(), result2.hashCode());
        assertNotEquals(result1, result3);
    }

    @Test
    void shouldHaveReasonableToString() {
        VerificationResult validEmpty = VerificationResult.valid();
        VerificationResult validWithIssues = VerificationResult.validWithInfos(
            List.of(VerificationIssue.info("CODE", "Info"))
        );
        VerificationResult invalid = VerificationResult.invalid("CODE", "Message");

        assertTrue(validEmpty.toString().contains("valid=true"));
        assertTrue(validEmpty.toString().contains("no issues"));
        assertTrue(validWithIssues.toString().contains("valid=true"));
        assertTrue(invalid.toString().contains("valid=false"));
    }

    @Test
    void shouldReturnUnmodifiableIssuesList() {
        VerificationResult result = VerificationResult.invalid("CODE", "Message");

        assertThrows(UnsupportedOperationException.class, () ->
            result.getIssuesList().add(VerificationIssue.error("X", "Y"))
        );
    }
}

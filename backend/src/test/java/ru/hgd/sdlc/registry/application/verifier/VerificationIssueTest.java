package ru.hgd.sdlc.registry.application.verifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VerificationIssue.
 */
class VerificationIssueTest {

    @Test
    void shouldCreateErrorIssue() {
        VerificationIssue issue = VerificationIssue.error("CODE", "Message");

        assertEquals(VerificationSeverity.ERROR, issue.getSeverity());
        assertEquals("CODE", issue.getCode());
        assertEquals("Message", issue.getMessage());
        assertTrue(issue.getDetailsOptional().isEmpty());
    }

    @Test
    void shouldCreateErrorIssueWithDetails() {
        VerificationIssue issue = VerificationIssue.error("CODE", "Message", "Details");

        assertEquals(VerificationSeverity.ERROR, issue.getSeverity());
        assertEquals("CODE", issue.getCode());
        assertEquals("Message", issue.getMessage());
        assertTrue(issue.getDetailsOptional().isPresent());
        assertEquals("Details", issue.getDetailsOptional().get());
    }

    @Test
    void shouldCreateWarningIssue() {
        VerificationIssue issue = VerificationIssue.warning("WARN_CODE", "Warning message");

        assertEquals(VerificationSeverity.WARNING, issue.getSeverity());
        assertEquals("WARN_CODE", issue.getCode());
        assertEquals("Warning message", issue.getMessage());
    }

    @Test
    void shouldCreateWarningIssueWithDetails() {
        VerificationIssue issue = VerificationIssue.warning("WARN_CODE", "Warning", "Extra info");

        assertEquals(VerificationSeverity.WARNING, issue.getSeverity());
        assertEquals("Extra info", issue.getDetails());
    }

    @Test
    void shouldCreateInfoIssue() {
        VerificationIssue issue = VerificationIssue.info("INFO_CODE", "Info message");

        assertEquals(VerificationSeverity.INFO, issue.getSeverity());
        assertEquals("INFO_CODE", issue.getCode());
        assertEquals("Info message", issue.getMessage());
    }

    @Test
    void shouldCreateInfoIssueWithDetails() {
        VerificationIssue issue = VerificationIssue.info("INFO_CODE", "Info", "Details");

        assertEquals(VerificationSeverity.INFO, issue.getSeverity());
        assertEquals("Details", issue.getDetails());
    }

    @Test
    void shouldIncludeDetailsInToString() {
        VerificationIssue issue = VerificationIssue.error("CODE", "Message", "Details");

        String str = issue.toString();
        assertTrue(str.contains("[ERROR]"));
        assertTrue(str.contains("CODE"));
        assertTrue(str.contains("Message"));
        assertTrue(str.contains("Details"));
    }

    @Test
    void shouldNotIncludeNullDetailsInToString() {
        VerificationIssue issue = VerificationIssue.error("CODE", "Message");

        String str = issue.toString();
        assertTrue(str.contains("[ERROR]"));
        assertTrue(str.contains("CODE"));
        assertTrue(str.contains("Message"));
        assertFalse(str.contains("null"));
    }

    @Test
    void shouldSupportBuilder() {
        VerificationIssue issue = VerificationIssue.builder()
            .severity(VerificationSeverity.ERROR)
            .code("CUSTOM_CODE")
            .message("Custom message")
            .details("Custom details")
            .build();

        assertEquals(VerificationSeverity.ERROR, issue.getSeverity());
        assertEquals("CUSTOM_CODE", issue.getCode());
        assertEquals("Custom message", issue.getMessage());
        assertEquals("Custom details", issue.getDetails());
    }

    @Test
    void shouldSupportToBuilder() {
        VerificationIssue original = VerificationIssue.error("CODE", "Message", "Details");
        VerificationIssue modified = original.toBuilder()
            .severity(VerificationSeverity.WARNING)
            .build();

        assertEquals(VerificationSeverity.WARNING, modified.getSeverity());
        assertEquals("CODE", modified.getCode());
        assertEquals("Message", modified.getMessage());
        assertEquals("Details", modified.getDetails());
    }

    @Test
    void shouldImplementEqualsAndHashCode() {
        VerificationIssue issue1 = VerificationIssue.error("CODE", "Message", "Details");
        VerificationIssue issue2 = VerificationIssue.error("CODE", "Message", "Details");
        VerificationIssue issue3 = VerificationIssue.error("CODE", "Message");

        assertEquals(issue1, issue2);
        assertEquals(issue1.hashCode(), issue2.hashCode());
        assertNotEquals(issue1, issue3);
    }
}

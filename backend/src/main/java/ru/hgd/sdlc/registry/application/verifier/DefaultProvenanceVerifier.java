package ru.hgd.sdlc.registry.application.verifier;

import lombok.NonNull;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.registry.domain.model.provenance.Provenance;
import ru.hgd.sdlc.registry.domain.model.provenance.SignedProvenance;
import ru.hgd.sdlc.registry.domain.model.release.ReleasePackage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Default implementation of ProvenanceVerifier.
 * Performs comprehensive verification of release package provenance including:
 * <ul>
 *   <li>Provenance presence check</li>
 *   <li>Required field validation</li>
 *   <li>Checksum verification</li>
 *   <li>Signature verification</li>
 *   <li>Builder info validation</li>
 *   <li>Git commit SHA format validation</li>
 * </ul>
 */
public final class DefaultProvenanceVerifier implements ProvenanceVerifier {

    /**
     * Pattern for valid Git commit SHA-1 hash (40 hex characters).
     */
    private static final Pattern GIT_SHA_PATTERN = Pattern.compile("^[0-9a-fA-F]{40}$");

    @Override
    public VerificationResult verify(@NonNull ReleasePackage pkg) {
        List<VerificationIssue> issues = new ArrayList<>();

        // 1. Check provenance is present
        Provenance provenance = pkg.provenance();
        if (provenance == null) {
            issues.add(VerificationIssue.error(
                "MISSING_PROVENANCE",
                "Release package does not contain provenance"
            ));
            return VerificationResult.invalid(issues);
        }

        // 2. Validate required fields
        validateRequiredFields(provenance, issues);

        // 3. Validate git commit SHA format
        validateGitCommitSha(provenance, issues);

        // 4. Validate builder info
        validateBuilderInfo(provenance, issues);

        // 5. Verify checksums
        verifyChecksums(provenance, issues);

        // 6. Verify signature if present
        if (provenance.isSigned()) {
            VerificationResult sigResult = verifySignature(provenance);
            issues.addAll(sigResult.getIssuesList());
        }

        // Determine validity: any ERROR makes it invalid
        boolean hasErrors = issues.stream()
            .anyMatch(i -> i.getSeverity() == VerificationSeverity.ERROR);

        return hasErrors
            ? VerificationResult.invalid(issues)
            : VerificationResult.builder()
                .success(true)
                .issues(issues)
                .build();
    }

    @Override
    public VerificationResult verifySignature(@NonNull Provenance provenance) {
        List<VerificationIssue> issues = new ArrayList<>();

        if (!provenance.isSigned()) {
            issues.add(VerificationIssue.warning(
                "NO_SIGNATURE",
                "Provenance is not signed",
                "Unsigned provenance cannot be cryptographically verified"
            ));
            return VerificationResult.builder()
                .success(true)
                .issues(issues)
                .build();
        }

        var signature = provenance.getSignature().orElseThrow();

        // Validate signature algorithm
        if (!"Ed25519".equals(signature.algorithm())) {
            issues.add(VerificationIssue.error(
                "UNSUPPORTED_SIGNATURE_ALGORITHM",
                "Unsupported signature algorithm: " + signature.algorithm(),
                "Only Ed25519 signatures are supported"
            ));
            return VerificationResult.invalid(issues);
        }

        try {
            // Create SignedProvenance and verify using embedded public key
            SignedProvenance signedProvenance = SignedProvenance.builder()
                .provenance(provenance)
                .signature(signature)
                .build();

            SignedProvenance.VerificationResult result = signedProvenance.verify();

            if (result.valid()) {
                issues.add(VerificationIssue.info(
                    "SIGNATURE_VALID",
                    "Signature verification successful",
                    "Key ID: " + result.keyId()
                ));
            } else {
                issues.add(VerificationIssue.error(
                    "INVALID_SIGNATURE",
                    "Signature verification failed",
                    result.reason()
                ));
            }
        } catch (Exception e) {
            issues.add(VerificationIssue.error(
                "SIGNATURE_VERIFICATION_ERROR",
                "Failed to verify signature",
                e.getMessage()
            ));
        }

        boolean hasErrors = issues.stream()
            .anyMatch(i -> i.getSeverity() == VerificationSeverity.ERROR);

        return hasErrors
            ? VerificationResult.invalid(issues)
            : VerificationResult.builder()
                .success(true)
                .issues(issues)
                .build();
    }

    /**
     * Validates that all required fields are present in the provenance.
     */
    private void validateRequiredFields(Provenance provenance, List<VerificationIssue> issues) {
        // Check release ID
        if (provenance.getReleaseId() == null) {
            issues.add(VerificationIssue.error(
                "MISSING_RELEASE_ID",
                "Provenance is missing release ID"
            ));
        }

        // Check commit SHA
        if (provenance.getCommitSha() == null || provenance.getCommitSha().isBlank()) {
            issues.add(VerificationIssue.error(
                "MISSING_COMMIT_SHA",
                "Provenance is missing commit SHA"
            ));
        }

        // Check build timestamp
        if (provenance.getBuildTimestamp() == null) {
            issues.add(VerificationIssue.error(
                "MISSING_BUILD_TIMESTAMP",
                "Provenance is missing build timestamp"
            ));
        } else if (provenance.getBuildTimestamp().isAfter(Instant.now())) {
            issues.add(VerificationIssue.warning(
                "FUTURE_BUILD_TIMESTAMP",
                "Build timestamp is in the future",
                "Timestamp: " + provenance.getBuildTimestamp()
            ));
        }

        // Check repository URL
        if (provenance.getRepositoryUrl() == null || provenance.getRepositoryUrl().isBlank()) {
            issues.add(VerificationIssue.error(
                "MISSING_REPOSITORY_URL",
                "Provenance is missing repository URL"
            ));
        }

        // Check commit author
        if (provenance.getCommitAuthor() == null || provenance.getCommitAuthor().isBlank()) {
            issues.add(VerificationIssue.warning(
                "MISSING_COMMIT_AUTHOR",
                "Provenance is missing commit author"
            ));
        }

        // Check committed at
        if (provenance.getCommittedAt() == null) {
            issues.add(VerificationIssue.warning(
                "MISSING_COMMITTED_AT",
                "Provenance is missing commit timestamp"
            ));
        }

        // Check compiler version
        if (provenance.getCompilerVersion() == null || provenance.getCompilerVersion().isBlank()) {
            issues.add(VerificationIssue.warning(
                "MISSING_COMPILER_VERSION",
                "Provenance is missing compiler version"
            ));
        }
    }

    /**
     * Validates the git commit SHA format.
     */
    private void validateGitCommitSha(Provenance provenance, List<VerificationIssue> issues) {
        String commitSha = provenance.getCommitSha();
        if (commitSha != null && !commitSha.isBlank()) {
            if (!GIT_SHA_PATTERN.matcher(commitSha).matches()) {
                issues.add(VerificationIssue.error(
                    "INVALID_GIT_SHA_FORMAT",
                    "Git commit SHA must be a 40-character hex string",
                    "Got: " + commitSha
                ));
            }
        }
    }

    /**
     * Validates builder info is present.
     */
    private void validateBuilderInfo(Provenance provenance, List<VerificationIssue> issues) {
        if (provenance.getBuilder() == null) {
            issues.add(VerificationIssue.error(
                "MISSING_BUILDER_INFO",
                "Provenance is missing builder information"
            ));
        } else {
            var builder = provenance.getBuilder();
            if (builder.name() == null || builder.name().isBlank()) {
                issues.add(VerificationIssue.warning(
                    "MISSING_BUILDER_NAME",
                    "Builder info is missing name"
                ));
            }
            if (builder.version() == null || builder.version().isBlank()) {
                issues.add(VerificationIssue.warning(
                    "MISSING_BUILDER_VERSION",
                    "Builder info is missing version"
                ));
            }
        }
    }

    /**
     * Verifies checksums are present and valid.
     */
    private void verifyChecksums(Provenance provenance, List<VerificationIssue> issues) {
        // Check IR checksum
        if (provenance.getIrChecksum() == null) {
            issues.add(VerificationIssue.error(
                "MISSING_IR_CHECKSUM",
                "Provenance is missing IR checksum"
            ));
        }

        // Check package checksum
        if (provenance.getPackageChecksum() == null) {
            issues.add(VerificationIssue.error(
                "MISSING_PACKAGE_CHECKSUM",
                "Provenance is missing package checksum"
            ));
        }

        // Note: Actual checksum re-computation would require access to the raw IR bytes
        // and package bytes, which is handled at a higher level during package creation.
        // Here we validate that checksums are present and properly formatted.
        if (provenance.getIrChecksum() != null) {
            try {
                // Validate checksum format by attempting to get bytes
                provenance.getIrChecksum().toBytes();
            } catch (Exception e) {
                issues.add(VerificationIssue.error(
                    "INVALID_IR_CHECKSUM_FORMAT",
                    "IR checksum has invalid format",
                    e.getMessage()
                ));
            }
        }

        if (provenance.getPackageChecksum() != null) {
            try {
                // Validate checksum format by attempting to get bytes
                provenance.getPackageChecksum().toBytes();
            } catch (Exception e) {
                issues.add(VerificationIssue.error(
                    "INVALID_PACKAGE_CHECKSUM_FORMAT",
                    "Package checksum has invalid format",
                    e.getMessage()
                ));
            }
        }
    }
}

package ru.hgd.sdlc.benchmark.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.benchmark.application.BenchmarkService;
import ru.hgd.sdlc.benchmark.domain.BenchmarkCaseEntity;
import ru.hgd.sdlc.benchmark.domain.BenchmarkRunEntity;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;

@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {
    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    // --- Cases ---

    @PostMapping("/cases")
    public ResponseEntity<CaseResponse> createCase(
            @RequestBody CreateCaseRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }
        String actor = user != null ? user.getUsername() : "system";
        BenchmarkCaseEntity entity = benchmarkService.createCase(
                request.instruction(),
                request.projectId(),
                request.name(),
                request.artifactType(),
                request.artifactId(),
                request.artifactTypeB(),
                request.artifactIdB(),
                actor
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CaseResponse.from(entity));
    }

    @DeleteMapping("/cases/{caseId}")
    public ResponseEntity<Void> deleteCase(@PathVariable UUID caseId) {
        benchmarkService.deleteCase(caseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cases")
    public List<CaseResponse> listCases() {
        return benchmarkService.listCases().stream().map(CaseResponse::from).toList();
    }

    @GetMapping("/cases/{caseId}/runs")
    public List<RunResponse> listRunsByCase(@PathVariable UUID caseId) {
        return benchmarkService.listRunsByCase(caseId).stream().map(RunResponse::from).toList();
    }

    // --- Runs ---

    @PostMapping("/runs")
    public ResponseEntity<RunResponse> startRun(
            @RequestBody StartRunRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }
        String actor = user != null ? user.getUsername() : "system";
        BenchmarkRunEntity entity = benchmarkService.startRun(
                request.caseId(),
                request.instructionOverride(),
                request.projectIdOverride(),
                request.codingAgent(),
                actor
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(RunResponse.from(entity));
    }

    @GetMapping("/runs")
    public List<RunResponse> listAllRuns() {
        return benchmarkService.listAllRuns().stream().map(RunResponse::from).toList();
    }

    @GetMapping("/runs/{runId}")
    public RunResponse getBenchmarkRun(@PathVariable UUID runId) {
        return RunResponse.from(benchmarkService.getBenchmarkRun(runId));
    }

    @GetMapping("/runs/{runId}/file-comparison")
    public FileComparisonResponse getRunFileComparison(@PathVariable UUID runId) {
        List<FileComparisonItemResponse> items = benchmarkService.getRunFileComparison(runId).stream()
                .map(FileComparisonItemResponse::from)
                .toList();
        return new FileComparisonResponse(runId, items);
    }

    @PostMapping("/runs/{runId}/verdict")
    public RunResponse submitVerdict(
            @PathVariable UUID runId,
            @RequestBody VerdictRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }
        String actor = user != null ? user.getUsername() : "system";
        return RunResponse.from(benchmarkService.submitVerdict(
                runId,
                request.verdict(),
                actor,
                request.reviewComment(),
                request.lineCommentsJson(),
                request.decisionScoresJson()
        ));
    }

    // --- Exception handlers ---

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<String> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    // --- Records ---

    public record CreateCaseRequest(
            @JsonProperty("instruction") String instruction,
            @JsonProperty("project_id") UUID projectId,
            @JsonProperty("name") String name,
            @JsonProperty("artifact_type") String artifactType,
            @JsonProperty("artifact_id") String artifactId,
            @JsonProperty("artifact_type_b") String artifactTypeB,
            @JsonProperty("artifact_id_b") String artifactIdB
    ) {}

    public record StartRunRequest(
            @JsonProperty("case_id") UUID caseId,
            @JsonProperty("instruction_override") String instructionOverride,
            @JsonProperty("project_id_override") UUID projectIdOverride,
            @JsonProperty("coding_agent") String codingAgent
    ) {}

    public record VerdictRequest(
            @JsonProperty("verdict") String verdict,
            @JsonProperty("review_comment") String reviewComment,
            @JsonProperty("line_comments_json") String lineCommentsJson,
            @JsonProperty("decision_scores_json") String decisionScoresJson
    ) {}

    public record FileComparisonResponse(
            @JsonProperty("run_id") UUID runId,
            @JsonProperty("files") List<FileComparisonItemResponse> files
    ) {}

    public record FileComparisonItemResponse(
            @JsonProperty("path") String path,
            @JsonProperty("status_a") String statusA,
            @JsonProperty("status_b") String statusB,
            @JsonProperty("content_a") String contentA,
            @JsonProperty("content_b") String contentB,
            @JsonProperty("exists_a") boolean existsA,
            @JsonProperty("exists_b") boolean existsB,
            @JsonProperty("binary_a") boolean binaryA,
            @JsonProperty("binary_b") boolean binaryB,
            @JsonProperty("truncated_a") boolean truncatedA,
            @JsonProperty("truncated_b") boolean truncatedB,
            @JsonProperty("size_bytes_a") long sizeBytesA,
            @JsonProperty("size_bytes_b") long sizeBytesB
    ) {
        static FileComparisonItemResponse from(ru.hgd.sdlc.benchmark.application.BenchmarkDiffService.FileComparisonEntry entry) {
            return new FileComparisonItemResponse(
                    entry.path(),
                    entry.statusA(),
                    entry.statusB(),
                    entry.contentA(),
                    entry.contentB(),
                    entry.existsA(),
                    entry.existsB(),
                    entry.binaryA(),
                    entry.binaryB(),
                    entry.truncatedA(),
                    entry.truncatedB(),
                    entry.sizeBytesA(),
                    entry.sizeBytesB()
            );
        }
    }

    public record CaseResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            @JsonProperty("instruction") String instruction,
            @JsonProperty("project_id") UUID projectId,
            @JsonProperty("artifact_type") String artifactType,
            @JsonProperty("artifact_id") String artifactId,
            @JsonProperty("artifact_type_b") String artifactTypeB,
            @JsonProperty("artifact_id_b") String artifactIdB,
            @JsonProperty("created_by") String createdBy,
            @JsonProperty("created_at") Instant createdAt
    ) {
        static CaseResponse from(BenchmarkCaseEntity e) {
            return new CaseResponse(e.getId(), e.getName(), e.getInstruction(), e.getProjectId(),
                    e.getArtifactType(), e.getArtifactId(), e.getArtifactBType(), e.getArtifactBId(),
                    e.getCreatedBy(), e.getCreatedAt());
        }
    }

    public record RunResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("case_id") UUID caseId,
            @JsonProperty("artifact_type") String artifactType,
            @JsonProperty("artifact_id") String artifactId,
            @JsonProperty("artifact_version_id") UUID artifactVersionId,
            @JsonProperty("artifact_type_b") String artifactTypeB,
            @JsonProperty("artifact_id_b") String artifactIdB,
            @JsonProperty("artifact_version_id_b") UUID artifactVersionIdB,
            @JsonProperty("coding_agent") String codingAgent,
            @JsonProperty("run_a_id") UUID runAId,
            @JsonProperty("run_b_id") UUID runBId,
            @JsonProperty("status") String status,
            @JsonProperty("human_verdict") String humanVerdict,
            @JsonProperty("review_comment") String reviewComment,
            @JsonProperty("line_comments_json") String lineCommentsJson,
            @JsonProperty("decision_scores_json") String decisionScoresJson,
            @JsonProperty("diff_a") String diffA,
            @JsonProperty("diff_b") String diffB,
            @JsonProperty("diff_of_diffs") String diffOfDiffs,
            @JsonProperty("created_by") String createdBy,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("completed_at") Instant completedAt,
            @JsonProperty("resource_version") long resourceVersion
    ) {
        static RunResponse from(BenchmarkRunEntity e) {
            return new RunResponse(
                    e.getId(),
                    e.getCaseId(),
                    e.getArtifactType() != null ? e.getArtifactType().name() : null,
                    e.getArtifactId(),
                    e.getArtifactVersionId(),
                    e.getArtifactBType() != null ? e.getArtifactBType().name() : null,
                    e.getArtifactBId(),
                    e.getArtifactBVersionId(),
                    e.getCodingAgent(),
                    e.getRunAId(),
                    e.getRunBId(),
                    e.getStatus() != null ? e.getStatus().name() : null,
                    e.getHumanVerdict() != null ? e.getHumanVerdict().name() : null,
                    e.getReviewComment(),
                    e.getLineCommentsJson(),
                    e.getDecisionScoresJson(),
                    e.getDiffA(),
                    e.getDiffB(),
                    e.getDiffOfDiffs(),
                    e.getCreatedBy(),
                    e.getCreatedAt(),
                    e.getCompletedAt(),
                    e.getResourceVersion()
            );
        }
    }
}

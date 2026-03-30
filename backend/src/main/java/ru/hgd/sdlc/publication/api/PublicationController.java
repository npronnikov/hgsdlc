package ru.hgd.sdlc.publication.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.publication.application.PublicationService;
import ru.hgd.sdlc.publication.domain.PublicationJob;
import ru.hgd.sdlc.publication.domain.PublicationRequest;
import ru.hgd.sdlc.rule.api.RuleResponse;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.flow.api.FlowResponse;
import ru.hgd.sdlc.flow.application.FlowYamlParser;
import ru.hgd.sdlc.flow.domain.FlowVersion;
import ru.hgd.sdlc.skill.api.SkillResponse;
import ru.hgd.sdlc.skill.domain.SkillVersion;

@RestController
@RequestMapping("/api/publications")
public class PublicationController {
    private final PublicationService publicationService;
    private final FlowYamlParser flowYamlParser;

    public PublicationController(PublicationService publicationService, FlowYamlParser flowYamlParser) {
        this.publicationService = publicationService;
        this.flowYamlParser = flowYamlParser;
    }

    @GetMapping("/requests")
    public List<PublicationRequestResponse> requests(@RequestParam(required = false) String status) {
        return publicationService.listRequests(status).stream().map(PublicationRequestResponse::from).toList();
    }

    @GetMapping("/jobs")
    public List<PublicationJobResponse> jobs(@RequestParam(required = false) String status) {
        return publicationService.listJobs(status).stream().map(PublicationJobResponse::from).toList();
    }

    @GetMapping("/requests/{requestId}/jobs")
    public List<PublicationJobResponse> jobsByRequest(@PathVariable UUID requestId) {
        return publicationService.jobsByRequest(requestId).stream().map(PublicationJobResponse::from).toList();
    }

    @PostMapping("/skills/{skillId}/versions/{version}/approve")
    public SkillResponse approveSkill(
            @PathVariable String skillId,
            @PathVariable String version,
            @AuthenticationPrincipal User user
    ) {
        SkillVersion updated = publicationService.approveSkillPublication(skillId, version, user);
        return SkillResponse.from(updated);
    }

    @PostMapping("/skills/{skillId}/versions/{version}/reject")
    public SkillResponse rejectSkill(
            @PathVariable String skillId,
            @PathVariable String version,
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) RejectRequest request
    ) {
        SkillVersion updated = publicationService.rejectSkillPublication(skillId, version, user, request == null ? null : request.reason());
        return SkillResponse.from(updated);
    }

    @PostMapping("/skills/{skillId}/versions/{version}/retry")
    public SkillResponse retrySkill(
            @PathVariable String skillId,
            @PathVariable String version,
            @AuthenticationPrincipal User user
    ) {
        SkillVersion updated = publicationService.retrySkillPublication(skillId, version, user);
        return SkillResponse.from(updated);
    }

    @PostMapping("/rules/{ruleId}/versions/{version}/approve")
    public RuleResponse approveRule(
            @PathVariable String ruleId,
            @PathVariable String version,
            @AuthenticationPrincipal User user
    ) {
        RuleVersion updated = publicationService.approveRulePublication(ruleId, version, user);
        return RuleResponse.from(updated);
    }

    @PostMapping("/rules/{ruleId}/versions/{version}/reject")
    public RuleResponse rejectRule(
            @PathVariable String ruleId,
            @PathVariable String version,
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) RejectRequest request
    ) {
        RuleVersion updated = publicationService.rejectRulePublication(ruleId, version, user, request == null ? null : request.reason());
        return RuleResponse.from(updated);
    }

    @PostMapping("/rules/{ruleId}/versions/{version}/retry")
    public RuleResponse retryRule(
            @PathVariable String ruleId,
            @PathVariable String version,
            @AuthenticationPrincipal User user
    ) {
        RuleVersion updated = publicationService.retryRulePublication(ruleId, version, user);
        return RuleResponse.from(updated);
    }

    @PostMapping("/flows/{flowId}/versions/{version}/approve")
    public FlowResponse approveFlow(
            @PathVariable String flowId,
            @PathVariable String version,
            @AuthenticationPrincipal User user
    ) {
        FlowVersion updated = publicationService.approveFlowPublication(flowId, version, user);
        return FlowResponse.from(updated, flowYamlParser.parse(updated.getFlowYaml()));
    }

    @PostMapping("/flows/{flowId}/versions/{version}/reject")
    public FlowResponse rejectFlow(
            @PathVariable String flowId,
            @PathVariable String version,
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) RejectRequest request
    ) {
        FlowVersion updated = publicationService.rejectFlowPublication(flowId, version, user, request == null ? null : request.reason());
        return FlowResponse.from(updated, flowYamlParser.parse(updated.getFlowYaml()));
    }

    @PostMapping("/flows/{flowId}/versions/{version}/retry")
    public FlowResponse retryFlow(
            @PathVariable String flowId,
            @PathVariable String version,
            @AuthenticationPrincipal User user
    ) {
        FlowVersion updated = publicationService.retryFlowPublication(flowId, version, user);
        return FlowResponse.from(updated, flowYamlParser.parse(updated.getFlowYaml()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<String> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    public record RejectRequest(
            @JsonProperty("reason") String reason
    ) {}

    public record PublicationRequestResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("entity_type") String entityType,
            @JsonProperty("entity_id") String entityId,
            @JsonProperty("version") String version,
            @JsonProperty("canonical_name") String canonicalName,
            @JsonProperty("author") String author,
            @JsonProperty("requested_mode") String requestedMode,
            @JsonProperty("status") String status,
            @JsonProperty("approval_count") int approvalCount,
            @JsonProperty("required_approvals") int requiredApprovals,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("last_error") String lastError
    ) {
        static PublicationRequestResponse from(PublicationRequest request) {
            return new PublicationRequestResponse(
                    request.getId(),
                    request.getEntityType() == null ? null : request.getEntityType().name().toLowerCase(),
                    request.getEntityId(),
                    request.getVersion(),
                    request.getCanonicalName(),
                    request.getAuthor(),
                    request.getRequestedMode(),
                    request.getStatus() == null ? null : request.getStatus().name().toLowerCase(),
                    request.getApprovalCount(),
                    request.getRequiredApprovals(),
                    request.getCreatedAt(),
                    request.getUpdatedAt(),
                    request.getLastError()
            );
        }
    }

    public record PublicationJobResponse(
            @JsonProperty("id") UUID id,
            @JsonProperty("request_id") UUID requestId,
            @JsonProperty("entity_type") String entityType,
            @JsonProperty("entity_id") String entityId,
            @JsonProperty("version") String version,
            @JsonProperty("status") String status,
            @JsonProperty("step") String step,
            @JsonProperty("attempt_no") int attemptNo,
            @JsonProperty("branch_name") String branchName,
            @JsonProperty("pr_url") String prUrl,
            @JsonProperty("pr_number") Integer prNumber,
            @JsonProperty("commit_sha") String commitSha,
            @JsonProperty("error") String error,
            @JsonProperty("started_at") Instant startedAt,
            @JsonProperty("finished_at") Instant finishedAt,
            @JsonProperty("created_at") Instant createdAt
    ) {
        static PublicationJobResponse from(PublicationJob job) {
            return new PublicationJobResponse(
                    job.getId(),
                    job.getRequestId(),
                    job.getEntityType() == null ? null : job.getEntityType().name().toLowerCase(),
                    job.getEntityId(),
                    job.getVersion(),
                    job.getStatus() == null ? null : job.getStatus().name().toLowerCase(),
                    job.getStep(),
                    job.getAttemptNo(),
                    job.getBranchName(),
                    job.getPrUrl(),
                    job.getPrNumber(),
                    job.getCommitSha(),
                    job.getError(),
                    job.getStartedAt(),
                    job.getFinishedAt(),
                    job.getCreatedAt()
            );
        }
    }
}

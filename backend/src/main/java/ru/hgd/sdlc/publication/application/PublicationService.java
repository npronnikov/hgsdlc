package ru.hgd.sdlc.publication.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.publication.domain.PublicationApproval;
import ru.hgd.sdlc.publication.domain.PublicationDecision;
import ru.hgd.sdlc.publication.domain.PublicationEntityType;
import ru.hgd.sdlc.publication.domain.PublicationJob;
import ru.hgd.sdlc.publication.domain.PublicationJobStatus;
import ru.hgd.sdlc.publication.domain.PublicationRequest;
import ru.hgd.sdlc.publication.domain.PublicationStatus;
import ru.hgd.sdlc.publication.domain.PublicationTarget;
import ru.hgd.sdlc.rule.domain.RuleContentSource;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;
import ru.hgd.sdlc.publication.infrastructure.PublicationApprovalRepository;
import ru.hgd.sdlc.publication.infrastructure.PublicationJobRepository;
import ru.hgd.sdlc.publication.infrastructure.PublicationRequestRepository;
import ru.hgd.sdlc.settings.application.SettingsService;
import ru.hgd.sdlc.settings.domain.SystemSetting;
import ru.hgd.sdlc.settings.infrastructure.SystemSettingRepository;
import ru.hgd.sdlc.flow.domain.FlowContentSource;
import ru.hgd.sdlc.flow.domain.FlowVersion;
import ru.hgd.sdlc.flow.infrastructure.FlowVersionRepository;
import ru.hgd.sdlc.skill.domain.SkillContentSource;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

@Service
public class PublicationService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TEAM_SCOPE = "team";
    private static final String ORGANIZATION_SCOPE = "organization";

    private final PublicationRequestRepository publicationRequestRepository;
    private final PublicationApprovalRepository publicationApprovalRepository;
    private final PublicationJobRepository publicationJobRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final SystemSettingRepository systemSettingRepository;

    public PublicationService(
            PublicationRequestRepository publicationRequestRepository,
            PublicationApprovalRepository publicationApprovalRepository,
            PublicationJobRepository publicationJobRepository,
            SkillVersionRepository skillVersionRepository,
            RuleVersionRepository ruleVersionRepository,
            FlowVersionRepository flowVersionRepository,
            SystemSettingRepository systemSettingRepository
    ) {
        this.publicationRequestRepository = publicationRequestRepository;
        this.publicationApprovalRepository = publicationApprovalRepository;
        this.publicationJobRepository = publicationJobRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.ruleVersionRepository = ruleVersionRepository;
        this.flowVersionRepository = flowVersionRepository;
        this.systemSettingRepository = systemSettingRepository;
    }

    @Transactional
    public void upsertSkillRequest(SkillVersion skill, String actorId, PublicationTarget target, String requestedMode) {
        Instant now = Instant.now();
        PublicationRequest request = publicationRequestRepository
                .findByEntityTypeAndEntityIdAndVersion(PublicationEntityType.SKILL, skill.getSkillId(), skill.getVersion())
                .orElseGet(() -> PublicationRequest.builder()
                        .id(UUID.randomUUID())
                        .entityType(PublicationEntityType.SKILL)
                        .entityId(skill.getSkillId())
                        .version(skill.getVersion())
                        .canonicalName(skill.getCanonicalName())
                        .author(actorId)
                        .approvalCount(0)
                        .requiredApprovals(1)
                        .createdAt(now)
                        .build());
        request.setCanonicalName(skill.getCanonicalName());
        request.setRequestedTarget(normalizeTargetForScope(target, skill.getScope()));
        request.setRequestedMode(normalizePublishModeForScope(requestedMode, skill.getScope()));
        request.setStatus(PublicationStatus.PENDING_APPROVAL);
        request.setLastError(null);
        request.setUpdatedAt(now);
        publicationRequestRepository.save(request);
    }

    @Transactional
    public void upsertRuleRequest(RuleVersion rule, String actorId, PublicationTarget target, String requestedMode) {
        Instant now = Instant.now();
        PublicationRequest request = publicationRequestRepository
                .findByEntityTypeAndEntityIdAndVersion(PublicationEntityType.RULE, rule.getRuleId(), rule.getVersion())
                .orElseGet(() -> PublicationRequest.builder()
                        .id(UUID.randomUUID())
                        .entityType(PublicationEntityType.RULE)
                        .entityId(rule.getRuleId())
                        .version(rule.getVersion())
                        .canonicalName(rule.getCanonicalName())
                        .author(actorId)
                        .approvalCount(0)
                        .requiredApprovals(1)
                        .createdAt(now)
                        .build());
        request.setCanonicalName(rule.getCanonicalName());
        request.setRequestedTarget(normalizeTargetForScope(target, rule.getScope()));
        request.setRequestedMode(normalizePublishModeForScope(requestedMode, rule.getScope()));
        request.setStatus(PublicationStatus.PENDING_APPROVAL);
        request.setLastError(null);
        request.setUpdatedAt(now);
        publicationRequestRepository.save(request);
    }

    @Transactional
    public void upsertFlowRequest(FlowVersion flow, String actorId, PublicationTarget target, String requestedMode) {
        Instant now = Instant.now();
        PublicationRequest request = publicationRequestRepository
                .findByEntityTypeAndEntityIdAndVersion(PublicationEntityType.FLOW, flow.getFlowId(), flow.getVersion())
                .orElseGet(() -> PublicationRequest.builder()
                        .id(UUID.randomUUID())
                        .entityType(PublicationEntityType.FLOW)
                        .entityId(flow.getFlowId())
                        .version(flow.getVersion())
                        .canonicalName(flow.getCanonicalName())
                        .author(actorId)
                        .approvalCount(0)
                        .requiredApprovals(1)
                        .createdAt(now)
                        .build());
        request.setCanonicalName(flow.getCanonicalName());
        request.setRequestedTarget(normalizeTargetForScope(target, flow.getScope()));
        request.setRequestedMode(normalizePublishModeForScope(requestedMode, flow.getScope()));
        request.setStatus(PublicationStatus.PENDING_APPROVAL);
        request.setLastError(null);
        request.setUpdatedAt(now);
        publicationRequestRepository.save(request);
    }

    @Transactional
    public SkillVersion approveSkillPublication(String skillId, String version, User approver) {
        PublicationRequest request = publicationRequestRepository
                .findByEntityTypeAndEntityIdAndVersion(PublicationEntityType.SKILL, skillId, version)
                .orElseThrow(() -> new NotFoundException("Publication request not found for skill: " + skillId + "@" + version));
        SkillVersion skill = skillVersionRepository
                .findFirstBySkillIdAndVersionOrderBySavedAtDesc(skillId, version)
                .orElseThrow(() -> new NotFoundException("Skill version not found: " + skillId + "@" + version));

        requireApprover(approver);
        String approverId = approver.getUsername();
        if (approverId != null && approverId.equalsIgnoreCase(request.getAuthor())) {
            throw new ValidationException("Self-approval is not allowed");
        }
        if (request.getStatus() != PublicationStatus.PENDING_APPROVAL) {
            throw new ValidationException("Publication request is not pending approval");
        }

        Instant now = Instant.now();
        request.setStatus(PublicationStatus.APPROVED);
        request.setApprovalCount(request.getApprovalCount() + 1);
        request.setUpdatedAt(now);
        request.setLastError(null);
        publicationRequestRepository.save(request);

        publicationApprovalRepository.save(PublicationApproval.builder()
                .id(UUID.randomUUID())
                .requestId(request.getId())
                .approver(approverId)
                .decision(PublicationDecision.APPROVE)
                .comment("approved")
                .createdAt(now)
                .build());

        return runPublishJob(skill, request, approverId);
    }

    @Transactional
    public SkillVersion rejectSkillPublication(String skillId, String version, User approver, String reason) {
        requireApprover(approver);
        PublicationRequest request = publicationRequestRepository
                .findByEntityTypeAndEntityIdAndVersion(PublicationEntityType.SKILL, skillId, version)
                .orElseThrow(() -> new NotFoundException("Publication request not found for skill: " + skillId + "@" + version));
        SkillVersion skill = skillVersionRepository
                .findFirstBySkillIdAndVersionOrderBySavedAtDesc(skillId, version)
                .orElseThrow(() -> new NotFoundException("Skill version not found: " + skillId + "@" + version));

        request.setStatus(PublicationStatus.REJECTED);
        request.setUpdatedAt(Instant.now());
        request.setLastError(reason);
        publicationRequestRepository.save(request);

        publicationApprovalRepository.save(PublicationApproval.builder()
                .id(UUID.randomUUID())
                .requestId(request.getId())
                .approver(approver.getUsername())
                .decision(PublicationDecision.REJECT)
                .comment(reason)
                .createdAt(Instant.now())
                .build());

        skill.setPublicationStatus(PublicationStatus.REJECTED);
        skill.setLastPublishError(reason);
        skill.setSavedAt(Instant.now());
        skill.setSavedBy(approver.getUsername());
        return skillVersionRepository.save(skill);
    }

    @Transactional
    public SkillVersion retrySkillPublication(String skillId, String version, User user) {
        requireApprover(user);
        PublicationRequest request = publicationRequestRepository
                .findByEntityTypeAndEntityIdAndVersion(PublicationEntityType.SKILL, skillId, version)
                .orElseThrow(() -> new NotFoundException("Publication request not found for skill: " + skillId + "@" + version));
        SkillVersion skill = skillVersionRepository
                .findFirstBySkillIdAndVersionOrderBySavedAtDesc(skillId, version)
                .orElseThrow(() -> new NotFoundException("Skill version not found: " + skillId + "@" + version));

        if (request.getStatus() != PublicationStatus.FAILED && request.getStatus() != PublicationStatus.APPROVED) {
            throw new ValidationException("Retry is allowed only for failed or approved requests");
        }
        request.setStatus(PublicationStatus.APPROVED);
        request.setUpdatedAt(Instant.now());
        request.setLastError(null);
        publicationRequestRepository.save(request);
        return runPublishJob(skill, request, user.getUsername());
    }

    @Transactional
    public RuleVersion approveRulePublication(String ruleId, String version, User approver) {
        PublicationRequest request = publicationRequestRepository
                .findByEntityTypeAndEntityIdAndVersion(PublicationEntityType.RULE, ruleId, version)
                .orElseThrow(() -> new NotFoundException("Publication request not found for rule: " + ruleId + "@" + version));
        RuleVersion rule = ruleVersionRepository
                .findFirstByRuleIdAndVersionOrderBySavedAtDesc(ruleId, version)
                .orElseThrow(() -> new NotFoundException("Rule version not found: " + ruleId + "@" + version));
        requireApprover(approver);
        String approverId = approver.getUsername();
        if (approverId != null && approverId.equalsIgnoreCase(request.getAuthor())) {
            throw new ValidationException("Self-approval is not allowed");
        }
        if (request.getStatus() != PublicationStatus.PENDING_APPROVAL) {
            throw new ValidationException("Publication request is not pending approval");
        }

        Instant now = Instant.now();
        request.setStatus(PublicationStatus.APPROVED);
        request.setApprovalCount(request.getApprovalCount() + 1);
        request.setUpdatedAt(now);
        request.setLastError(null);
        publicationRequestRepository.save(request);

        publicationApprovalRepository.save(PublicationApproval.builder()
                .id(UUID.randomUUID())
                .requestId(request.getId())
                .approver(approverId)
                .decision(PublicationDecision.APPROVE)
                .comment("approved")
                .createdAt(now)
                .build());

        return runPublishRuleJob(rule, request, approverId);
    }

    @Transactional
    public RuleVersion rejectRulePublication(String ruleId, String version, User approver, String reason) {
        requireApprover(approver);
        PublicationRequest request = publicationRequestRepository
                .findByEntityTypeAndEntityIdAndVersion(PublicationEntityType.RULE, ruleId, version)
                .orElseThrow(() -> new NotFoundException("Publication request not found for rule: " + ruleId + "@" + version));
        RuleVersion rule = ruleVersionRepository
                .findFirstByRuleIdAndVersionOrderBySavedAtDesc(ruleId, version)
                .orElseThrow(() -> new NotFoundException("Rule version not found: " + ruleId + "@" + version));
        request.setStatus(PublicationStatus.REJECTED);
        request.setUpdatedAt(Instant.now());
        request.setLastError(reason);
        publicationRequestRepository.save(request);

        publicationApprovalRepository.save(PublicationApproval.builder()
                .id(UUID.randomUUID())
                .requestId(request.getId())
                .approver(approver.getUsername())
                .decision(PublicationDecision.REJECT)
                .comment(reason)
                .createdAt(Instant.now())
                .build());

        rule.setPublicationStatus(PublicationStatus.REJECTED);
        rule.setLastPublishError(reason);
        rule.setSavedAt(Instant.now());
        rule.setSavedBy(approver.getUsername());
        return ruleVersionRepository.save(rule);
    }

    @Transactional
    public RuleVersion retryRulePublication(String ruleId, String version, User user) {
        requireApprover(user);
        PublicationRequest request = publicationRequestRepository
                .findByEntityTypeAndEntityIdAndVersion(PublicationEntityType.RULE, ruleId, version)
                .orElseThrow(() -> new NotFoundException("Publication request not found for rule: " + ruleId + "@" + version));
        RuleVersion rule = ruleVersionRepository
                .findFirstByRuleIdAndVersionOrderBySavedAtDesc(ruleId, version)
                .orElseThrow(() -> new NotFoundException("Rule version not found: " + ruleId + "@" + version));
        if (request.getStatus() != PublicationStatus.FAILED && request.getStatus() != PublicationStatus.APPROVED) {
            throw new ValidationException("Retry is allowed only for failed or approved requests");
        }
        request.setStatus(PublicationStatus.APPROVED);
        request.setUpdatedAt(Instant.now());
        request.setLastError(null);
        publicationRequestRepository.save(request);
        return runPublishRuleJob(rule, request, user.getUsername());
    }

    @Transactional
    public FlowVersion approveFlowPublication(String flowId, String version, User approver) {
        PublicationRequest request = publicationRequestRepository
                .findByEntityTypeAndEntityIdAndVersion(PublicationEntityType.FLOW, flowId, version)
                .orElseThrow(() -> new NotFoundException("Publication request not found for flow: " + flowId + "@" + version));
        FlowVersion flow = flowVersionRepository
                .findFirstByFlowIdAndVersionOrderBySavedAtDesc(flowId, version)
                .orElseThrow(() -> new NotFoundException("Flow version not found: " + flowId + "@" + version));
        requireApprover(approver);
        String approverId = approver.getUsername();
        if (approverId != null && approverId.equalsIgnoreCase(request.getAuthor())) {
            throw new ValidationException("Self-approval is not allowed");
        }
        if (request.getStatus() != PublicationStatus.PENDING_APPROVAL) {
            throw new ValidationException("Publication request is not pending approval");
        }

        Instant now = Instant.now();
        request.setStatus(PublicationStatus.APPROVED);
        request.setApprovalCount(request.getApprovalCount() + 1);
        request.setUpdatedAt(now);
        request.setLastError(null);
        publicationRequestRepository.save(request);

        publicationApprovalRepository.save(PublicationApproval.builder()
                .id(UUID.randomUUID())
                .requestId(request.getId())
                .approver(approverId)
                .decision(PublicationDecision.APPROVE)
                .comment("approved")
                .createdAt(now)
                .build());

        return runPublishFlowJob(flow, request, approverId);
    }

    @Transactional
    public FlowVersion rejectFlowPublication(String flowId, String version, User approver, String reason) {
        requireApprover(approver);
        PublicationRequest request = publicationRequestRepository
                .findByEntityTypeAndEntityIdAndVersion(PublicationEntityType.FLOW, flowId, version)
                .orElseThrow(() -> new NotFoundException("Publication request not found for flow: " + flowId + "@" + version));
        FlowVersion flow = flowVersionRepository
                .findFirstByFlowIdAndVersionOrderBySavedAtDesc(flowId, version)
                .orElseThrow(() -> new NotFoundException("Flow version not found: " + flowId + "@" + version));
        request.setStatus(PublicationStatus.REJECTED);
        request.setUpdatedAt(Instant.now());
        request.setLastError(reason);
        publicationRequestRepository.save(request);

        publicationApprovalRepository.save(PublicationApproval.builder()
                .id(UUID.randomUUID())
                .requestId(request.getId())
                .approver(approver.getUsername())
                .decision(PublicationDecision.REJECT)
                .comment(reason)
                .createdAt(Instant.now())
                .build());

        flow.setPublicationStatus(PublicationStatus.REJECTED);
        flow.setLastPublishError(reason);
        flow.setSavedAt(Instant.now());
        flow.setSavedBy(approver.getUsername());
        return flowVersionRepository.save(flow);
    }

    @Transactional
    public FlowVersion retryFlowPublication(String flowId, String version, User user) {
        requireApprover(user);
        PublicationRequest request = publicationRequestRepository
                .findByEntityTypeAndEntityIdAndVersion(PublicationEntityType.FLOW, flowId, version)
                .orElseThrow(() -> new NotFoundException("Publication request not found for flow: " + flowId + "@" + version));
        FlowVersion flow = flowVersionRepository
                .findFirstByFlowIdAndVersionOrderBySavedAtDesc(flowId, version)
                .orElseThrow(() -> new NotFoundException("Flow version not found: " + flowId + "@" + version));
        if (request.getStatus() != PublicationStatus.FAILED && request.getStatus() != PublicationStatus.APPROVED) {
            throw new ValidationException("Retry is allowed only for failed or approved requests");
        }
        request.setStatus(PublicationStatus.APPROVED);
        request.setUpdatedAt(Instant.now());
        request.setLastError(null);
        publicationRequestRepository.save(request);
        return runPublishFlowJob(flow, request, user.getUsername());
    }

    @Transactional(readOnly = true)
    public List<PublicationRequest> listRequests(String statusRaw) {
        if (statusRaw == null || statusRaw.isBlank()) {
            return publicationRequestRepository.findAllByOrderByCreatedAtDesc();
        }
        PublicationStatus status = PublicationStatus.valueOf(statusRaw.trim().toUpperCase(Locale.ROOT));
        return publicationRequestRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public List<PublicationJob> listJobs(String statusRaw) {
        if (statusRaw == null || statusRaw.isBlank()) {
            return publicationJobRepository.findAllByOrderByCreatedAtDesc();
        }
        PublicationJobStatus status = PublicationJobStatus.valueOf(statusRaw.trim().toUpperCase(Locale.ROOT));
        return publicationJobRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public List<PublicationJob> jobsByRequest(UUID requestId) {
        return publicationJobRepository.findByRequestIdOrderByCreatedAtDesc(requestId);
    }

    private SkillVersion runPublishJob(SkillVersion skill, PublicationRequest request, String actor) {
        Instant now = Instant.now();
        int attempt = publicationJobRepository.findByRequestIdOrderByCreatedAtDesc(request.getId()).size() + 1;
        PublicationJob job = PublicationJob.builder()
                .id(UUID.randomUUID())
                .requestId(request.getId())
                .entityType(PublicationEntityType.SKILL)
                .entityId(skill.getSkillId())
                .version(skill.getVersion())
                .status(PublicationJobStatus.RUNNING)
                .step("prepare")
                .attemptNo(attempt)
                .startedAt(now)
                .createdAt(now)
                .build();
        publicationJobRepository.save(job);

        skill.setPublicationTarget(request.getRequestedTarget());
        skill.setPublicationStatus(PublicationStatus.PUBLISHING);
        skill.setLastPublishError(null);
        skill.setSavedAt(now);
        skill.setSavedBy(actor);
        skillVersionRepository.save(skill);

        try {
            PublishResult publishResult = publish(skill, request, job);
            job.setStatus(PublicationJobStatus.COMPLETED);
            job.setStep("completed");
            job.setCommitSha(publishResult.commitSha());
            job.setPrUrl(publishResult.prUrl());
            job.setPrNumber(publishResult.prNumber());
            job.setFinishedAt(Instant.now());
            publicationJobRepository.save(job);

            request.setStatus(publishResult.awaitingMerge() ? PublicationStatus.PUBLISHING : PublicationStatus.PUBLISHED);
            request.setUpdatedAt(Instant.now());
            request.setLastError(null);
            publicationRequestRepository.save(request);

            skill.setPublishedCommitSha(publishResult.commitSha());
            skill.setPublishedPrUrl(publishResult.prUrl());
            skill.setLastPublishError(null);
            skill.setContentSource(SkillContentSource.GIT);
            skill.setSourcePath("skills/" + skill.getSkillId() + "/" + skill.getVersion());
            skill.setSourceRef(publishResult.sourceRef());
            if (!publishResult.awaitingMerge()) {
                skill.setPublicationStatus(PublicationStatus.PUBLISHED);
            }
            return skillVersionRepository.save(skill);
        } catch (Exception ex) {
            String error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            job.setStatus(PublicationJobStatus.FAILED);
            job.setStep("failed");
            job.setError(error);
            job.setFinishedAt(Instant.now());
            publicationJobRepository.save(job);

            request.setStatus(PublicationStatus.FAILED);
            request.setUpdatedAt(Instant.now());
            request.setLastError(error);
            publicationRequestRepository.save(request);

            skill.setPublicationStatus(PublicationStatus.FAILED);
            skill.setLastPublishError(error);
            skillVersionRepository.save(skill);
            throw new ValidationException("Publish failed: " + error);
        }
    }

    private RuleVersion runPublishRuleJob(RuleVersion rule, PublicationRequest request, String actor) {
        Instant now = Instant.now();
        int attempt = publicationJobRepository.findByRequestIdOrderByCreatedAtDesc(request.getId()).size() + 1;
        PublicationJob job = PublicationJob.builder()
                .id(UUID.randomUUID())
                .requestId(request.getId())
                .entityType(PublicationEntityType.RULE)
                .entityId(rule.getRuleId())
                .version(rule.getVersion())
                .status(PublicationJobStatus.RUNNING)
                .step("prepare")
                .attemptNo(attempt)
                .startedAt(now)
                .createdAt(now)
                .build();
        publicationJobRepository.save(job);

        rule.setPublicationTarget(request.getRequestedTarget());
        rule.setPublicationStatus(PublicationStatus.PUBLISHING);
        rule.setLastPublishError(null);
        rule.setSavedAt(now);
        rule.setSavedBy(actor);
        ruleVersionRepository.save(rule);

        try {
            PublishResult publishResult = publishRule(rule, request, job);
            job.setStatus(PublicationJobStatus.COMPLETED);
            job.setStep("completed");
            job.setCommitSha(publishResult.commitSha());
            job.setPrUrl(publishResult.prUrl());
            job.setPrNumber(publishResult.prNumber());
            job.setFinishedAt(Instant.now());
            publicationJobRepository.save(job);

            request.setStatus(publishResult.awaitingMerge() ? PublicationStatus.PUBLISHING : PublicationStatus.PUBLISHED);
            request.setUpdatedAt(Instant.now());
            request.setLastError(null);
            publicationRequestRepository.save(request);

            rule.setPublishedCommitSha(publishResult.commitSha());
            rule.setPublishedPrUrl(publishResult.prUrl());
            rule.setLastPublishError(null);
            rule.setContentSource(RuleContentSource.GIT);
            rule.setSourcePath("rules/" + rule.getRuleId() + "/" + rule.getVersion());
            rule.setSourceRef(publishResult.sourceRef());
            if (!publishResult.awaitingMerge()) {
                rule.setPublicationStatus(PublicationStatus.PUBLISHED);
            }
            return ruleVersionRepository.save(rule);
        } catch (Exception ex) {
            String error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            job.setStatus(PublicationJobStatus.FAILED);
            job.setStep("failed");
            job.setError(error);
            job.setFinishedAt(Instant.now());
            publicationJobRepository.save(job);

            request.setStatus(PublicationStatus.FAILED);
            request.setUpdatedAt(Instant.now());
            request.setLastError(error);
            publicationRequestRepository.save(request);

            rule.setPublicationStatus(PublicationStatus.FAILED);
            rule.setLastPublishError(error);
            ruleVersionRepository.save(rule);
            throw new ValidationException("Publish failed: " + error);
        }
    }

    private FlowVersion runPublishFlowJob(FlowVersion flow, PublicationRequest request, String actor) {
        Instant now = Instant.now();
        int attempt = publicationJobRepository.findByRequestIdOrderByCreatedAtDesc(request.getId()).size() + 1;
        PublicationJob job = PublicationJob.builder()
                .id(UUID.randomUUID())
                .requestId(request.getId())
                .entityType(PublicationEntityType.FLOW)
                .entityId(flow.getFlowId())
                .version(flow.getVersion())
                .status(PublicationJobStatus.RUNNING)
                .step("prepare")
                .attemptNo(attempt)
                .startedAt(now)
                .createdAt(now)
                .build();
        publicationJobRepository.save(job);

        flow.setPublicationTarget(request.getRequestedTarget());
        flow.setPublicationStatus(PublicationStatus.PUBLISHING);
        flow.setLastPublishError(null);
        flow.setSavedAt(now);
        flow.setSavedBy(actor);
        flowVersionRepository.save(flow);

        try {
            PublishResult publishResult = publishFlow(flow, request, job);
            job.setStatus(PublicationJobStatus.COMPLETED);
            job.setStep("completed");
            job.setCommitSha(publishResult.commitSha());
            job.setPrUrl(publishResult.prUrl());
            job.setPrNumber(publishResult.prNumber());
            job.setFinishedAt(Instant.now());
            publicationJobRepository.save(job);

            request.setStatus(publishResult.awaitingMerge() ? PublicationStatus.PUBLISHING : PublicationStatus.PUBLISHED);
            request.setUpdatedAt(Instant.now());
            request.setLastError(null);
            publicationRequestRepository.save(request);

            flow.setPublishedCommitSha(publishResult.commitSha());
            flow.setPublishedPrUrl(publishResult.prUrl());
            flow.setLastPublishError(null);
            flow.setContentSource(FlowContentSource.GIT);
            flow.setSourcePath("flows/" + flow.getFlowId() + "/" + flow.getVersion());
            flow.setSourceRef(publishResult.sourceRef());
            if (!publishResult.awaitingMerge()) {
                flow.setPublicationStatus(PublicationStatus.PUBLISHED);
            }
            return flowVersionRepository.save(flow);
        } catch (Exception ex) {
            String error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            job.setStatus(PublicationJobStatus.FAILED);
            job.setStep("failed");
            job.setError(error);
            job.setFinishedAt(Instant.now());
            publicationJobRepository.save(job);

            request.setStatus(PublicationStatus.FAILED);
            request.setUpdatedAt(Instant.now());
            request.setLastError(error);
            publicationRequestRepository.save(request);

            flow.setPublicationStatus(PublicationStatus.FAILED);
            flow.setLastPublishError(error);
            flowVersionRepository.save(flow);
            throw new ValidationException("Publish failed: " + error);
        }
    }

    private PublishResult publish(SkillVersion skill, PublicationRequest request, PublicationJob job) throws IOException, InterruptedException {
        CatalogGitSettings settings = loadCatalogGitSettings();
        String sourceDir = "skills/" + skill.getSkillId() + "/" + skill.getVersion();
        if (isTeamScope(skill.getScope())) {
            Path mirrorRepoPath = resolveRuntimeMirrorPath(settings.workspaceRoot(), settings.repoUrl());
            syncMirror(settings.repoUrl(), settings.defaultBranch(), mirrorRepoPath);
            checkoutOrCreateBranch(mirrorRepoPath, settings.defaultBranch());
            configureGitIdentity(mirrorRepoPath);
            job.setStep("git_prepare");
            job.setBranchName(settings.defaultBranch());
            publicationJobRepository.save(job);

            writeSkillFiles(mirrorRepoPath, sourceDir, skill, settings.defaultBranch());
            job.setStep("git_commit");
            publicationJobRepository.save(job);
            runGit(List.of("git", "-C", mirrorRepoPath.toString(), "add", sourceDir));
            runGit(List.of("git", "-C", mirrorRepoPath.toString(), "commit", "-m", "publish(skill-team): " + skill.getCanonicalName()));
            String commitSha = runGitWithOutput(List.of("git", "-C", mirrorRepoPath.toString(), "rev-parse", "HEAD")).trim();
            return new PublishResult(commitSha, null, null, commitSha, false);
        }

        Path repoPath = resolvePublishRepoPath(settings.workspaceRoot(), settings.repoUrl());
        syncMirror(settings.repoUrl(), settings.defaultBranch(), repoPath);

        String branchName;
        String mode = normalizePublishMode(request.getRequestedMode());
        if ("pr".equals(mode)) {
            branchName = "publish/skill/" + skill.getSkillId() + "/" + skill.getVersion().replace('.', '-') + "/" + Instant.now().toEpochMilli();
        } else {
            branchName = settings.defaultBranch();
        }
        job.setStep("git_prepare");
        job.setBranchName(branchName);
        publicationJobRepository.save(job);

        runGit(List.of("git", "-C", repoPath.toString(), "checkout", "-B", settings.defaultBranch(), "origin/" + settings.defaultBranch()));
        if (!branchName.equals(settings.defaultBranch())) {
            runGit(List.of("git", "-C", repoPath.toString(), "checkout", "-B", branchName));
        }
        configureGitIdentity(repoPath);

        writeSkillFiles(repoPath, sourceDir, skill, settings.defaultBranch());

        job.setStep("git_commit");
        publicationJobRepository.save(job);
        runGit(List.of("git", "-C", repoPath.toString(), "add", sourceDir));
        runGit(List.of("git", "-C", repoPath.toString(), "commit", "-m", "publish(skill): " + skill.getCanonicalName()));
        String commitSha = runGitWithOutput(List.of("git", "-C", repoPath.toString(), "rev-parse", "HEAD")).trim();

        String pushUrl = authenticatedRepoUrl(settings.repoUrl(), settings.gitUsername(), settings.gitPasswordOrPat());
        runGit(List.of("git", "-C", repoPath.toString(), "push", pushUrl, branchName));

        if ("pr".equals(mode)) {
            job.setStep("create_pr");
            publicationJobRepository.save(job);
            PrResult prResult = createPullRequest(settings, branchName, skill.getCanonicalName());
            return new PublishResult(commitSha, prResult.url(), prResult.number(), branchName, true);
        }
        syncRuntimeMirrorContent(settings.workspaceRoot(), settings.repoUrl(), repoPath, sourceDir);
        return new PublishResult(commitSha, null, null, commitSha, false);
    }

    private PublishResult publishRule(RuleVersion rule, PublicationRequest request, PublicationJob job) throws IOException, InterruptedException {
        CatalogGitSettings settings = loadCatalogGitSettings();
        String sourceDir = "rules/" + rule.getRuleId() + "/" + rule.getVersion();
        if (isTeamScope(rule.getScope())) {
            Path mirrorRepoPath = resolveRuntimeMirrorPath(settings.workspaceRoot(), settings.repoUrl());
            syncMirror(settings.repoUrl(), settings.defaultBranch(), mirrorRepoPath);
            checkoutOrCreateBranch(mirrorRepoPath, settings.defaultBranch());
            configureGitIdentity(mirrorRepoPath);
            job.setStep("git_prepare");
            job.setBranchName(settings.defaultBranch());
            publicationJobRepository.save(job);

            writeRuleFiles(mirrorRepoPath, sourceDir, rule, settings.defaultBranch());
            job.setStep("git_commit");
            publicationJobRepository.save(job);
            runGit(List.of("git", "-C", mirrorRepoPath.toString(), "add", sourceDir));
            runGit(List.of("git", "-C", mirrorRepoPath.toString(), "commit", "-m", "publish(rule-team): " + rule.getCanonicalName()));
            String commitSha = runGitWithOutput(List.of("git", "-C", mirrorRepoPath.toString(), "rev-parse", "HEAD")).trim();
            return new PublishResult(commitSha, null, null, commitSha, false);
        }

        Path repoPath = resolvePublishRepoPath(settings.workspaceRoot(), settings.repoUrl());
        syncMirror(settings.repoUrl(), settings.defaultBranch(), repoPath);

        String branchName;
        String mode = normalizePublishMode(request.getRequestedMode());
        if ("pr".equals(mode)) {
            branchName = "publish/rule/" + rule.getRuleId() + "/" + rule.getVersion().replace('.', '-') + "/" + Instant.now().toEpochMilli();
        } else {
            branchName = settings.defaultBranch();
        }
        job.setStep("git_prepare");
        job.setBranchName(branchName);
        publicationJobRepository.save(job);

        runGit(List.of("git", "-C", repoPath.toString(), "checkout", "-B", settings.defaultBranch(), "origin/" + settings.defaultBranch()));
        if (!branchName.equals(settings.defaultBranch())) {
            runGit(List.of("git", "-C", repoPath.toString(), "checkout", "-B", branchName));
        }
        configureGitIdentity(repoPath);

        writeRuleFiles(repoPath, sourceDir, rule, settings.defaultBranch());

        job.setStep("git_commit");
        publicationJobRepository.save(job);
        runGit(List.of("git", "-C", repoPath.toString(), "add", sourceDir));
        runGit(List.of("git", "-C", repoPath.toString(), "commit", "-m", "publish(rule): " + rule.getCanonicalName()));
        String commitSha = runGitWithOutput(List.of("git", "-C", repoPath.toString(), "rev-parse", "HEAD")).trim();
        String pushUrl = authenticatedRepoUrl(settings.repoUrl(), settings.gitUsername(), settings.gitPasswordOrPat());
        runGit(List.of("git", "-C", repoPath.toString(), "push", pushUrl, branchName));

        if ("pr".equals(mode)) {
            job.setStep("create_pr");
            publicationJobRepository.save(job);
            PrResult prResult = createPullRequest(settings, branchName, rule.getCanonicalName());
            return new PublishResult(commitSha, prResult.url(), prResult.number(), branchName, true);
        }
        syncRuntimeMirrorContent(settings.workspaceRoot(), settings.repoUrl(), repoPath, sourceDir);
        return new PublishResult(commitSha, null, null, commitSha, false);
    }

    private PublishResult publishFlow(FlowVersion flow, PublicationRequest request, PublicationJob job) throws IOException, InterruptedException {
        CatalogGitSettings settings = loadCatalogGitSettings();
        String sourceDir = "flows/" + flow.getFlowId() + "/" + flow.getVersion();
        if (isTeamScope(flow.getScope())) {
            Path mirrorRepoPath = resolveRuntimeMirrorPath(settings.workspaceRoot(), settings.repoUrl());
            syncMirror(settings.repoUrl(), settings.defaultBranch(), mirrorRepoPath);
            checkoutOrCreateBranch(mirrorRepoPath, settings.defaultBranch());
            configureGitIdentity(mirrorRepoPath);
            job.setStep("git_prepare");
            job.setBranchName(settings.defaultBranch());
            publicationJobRepository.save(job);

            writeFlowFiles(mirrorRepoPath, sourceDir, flow, settings.defaultBranch());
            job.setStep("git_commit");
            publicationJobRepository.save(job);
            runGit(List.of("git", "-C", mirrorRepoPath.toString(), "add", sourceDir));
            runGit(List.of("git", "-C", mirrorRepoPath.toString(), "commit", "-m", "publish(flow-team): " + flow.getCanonicalName()));
            String commitSha = runGitWithOutput(List.of("git", "-C", mirrorRepoPath.toString(), "rev-parse", "HEAD")).trim();
            return new PublishResult(commitSha, null, null, commitSha, false);
        }

        Path repoPath = resolvePublishRepoPath(settings.workspaceRoot(), settings.repoUrl());
        syncMirror(settings.repoUrl(), settings.defaultBranch(), repoPath);

        String branchName;
        String mode = normalizePublishMode(request.getRequestedMode());
        if ("pr".equals(mode)) {
            branchName = "publish/flow/" + flow.getFlowId() + "/" + flow.getVersion().replace('.', '-') + "/" + Instant.now().toEpochMilli();
        } else {
            branchName = settings.defaultBranch();
        }
        job.setStep("git_prepare");
        job.setBranchName(branchName);
        publicationJobRepository.save(job);

        runGit(List.of("git", "-C", repoPath.toString(), "checkout", "-B", settings.defaultBranch(), "origin/" + settings.defaultBranch()));
        if (!branchName.equals(settings.defaultBranch())) {
            runGit(List.of("git", "-C", repoPath.toString(), "checkout", "-B", branchName));
        }
        configureGitIdentity(repoPath);

        writeFlowFiles(repoPath, sourceDir, flow, settings.defaultBranch());

        job.setStep("git_commit");
        publicationJobRepository.save(job);
        runGit(List.of("git", "-C", repoPath.toString(), "add", sourceDir));
        runGit(List.of("git", "-C", repoPath.toString(), "commit", "-m", "publish(flow): " + flow.getCanonicalName()));
        String commitSha = runGitWithOutput(List.of("git", "-C", repoPath.toString(), "rev-parse", "HEAD")).trim();
        String pushUrl = authenticatedRepoUrl(settings.repoUrl(), settings.gitUsername(), settings.gitPasswordOrPat());
        runGit(List.of("git", "-C", repoPath.toString(), "push", pushUrl, branchName));

        if ("pr".equals(mode)) {
            job.setStep("create_pr");
            publicationJobRepository.save(job);
            PrResult prResult = createPullRequest(settings, branchName, flow.getCanonicalName());
            return new PublishResult(commitSha, prResult.url(), prResult.number(), branchName, true);
        }
        syncRuntimeMirrorContent(settings.workspaceRoot(), settings.repoUrl(), repoPath, sourceDir);
        return new PublishResult(commitSha, null, null, commitSha, false);
    }

    private void writeSkillFiles(Path repoPath, String sourceDir, SkillVersion skill, String baseBranch) throws IOException {
        Path dir = repoPath.resolve(sourceDir);
        Files.createDirectories(dir);
        Path markdownPath = dir.resolve("SKILL.md");
        Files.writeString(markdownPath, skill.getSkillMarkdown(), StandardCharsets.UTF_8);

        String checksum = ChecksumUtil.sha256(skill.getSkillMarkdown());
        String metadata = String.join("\n",
                "entity_type: skill",
                "id: " + skill.getSkillId(),
                "version: " + toYamlString(skill.getVersion()),
                "canonical_name: " + skill.getCanonicalName(),
                "display_name: " + toYamlString(skill.getName()),
                "description: " + toYamlString(skill.getDescription()),
                "coding_agent: " + (skill.getCodingAgent() == null ? "qwen" : skill.getCodingAgent().name().toLowerCase(Locale.ROOT)),
                "team_code: " + toYamlString(skill.getTeamCode()),
                "platform_code: " + toYamlString(skill.getPlatformCode()),
                "tags: " + toYamlInlineList(skill.getTags()),
                "skill_kind: " + toYamlString(skill.getSkillKind()),
                "scope: " + toYamlString(skill.getScope()),
                "environment: " + toYamlLowerName(skill.getEnvironment()),
                "approval_status: " + toYamlLowerName(skill.getApprovalStatus()),
                "approved_by: " + toYamlString(skill.getApprovedBy()),
                "approved_at: " + toYamlInstant(skill.getApprovedAt()),
                "published_at: " + toYamlInstant(skill.getPublishedAt()),
                "source_ref: " + toYamlString(baseBranch),
                "source_path: " + toYamlString(sourceDir),
                "content_source: git",
                "visibility: " + toYamlLowerName(skill.getVisibility()),
                "lifecycle_status: " + toYamlLowerName(skill.getLifecycleStatus()),
                "forked_from: " + toYamlString(skill.getForkedFrom()),
                "forked_by: " + toYamlString(skill.getForkedBy()),
                "checksum: " + toYamlString(checksum),
                "");
        Files.writeString(dir.resolve("metadata.yaml"), metadata, StandardCharsets.UTF_8);
    }

    private void writeRuleFiles(Path repoPath, String sourceDir, RuleVersion rule, String baseBranch) throws IOException {
        Path dir = repoPath.resolve(sourceDir);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("RULE.md"), rule.getRuleMarkdown(), StandardCharsets.UTF_8);

        String checksum = ChecksumUtil.sha256(rule.getRuleMarkdown());
        String metadata = String.join("\n",
                "entity_type: rule",
                "id: " + rule.getRuleId(),
                "version: " + toYamlString(rule.getVersion()),
                "canonical_name: " + rule.getCanonicalName(),
                "display_name: " + toYamlString(rule.getTitle()),
                "description: " + toYamlString(rule.getDescription()),
                "coding_agent: " + (rule.getCodingAgent() == null ? "qwen" : rule.getCodingAgent().name().toLowerCase(Locale.ROOT)),
                "team_code: " + toYamlString(rule.getTeamCode()),
                "platform_code: " + toYamlString(rule.getPlatformCode()),
                "tags: " + toYamlInlineList(rule.getTags()),
                "rule_kind: " + toYamlString(rule.getRuleKind()),
                "scope: " + toYamlString(rule.getScope()),
                "environment: " + toYamlLowerName(rule.getEnvironment()),
                "approval_status: " + toYamlLowerName(rule.getApprovalStatus()),
                "approved_by: " + toYamlString(rule.getApprovedBy()),
                "approved_at: " + toYamlInstant(rule.getApprovedAt()),
                "published_at: " + toYamlInstant(rule.getPublishedAt()),
                "source_ref: " + toYamlString(baseBranch),
                "source_path: " + toYamlString(sourceDir),
                "content_source: git",
                "visibility: " + toYamlLowerName(rule.getVisibility()),
                "lifecycle_status: " + toYamlLowerName(rule.getLifecycleStatus()),
                "forked_from: " + toYamlString(rule.getForkedFrom()),
                "forked_by: " + toYamlString(rule.getForkedBy()),
                "checksum: " + toYamlString(checksum),
                "");
        Files.writeString(dir.resolve("metadata.yaml"), metadata, StandardCharsets.UTF_8);
    }

    private void writeFlowFiles(Path repoPath, String sourceDir, FlowVersion flow, String baseBranch) throws IOException {
        Path dir = repoPath.resolve(sourceDir);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("FLOW.yaml"), flow.getFlowYaml(), StandardCharsets.UTF_8);

        String checksum = ChecksumUtil.sha256(flow.getFlowYaml());
        String metadata = String.join("\n",
                "entity_type: flow",
                "id: " + flow.getFlowId(),
                "version: " + toYamlString(flow.getVersion()),
                "canonical_name: " + flow.getCanonicalName(),
                "display_name: " + toYamlString(flow.getTitle()),
                "description: " + toYamlString(flow.getDescription()),
                "coding_agent: " + toYamlString(flow.getCodingAgent()),
                "team_code: " + toYamlString(flow.getTeamCode()),
                "platform_code: " + toYamlString(flow.getPlatformCode()),
                "tags: " + toYamlInlineList(flow.getTags()),
                "flow_kind: " + toYamlString(flow.getFlowKind()),
                "risk_level: " + toYamlString(flow.getRiskLevel()),
                "scope: " + toYamlString(flow.getScope()),
                "environment: " + toYamlLowerName(flow.getEnvironment()),
                "approval_status: " + toYamlLowerName(flow.getApprovalStatus()),
                "approved_by: " + toYamlString(flow.getApprovedBy()),
                "approved_at: " + toYamlInstant(flow.getApprovedAt()),
                "published_at: " + toYamlInstant(flow.getPublishedAt()),
                "source_ref: " + toYamlString(baseBranch),
                "source_path: " + toYamlString(sourceDir),
                "content_source: git",
                "visibility: " + toYamlLowerName(flow.getVisibility()),
                "lifecycle_status: " + toYamlLowerName(flow.getLifecycleStatus()),
                "forked_from: " + toYamlString(flow.getForkedFrom()),
                "forked_by: " + toYamlString(flow.getForkedBy()),
                "checksum: " + toYamlString(checksum),
                "");
        Files.writeString(dir.resolve("metadata.yaml"), metadata, StandardCharsets.UTF_8);
    }

    private String toYamlString(String value) {
        if (value == null || value.isBlank()) {
            return "''";
        }
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private String toYamlInstant(Instant value) {
        if (value == null) {
            return "''";
        }
        return toYamlString(value.toString());
    }

    private String toYamlLowerName(Enum<?> value) {
        if (value == null) {
            return "''";
        }
        return value.name().toLowerCase(Locale.ROOT);
    }

    private String toYamlInlineList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        String joined = values.stream()
                .filter((item) -> item != null && !item.isBlank())
                .map(this::toYamlString)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        if (joined.isBlank()) {
            return "[]";
        }
        return "[" + joined + "]";
    }

    private PrResult createPullRequest(CatalogGitSettings settings, String branchName, String canonicalName) {
        if (settings.gitPasswordOrPat() == null || settings.gitPasswordOrPat().isBlank()) {
            throw new ValidationException("git_password_or_pat is required for PR mode");
        }
        RepoCoordinates repo = parseGitHubRepo(settings.repoUrl());
        try {
            Map<String, Object> payload = Map.of(
                    "title", "Publish " + canonicalName,
                    "head", branchName,
                    "base", settings.defaultBranch(),
                    "body", "Automated publication from SDLC"
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + repo.owner() + "/" + repo.repo() + "/pulls"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + settings.gitPasswordOrPat())
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ValidationException("GitHub PR creation failed: HTTP " + response.statusCode() + " " + truncate(response.body(), 1000));
            }
            Map<?, ?> parsed = JSON.readValue(response.body(), Map.class);
            Object prUrl = parsed.get("html_url");
            Object number = parsed.get("number");
            return new PrResult(prUrl == null ? null : String.valueOf(prUrl), number instanceof Number n ? n.intValue() : null);
        } catch (IOException | InterruptedException ex) {
            throw new ValidationException("GitHub PR creation failed: " + ex.getMessage());
        }
    }

    private RepoCoordinates parseGitHubRepo(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new ValidationException("catalog_repo_url is required");
        }
        String trimmed = repoUrl.trim();
        String path;
        if (trimmed.startsWith("git@github.com:")) {
            path = trimmed.substring("git@github.com:".length());
        } else {
            try {
                URI parsed = URI.create(trimmed);
                if (!"github.com".equalsIgnoreCase(parsed.getHost())) {
                    throw new ValidationException("Only github.com is supported for PR mode");
                }
                path = parsed.getPath();
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("Invalid repository URL: " + repoUrl);
            }
        }
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        String[] parts = path.split("/");
        if (parts.length < 2) {
            throw new ValidationException("Repository URL must include owner/repo: " + repoUrl);
        }
        return new RepoCoordinates(parts[0], parts[1]);
    }

    private CatalogGitSettings loadCatalogGitSettings() {
        String repoUrl = valueOrDefault(SettingsService.CATALOG_REPO_URL_KEY, "https://github.com/npronnikov/catalog.git");
        String branch = valueOrDefault(SettingsService.CATALOG_DEFAULT_BRANCH_KEY, "main");
        String mode = valueOrDefault(SettingsService.CATALOG_PUBLISH_MODE_KEY, "pr");
        String username = valueOrDefault(SettingsService.CATALOG_GIT_USERNAME_KEY, "");
        String password = valueOrDefault(SettingsService.CATALOG_GIT_PASSWORD_KEY, "");
        String workspaceRoot = valueOrDefault(SettingsService.WORKSPACE_ROOT_KEY, "/tmp/workspace");
        return new CatalogGitSettings(repoUrl, branch, mode, username, password, workspaceRoot);
    }

    private String valueOrDefault(String key, String fallback) {
        Optional<SystemSetting> setting = systemSettingRepository.findById(key);
        String value = setting.map(SystemSetting::getSettingValue).orElse(null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String normalizePublishMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "pr";
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals("local") && !normalized.equals("pr")) {
            throw new ValidationException("publish_mode must be local or pr");
        }
        return normalized;
    }

    private String normalizePublishModeForScope(String mode, String scope) {
        if (isTeamScope(scope)) {
            return "local";
        }
        return normalizePublishMode(mode);
    }

    private PublicationTarget normalizeTargetForScope(PublicationTarget requestedTarget, String scope) {
        if (isTeamScope(scope)) {
            return PublicationTarget.GIT_ONLY;
        }
        if (requestedTarget == null || requestedTarget == PublicationTarget.DB_ONLY) {
            return PublicationTarget.DB_AND_GIT;
        }
        return requestedTarget;
    }

    private boolean isTeamScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return false;
        }
        return TEAM_SCOPE.equals(scope.trim().toLowerCase(Locale.ROOT));
    }

    private Path resolvePublishRepoPath(String workspaceRoot, String repoUrl) {
        String suffix = Integer.toHexString(repoUrl.toLowerCase(Locale.ROOT).hashCode());
        return Path.of(workspaceRoot).toAbsolutePath().normalize().resolve(".catalog-publish").resolve(suffix);
    }

    private Path resolveRuntimeMirrorPath(String workspaceRoot, String repoUrl) {
        String suffix = Integer.toHexString(repoUrl.toLowerCase(Locale.ROOT).hashCode());
        return Path.of(workspaceRoot).toAbsolutePath().normalize().resolve(".catalog-mirror").resolve(suffix);
    }

    private void syncMirror(String repoUrl, String branch, Path mirrorPath) throws IOException, InterruptedException {
        Files.createDirectories(mirrorPath.getParent());
        if (!Files.isDirectory(mirrorPath.resolve(".git"))) {
            runGit(List.of("git", "clone", "--branch", branch, "--single-branch", repoUrl, mirrorPath.toString()));
            return;
        }
        runGit(List.of("git", "-C", mirrorPath.toString(), "remote", "set-url", "origin", repoUrl));
        runGit(List.of("git", "-C", mirrorPath.toString(), "fetch", "--prune", "--tags", "origin"));
    }

    private void checkoutOrCreateBranch(Path repoPath, String branch) throws IOException, InterruptedException {
        try {
            runGit(List.of("git", "-C", repoPath.toString(), "checkout", branch));
        } catch (ValidationException ex) {
            runGit(List.of("git", "-C", repoPath.toString(), "checkout", "-B", branch, "origin/" + branch));
        }
    }

    private void configureGitIdentity(Path repoPath) throws IOException, InterruptedException {
        String username = valueOrDefault(SettingsService.CATALOG_LOCAL_GIT_USERNAME_KEY, SettingsService.DEFAULT_LOCAL_GIT_USERNAME);
        String email = valueOrDefault(SettingsService.CATALOG_LOCAL_GIT_EMAIL_KEY, SettingsService.DEFAULT_LOCAL_GIT_EMAIL);
        runGit(List.of("git", "-C", repoPath.toString(), "config", "user.name", username));
        runGit(List.of("git", "-C", repoPath.toString(), "config", "user.email", email));
    }

    private void syncRuntimeMirrorContent(String workspaceRoot, String repoUrl, Path publishRepoPath, String sourceDir) throws IOException {
        Path mirrorRoot = resolveRuntimeMirrorPath(workspaceRoot, repoUrl);
        Path sourcePath = publishRepoPath.resolve(sourceDir).normalize();
        Path targetPath = mirrorRoot.resolve(sourceDir).normalize();
        if (!Files.exists(sourcePath)) {
            throw new ValidationException("Published source directory not found: " + sourcePath);
        }
        copyDirectory(sourcePath, targetPath);
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> stream = Files.walk(source)) {
            stream.sorted()
                    .forEach(path -> {
                        try {
                            Path relative = source.relativize(path);
                            Path destination = target.resolve(relative);
                            if (Files.isDirectory(path)) {
                                Files.createDirectories(destination);
                            } else {
                                Files.createDirectories(destination.getParent());
                                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                            }
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException ioEx) {
                throw ioEx;
            }
            throw ex;
        }
    }

    private String authenticatedRepoUrl(String repoUrl, String username, String passwordOrPat) {
        if (repoUrl == null || repoUrl.isBlank() || passwordOrPat == null || passwordOrPat.isBlank()) {
            return repoUrl;
        }
        try {
            URI uri = URI.create(repoUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                return repoUrl;
            }
            String user = (username == null || username.isBlank()) ? "x-access-token" : username;
            String userInfo = user + ":" + passwordOrPat;
            return new URI(uri.getScheme(), userInfo, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return repoUrl;
        }
    }

    private void runGit(List<String> command) throws IOException, InterruptedException {
        runGitWithOutput(command);
    }

    private String runGitWithOutput(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] outputBytes = process.getInputStream().readAllBytes();
        boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES);
        String output = new String(outputBytes, StandardCharsets.UTF_8);
        if (!finished) {
            process.destroyForcibly();
            throw new ValidationException("Git command timed out");
        }
        if (process.exitValue() != 0) {
            throw new ValidationException("Git command failed: " + truncate(output, 2000));
        }
        return output;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private void requireApprover(User user) {
        if (user == null || !user.hasAnyRole(Role.TECH_APPROVER, Role.ADMIN)) {
            throw new ValidationException("Approver role is required");
        }
    }

    public record PublicationDashboard(
            List<PublicationRequest> requests,
            List<PublicationJob> jobs
    ) {}

    private record CatalogGitSettings(
            String repoUrl,
            String defaultBranch,
            String publishMode,
            String gitUsername,
            String gitPasswordOrPat,
            String workspaceRoot
    ) {}

    private record RepoCoordinates(String owner, String repo) {}

    private record PrResult(String url, Integer number) {}

    private record PublishResult(String commitSha, String prUrl, Integer prNumber, String sourceRef, boolean awaitingMerge) {}
}

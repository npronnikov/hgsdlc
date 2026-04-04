package ru.hgd.sdlc.publication.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.flow.domain.FlowApprovalStatus;
import ru.hgd.sdlc.flow.domain.FlowStatus;
import ru.hgd.sdlc.flow.domain.FlowVersion;
import ru.hgd.sdlc.flow.infrastructure.FlowVersionRepository;
import ru.hgd.sdlc.publication.domain.PublicationEntityType;
import ru.hgd.sdlc.publication.domain.PublicationRequest;
import ru.hgd.sdlc.publication.domain.PublicationStatus;
import ru.hgd.sdlc.publication.infrastructure.PublicationJobRepository;
import ru.hgd.sdlc.publication.infrastructure.PublicationRequestRepository;
import ru.hgd.sdlc.rule.domain.RuleApprovalStatus;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;
import ru.hgd.sdlc.settings.application.SettingsService;
import ru.hgd.sdlc.settings.domain.SystemSetting;
import ru.hgd.sdlc.settings.infrastructure.SystemSettingRepository;
import ru.hgd.sdlc.skill.domain.SkillApprovalStatus;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

@Service
public class PublicationPrPollService {
    private static final Logger log = LoggerFactory.getLogger(PublicationPrPollService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PublicationRequestRepository publicationRequestRepository;
    private final PublicationJobRepository publicationJobRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final RuleVersionRepository ruleVersionRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final SystemSettingRepository systemSettingRepository;

    public PublicationPrPollService(
            PublicationRequestRepository publicationRequestRepository,
            PublicationJobRepository publicationJobRepository,
            SkillVersionRepository skillVersionRepository,
            RuleVersionRepository ruleVersionRepository,
            FlowVersionRepository flowVersionRepository,
            SystemSettingRepository systemSettingRepository
    ) {
        this.publicationRequestRepository = publicationRequestRepository;
        this.publicationJobRepository = publicationJobRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.ruleVersionRepository = ruleVersionRepository;
        this.flowVersionRepository = flowVersionRepository;
        this.systemSettingRepository = systemSettingRepository;
    }

    @Scheduled(
            fixedDelayString = "${publication.pr-poll-interval-ms:60000}",
            initialDelayString = "${publication.pr-poll-initial-delay-ms:15000}"
    )
    @Transactional
    public void pollPrMergeStatus() {
        List<PublicationRequest> publishing = publicationRequestRepository.findByStatusOrderByCreatedAtDesc(PublicationStatus.PUBLISHING);
        if (publishing.isEmpty()) {
            return;
        }

        String token = valueOrDefault(SettingsService.CATALOG_GIT_PASSWORD_KEY, "");
        String defaultBranch = valueOrDefault(SettingsService.CATALOG_DEFAULT_BRANCH_KEY, "");
        for (PublicationRequest request : publishing) {
            Optional<String> prUrlOpt = latestPrUrl(request.getId());
            if (prUrlOpt.isEmpty()) {
                continue;
            }
            PrCoordinates pr = parsePrUrl(prUrlOpt.get());
            if (pr == null) {
                continue;
            }
            try {
                PrState state = fetchPrState(pr, token);
                if (state == null) {
                    continue;
                }
                if (state.merged()) {
                    markPublished(request, defaultBranch, state.mergeCommitSha());
                } else if (state.closed()) {
                    markFailed(request, "PR closed without merge");
                }
            } catch (Exception ex) {
                log.warn("Failed to poll PR merge state for request {}: {}", request.getId(), ex.getMessage());
            }
        }
    }

    private Optional<String> latestPrUrl(java.util.UUID requestId) {
        return publicationJobRepository.findByRequestIdOrderByCreatedAtDesc(requestId).stream()
                .map((job) -> job.getPrUrl())
                .filter((url) -> url != null && !url.isBlank())
                .findFirst();
    }

    private PrState fetchPrState(PrCoordinates pr, String token) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + pr.owner() + "/" + pr.repo() + "/pulls/" + pr.number()))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("GitHub PR poll failed (HTTP {}): {}", response.statusCode(), truncate(response.body(), 500));
            return null;
        }
        Map<?, ?> parsed = JSON.readValue(response.body(), Map.class);
        boolean merged = Boolean.TRUE.equals(parsed.get("merged"));
        Object stateRaw = parsed.get("state");
        String state = stateRaw == null ? "" : String.valueOf(stateRaw);
        String mergeCommitSha = parsed.get("merge_commit_sha") == null ? null : String.valueOf(parsed.get("merge_commit_sha"));
        return new PrState(merged, "closed".equalsIgnoreCase(state), mergeCommitSha);
    }

    private void markPublished(PublicationRequest request, String defaultBranch, String mergeCommitSha) {
        request.setStatus(PublicationStatus.PUBLISHED);
        request.setUpdatedAt(Instant.now());
        request.setLastError(null);
        publicationRequestRepository.save(request);

        switch (request.getEntityType()) {
            case SKILL -> {
                SkillVersion skill = skillVersionRepository.findFirstBySkillIdAndVersionOrderBySavedAtDesc(request.getEntityId(), request.getVersion()).orElse(null);
                if (skill == null) {
                    return;
                }
                skill.setStatus(SkillStatus.PUBLISHED);
                skill.setApprovalStatus(SkillApprovalStatus.PUBLISHED);
                skill.setPublicationStatus(PublicationStatus.PUBLISHED);
                skill.setPublishedAt(skill.getPublishedAt() == null ? Instant.now() : skill.getPublishedAt());
                skill.setSourceRef(defaultBranch);
                skill.setSourcePath("skills/" + skill.getSkillId() + "/" + skill.getVersion());
                if (mergeCommitSha != null && !mergeCommitSha.isBlank()) {
                    skill.setPublishedCommitSha(mergeCommitSha);
                }
                String checksum = skill.getChecksum();
                if (checksum == null || checksum.isBlank()) {
                    checksum = ChecksumUtil.sha256(skill.getSkillMarkdown());
                }
                skill.setChecksum(checksum);
                skillVersionRepository.save(skill);
            }
            case RULE -> {
                RuleVersion rule = ruleVersionRepository.findFirstByRuleIdAndVersionOrderBySavedAtDesc(request.getEntityId(), request.getVersion()).orElse(null);
                if (rule == null) {
                    return;
                }
                rule.setStatus(RuleStatus.PUBLISHED);
                rule.setApprovalStatus(RuleApprovalStatus.PUBLISHED);
                rule.setPublicationStatus(PublicationStatus.PUBLISHED);
                rule.setPublishedAt(rule.getPublishedAt() == null ? Instant.now() : rule.getPublishedAt());
                rule.setSourceRef(defaultBranch);
                rule.setSourcePath("rules/" + rule.getRuleId() + "/" + rule.getVersion());
                if (mergeCommitSha != null && !mergeCommitSha.isBlank()) {
                    rule.setPublishedCommitSha(mergeCommitSha);
                }
                rule.setChecksum(ChecksumUtil.sha256(rule.getRuleMarkdown()));
                ruleVersionRepository.save(rule);
            }
            case FLOW -> {
                FlowVersion flow = flowVersionRepository.findFirstByFlowIdAndVersionOrderBySavedAtDesc(request.getEntityId(), request.getVersion()).orElse(null);
                if (flow == null) {
                    return;
                }
                flow.setStatus(FlowStatus.PUBLISHED);
                flow.setApprovalStatus(FlowApprovalStatus.PUBLISHED);
                flow.setPublicationStatus(PublicationStatus.PUBLISHED);
                flow.setPublishedAt(flow.getPublishedAt() == null ? Instant.now() : flow.getPublishedAt());
                flow.setSourceRef(defaultBranch);
                flow.setSourcePath("flows/" + flow.getFlowId() + "/" + flow.getVersion());
                if (mergeCommitSha != null && !mergeCommitSha.isBlank()) {
                    flow.setPublishedCommitSha(mergeCommitSha);
                }
                flow.setChecksum(ChecksumUtil.sha256(flow.getFlowYaml()));
                flowVersionRepository.save(flow);
            }
        }
        syncLocalCatalogRepo(defaultBranch);
    }

    private void markFailed(PublicationRequest request, String error) {
        request.setStatus(PublicationStatus.FAILED);
        request.setUpdatedAt(Instant.now());
        request.setLastError(error);
        publicationRequestRepository.save(request);
        switch (request.getEntityType()) {
            case SKILL -> skillVersionRepository.findFirstBySkillIdAndVersionOrderBySavedAtDesc(request.getEntityId(), request.getVersion())
                    .ifPresent((skill) -> {
                        skill.setPublicationStatus(PublicationStatus.FAILED);
                        skill.setLastPublishError(error);
                        skillVersionRepository.save(skill);
                    });
            case RULE -> ruleVersionRepository.findFirstByRuleIdAndVersionOrderBySavedAtDesc(request.getEntityId(), request.getVersion())
                    .ifPresent((rule) -> {
                        rule.setPublicationStatus(PublicationStatus.FAILED);
                        rule.setLastPublishError(error);
                        ruleVersionRepository.save(rule);
                    });
            case FLOW -> flowVersionRepository.findFirstByFlowIdAndVersionOrderBySavedAtDesc(request.getEntityId(), request.getVersion())
                    .ifPresent((flow) -> {
                        flow.setPublicationStatus(PublicationStatus.FAILED);
                        flow.setLastPublishError(error);
                        flowVersionRepository.save(flow);
                    });
        }
    }

    private PrCoordinates parsePrUrl(String prUrl) {
        try {
            URI uri = URI.create(prUrl.trim());
            if (!"github.com".equalsIgnoreCase(uri.getHost())) {
                return null;
            }
            String[] parts = uri.getPath().split("/");
            // /owner/repo/pull/number
            if (parts.length < 5 || !"pull".equalsIgnoreCase(parts[3])) {
                return null;
            }
            return new PrCoordinates(parts[1], parts[2], Integer.parseInt(parts[4]));
        } catch (Exception ex) {
            log.warn("Cannot parse pr_url '{}': {}", prUrl, ex.getMessage());
            return null;
        }
    }

    private String valueOrDefault(String key, String fallback) {
        Optional<SystemSetting> setting = systemSettingRepository.findById(key);
        String value = setting.map(SystemSetting::getSettingValue).orElse(null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private void syncLocalCatalogRepo(String defaultBranch) {
        if (defaultBranch == null || defaultBranch.isBlank()) {
            return;
        }
        String repoUrl = valueOrDefault(SettingsService.CATALOG_REPO_URL_KEY, "");
        if (repoUrl.isBlank()) {
            return;
        }
        String workspaceRoot = valueOrDefault(SettingsService.WORKSPACE_ROOT_KEY, "/tmp/workspace");
        Path repoPath = resolveCatalogRepoPath(workspaceRoot, repoUrl);
        if (!Files.isDirectory(repoPath.resolve(".git"))) {
            return;
        }
        try {
            runGit(List.of("git", "-C", repoPath.toString(), "remote", "set-url", "origin", repoUrl));
            runGit(List.of("git", "-C", repoPath.toString(), "fetch", "--prune", "--tags", "origin"));
            try {
                runGit(List.of("git", "-C", repoPath.toString(), "checkout", defaultBranch));
            } catch (Exception ex) {
                runGit(List.of("git", "-C", repoPath.toString(), "checkout", "-B", defaultBranch, "origin/" + defaultBranch));
            }
            runGit(List.of("git", "-C", repoPath.toString(), "pull", "--ff-only", "origin", defaultBranch));
        } catch (Exception ex) {
            log.warn("Failed to sync local catalog clone after PR merge: {}", ex.getMessage());
        }
    }

    private Path resolveCatalogRepoPath(String workspaceRoot, String repoUrl) {
        Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        String suffix = Integer.toHexString(repoUrl.toLowerCase(Locale.ROOT).hashCode());
        return root.resolve(".catalog-repo").resolve(suffix);
    }

    private void runGit(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] outputBytes = process.getInputStream().readAllBytes();
        boolean finished = process.waitFor(2, java.util.concurrent.TimeUnit.MINUTES);
        String output = new String(outputBytes, StandardCharsets.UTF_8);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Git command timed out");
        }
        if (process.exitValue() != 0) {
            throw new IOException("Git command failed: " + truncate(output, 500));
        }
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

    private record PrCoordinates(String owner, String repo, int number) {}
    private record PrState(boolean merged, boolean closed, String mergeCommitSha) {}
}

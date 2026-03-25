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
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import ru.hgd.sdlc.publication.infrastructure.PublicationApprovalRepository;
import ru.hgd.sdlc.publication.infrastructure.PublicationJobRepository;
import ru.hgd.sdlc.publication.infrastructure.PublicationRequestRepository;
import ru.hgd.sdlc.settings.application.SettingsService;
import ru.hgd.sdlc.settings.domain.SystemSetting;
import ru.hgd.sdlc.settings.infrastructure.SystemSettingRepository;
import ru.hgd.sdlc.skill.domain.SkillContentSource;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

@Service
public class PublicationService {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final PublicationRequestRepository publicationRequestRepository;
    private final PublicationApprovalRepository publicationApprovalRepository;
    private final PublicationJobRepository publicationJobRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SystemSettingRepository systemSettingRepository;

    public PublicationService(
            PublicationRequestRepository publicationRequestRepository,
            PublicationApprovalRepository publicationApprovalRepository,
            PublicationJobRepository publicationJobRepository,
            SkillVersionRepository skillVersionRepository,
            SystemSettingRepository systemSettingRepository
    ) {
        this.publicationRequestRepository = publicationRequestRepository;
        this.publicationApprovalRepository = publicationApprovalRepository;
        this.publicationJobRepository = publicationJobRepository;
        this.skillVersionRepository = skillVersionRepository;
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
        request.setRequestedTarget(target);
        request.setRequestedMode(normalizePublishMode(requestedMode));
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
            skill.setContentSource(request.getRequestedTarget() == PublicationTarget.DB_ONLY ? SkillContentSource.DB : SkillContentSource.GIT);
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

    private PublishResult publish(SkillVersion skill, PublicationRequest request, PublicationJob job) throws IOException, InterruptedException {
        if (request.getRequestedTarget() == PublicationTarget.DB_ONLY) {
            return new PublishResult(null, null, null, "db", false);
        }
        CatalogGitSettings settings = loadCatalogGitSettings();
        Path repoPath = resolvePublishRepoPath(settings.workspaceRoot(), settings.repoUrl());
        syncMirror(settings.repoUrl(), settings.defaultBranch(), repoPath);

        String sourceDir = "skills/" + skill.getSkillId() + "/" + skill.getVersion();
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
        return new PublishResult(commitSha, null, null, commitSha, false);
    }

    private void writeSkillFiles(Path repoPath, String sourceDir, SkillVersion skill, String baseBranch) throws IOException {
        Path dir = repoPath.resolve(sourceDir);
        Files.createDirectories(dir);
        Path markdownPath = dir.resolve("SKILL.md");
        Files.writeString(markdownPath, skill.getSkillMarkdown(), StandardCharsets.UTF_8);

        String checksum = ChecksumUtil.sha256(skill.getSkillMarkdown());
        String metadata = String.join("\n",
                "id: " + skill.getSkillId(),
                "version: " + skill.getVersion(),
                "canonical_name: " + skill.getCanonicalName(),
                "entity_type: skill",
                "display_name: " + skill.getName(),
                "description: " + toYamlString(skill.getDescription()),
                "coding_agent: " + (skill.getCodingAgent() == null ? "qwen" : skill.getCodingAgent().name().toLowerCase(Locale.ROOT)),
                "source_ref: " + baseBranch,
                "source_path: " + sourceDir,
                "checksum: sha256:" + checksum,
                "");
        Files.writeString(dir.resolve("metadata.yaml"), metadata, StandardCharsets.UTF_8);
    }

    private String toYamlString(String value) {
        if (value == null || value.isBlank()) {
            return "''";
        }
        return "\"" + value.replace("\"", "\\\"") + "\"";
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

    private Path resolvePublishRepoPath(String workspaceRoot, String repoUrl) {
        String suffix = Integer.toHexString(repoUrl.toLowerCase(Locale.ROOT).hashCode());
        return Path.of(workspaceRoot).toAbsolutePath().normalize().resolve(".catalog-publish").resolve(suffix);
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

package ru.hgd.sdlc.runtime.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.project.domain.Project;
import ru.hgd.sdlc.project.infrastructure.ProjectRepository;
import ru.hgd.sdlc.runtime.application.RuntimeStepTxService;
import ru.hgd.sdlc.runtime.application.port.ProcessExecutionPort;
import ru.hgd.sdlc.runtime.domain.PrCommitStrategy;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.domain.RunPublishMode;
import ru.hgd.sdlc.runtime.domain.RunPublishStatus;
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;
import ru.hgd.sdlc.settings.application.SettingsService;

@Service
public class RunPublishService {
    private static final Logger log = LoggerFactory.getLogger(RunPublishService.class);
    private static final String DEFAULT_PUSH_USER = "x-access-token";
    private static final Duration PR_TIMEOUT = Duration.ofSeconds(30);

    private final RunRepository runRepository;
    private final ProjectRepository projectRepository;
    private final RuntimeStepTxService runtimeStepTxService;
    private final ProcessExecutionPort processExecutionPort;
    private final SettingsService settingsService;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<UUID, Object> publishLocks = new ConcurrentHashMap<>();

    public RunPublishService(
            RunRepository runRepository,
            ProjectRepository projectRepository,
            RuntimeStepTxService runtimeStepTxService,
            ProcessExecutionPort processExecutionPort,
            SettingsService settingsService,
            TaskExecutor taskExecutor,
            ObjectMapper objectMapper
    ) {
        this.runRepository = runRepository;
        this.projectRepository = projectRepository;
        this.runtimeStepTxService = runtimeStepTxService;
        this.processExecutionPort = processExecutionPort;
        this.settingsService = settingsService;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    public void dispatchPublish(UUID runId) {
        taskExecutor.execute(() -> publishRun(runId));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RunEntity retryPublish(UUID runId, String actorId) {
        RunEntity run = getRun(runId);
        if (run.getStatus() == RunStatus.COMPLETED) {
            return run;
        }
        if (run.getStatus() != RunStatus.PUBLISH_FAILED && run.getStatus() != RunStatus.WAITING_PUBLISH) {
            throw new ConflictException("Retry publish is allowed only for PUBLISH_FAILED or WAITING_PUBLISH runs");
        }
        runtimeStepTxService.resetPublishForRetry(runId, actorId);
        dispatchPublish(runId);
        return getRun(runId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void publishRun(UUID runId) {
        Object lock = publishLocks.computeIfAbsent(runId, (ignored) -> new Object());
        synchronized (lock) {
            try {
                publishRunInternal(runId);
            } finally {
                publishLocks.remove(runId, lock);
            }
        }
    }

    private void publishRunInternal(UUID runId) {
        RunEntity run = getRun(runId);
        if (run.getStatus() == RunStatus.COMPLETED || run.getStatus() == RunStatus.CANCELLED || run.getStatus() == RunStatus.FAILED) {
            return;
        }
        if (run.getStatus() != RunStatus.WAITING_PUBLISH && run.getStatus() != RunStatus.PUBLISH_FAILED) {
            return;
        }

        runtimeStepTxService.markPublishRunning(runId);
        try {
            run = getRun(runId);
            if (run.getPublishMode() == RunPublishMode.LOCAL && !isGitWorkspace(run)) {
                runtimeStepTxService.markPublishCommitSkipped(runId, "workspace_not_git_repository");
                runtimeStepTxService.markPublishCompleted(runId);
                return;
            }
            ensureOnWorkBranch(run);
            configureLocalGitIdentity(run);

            if (run.getPublishCommitSha() == null || run.getPublishCommitSha().isBlank()) {
                String publishCommitSha = createFinalCommit(run);
                runtimeStepTxService.markPublishCommitSucceeded(runId, publishCommitSha);
            }

            if (run.getPublishMode() == RunPublishMode.PR) {
                run = getRun(runId);
                if (run.getPushStatus() != RunPublishStatus.SUCCEEDED) {
                    pushWorkBranch(run);
                    runtimeStepTxService.markPublishPushSucceeded(runId);
                }

                run = getRun(runId);
                if (run.getPrStatus() != RunPublishStatus.SUCCEEDED) {
                    PrResult pr = createOrFindPullRequest(run);
                    runtimeStepTxService.markPublishPrSucceeded(runId, pr.url(), pr.number());
                }
            }

            runtimeStepTxService.markPublishCompleted(runId);
        } catch (PublishException ex) {
            runtimeStepTxService.markPublishFailed(runId, ex.step(), ex.errorCode(), ex.getMessage());
        } catch (Exception ex) {
            runtimeStepTxService.markPublishFailed(runId, "publish", "PUBLISH_FAILED", ex.getMessage());
            log.error("Publish failed for run {}", runId, ex);
        }
    }

    private void ensureOnWorkBranch(RunEntity run) {
        Path root = resolveProjectRoot(run);
        runGitOrThrow(
                run,
                "checkout_work_branch",
                List.of("git", "-C", root.toString(), "checkout", run.getWorkBranch()),
                "commit"
        );
    }

    private void configureLocalGitIdentity(RunEntity run) {
        SettingsService.RuntimeSettings settings = settingsService.getRuntimeSettings();
        String localUser = trimToNull(settings.localGitUsername());
        String localEmail = trimToNull(settings.localGitEmail());
        if (localUser == null || localEmail == null) {
            throw new PublishException(
                    "commit",
                    "LOCAL_AUTH_INVALID",
                    "runtime local git identity is empty"
            );
        }
        Path root = resolveProjectRoot(run);
        runGitOrThrow(
                run,
                "config_user_name",
                List.of("git", "-C", root.toString(), "config", "user.name", localUser),
                "commit"
        );
        runGitOrThrow(
                run,
                "config_user_email",
                List.of("git", "-C", root.toString(), "config", "user.email", localEmail),
                "commit"
        );
    }

    private String createFinalCommit(RunEntity run) {
        if (run.getPrCommitStrategy() != null && run.getPrCommitStrategy() != PrCommitStrategy.SQUASH) {
            throw new PublishException("commit", "UNSUPPORTED_PR_COMMIT_STRATEGY", "Unsupported pr_commit_strategy");
        }
        Path root = resolveProjectRoot(run);
        runGitOrThrow(run, "final_add", List.of("git", "-C", root.toString(), "add", "-A"), "commit");
        runGitOrThrow(
                run,
                "final_soft_reset",
                List.of("git", "-C", root.toString(), "reset", "--soft", run.getTargetBranch()),
                "commit"
        );
        runGitOrThrow(
                run,
                "final_commit",
                List.of("git", "-C", root.toString(), "commit", "--allow-empty", "-m", "runtime publish: run " + run.getId()),
                "commit"
        );
        CommandResult sha = runGitOrThrow(
                run,
                "final_rev_parse",
                List.of("git", "-C", root.toString(), "rev-parse", "HEAD"),
                "commit"
        );
        String commitSha = trimToNull(sha.stdout());
        if (commitSha == null) {
            throw new PublishException("commit", "FINAL_COMMIT_SHA_MISSING", "Failed to resolve final commit sha");
        }
        return commitSha.split("\\R", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private void pushWorkBranch(RunEntity run) {
        Project project = resolveProject(run.getProjectId());
        String repoUrl = trimToNull(project.getRepoUrl());
        if (repoUrl == null) {
            throw new PublishException("push", "PUSH_REPO_URL_MISSING", "Project repo_url is empty");
        }
        SettingsService.RuntimeSettings settings = settingsService.getRuntimeSettings();
        String pushUrl = authenticatedRepoUrl(repoUrl, settings.gitUsername(), settings.gitPasswordOrPat());
        Path root = resolveProjectRoot(run);
        runGitOrThrow(
                run,
                "final_push",
                List.of(
                        "git",
                        "-C",
                        root.toString(),
                        "push",
                        "--force-with-lease",
                        pushUrl,
                        run.getWorkBranch() + ":" + run.getWorkBranch()
                ),
                "push"
        );
    }

    private PrResult createOrFindPullRequest(RunEntity run) {
        if (trimToNull(run.getPrUrl()) != null && run.getPrNumber() != null) {
            return new PrResult(run.getPrUrl(), run.getPrNumber());
        }
        Project project = resolveProject(run.getProjectId());
        String repoUrl = trimToNull(project.getRepoUrl());
        RepoCoordinates repo = parseGitHubRepo(repoUrl);

        SettingsService.RuntimeSettings settings = settingsService.getRuntimeSettings();
        String token = trimToNull(settings.gitPasswordOrPat());
        if (token == null) {
            throw new PublishException(
                    "create_pr",
                    "REMOTE_AUTH_MISSING",
                    "git_password_or_pat must be configured to create a pull request"
            );
        }

        try {
            Map<String, Object> payload = Map.of(
                    "title", "Runtime run " + run.getId() + ": " + run.getFlowCanonicalName(),
                    "head", run.getWorkBranch(),
                    "base", run.getTargetBranch(),
                    "body", "Automated PR created by runtime publishing"
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + repo.owner() + "/" + repo.repo() + "/pulls"))
                    .timeout(PR_TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parsePrResult(response.body());
            }
            if (response.statusCode() == 422) {
                Optional<PrResult> existingPr = findExistingPullRequest(repo, run, token);
                if (existingPr.isPresent()) {
                    return existingPr.get();
                }
            }
            throw new PublishException(
                    "create_pr",
                    "PR_CREATION_FAILED",
                    "GitHub PR creation failed: HTTP " + response.statusCode() + " " + truncate(response.body(), 1000)
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PublishException("create_pr", "PR_CREATION_FAILED", "GitHub PR creation failed: " + ex.getMessage());
        } catch (IOException ex) {
            throw new PublishException("create_pr", "PR_CREATION_FAILED", "GitHub PR creation failed: " + ex.getMessage());
        }
    }

    private Optional<PrResult> findExistingPullRequest(RepoCoordinates repo, RunEntity run, String token) {
        String head = repo.owner() + ":" + run.getWorkBranch();
        String query = "state=open&head=" + urlEncode(head) + "&base=" + urlEncode(run.getTargetBranch());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + repo.owner() + "/" + repo.repo() + "/pulls?" + query))
                    .timeout(PR_TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + token)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            List<?> list = objectMapper.readValue(response.body(), List.class);
            if (list == null || list.isEmpty() || !(list.getFirst() instanceof Map<?, ?> first)) {
                return Optional.empty();
            }
            Object url = first.get("html_url");
            Object number = first.get("number");
            return Optional.of(new PrResult(
                    url == null ? null : String.valueOf(url),
                    number instanceof Number n ? n.intValue() : null
            ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private PrResult parsePrResult(String body) throws IOException {
        Map<?, ?> parsed = objectMapper.readValue(body, Map.class);
        Object prUrl = parsed.get("html_url");
        Object number = parsed.get("number");
        return new PrResult(
                prUrl == null ? null : String.valueOf(prUrl),
                number instanceof Number n ? n.intValue() : null
        );
    }

    private RepoCoordinates parseGitHubRepo(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new PublishException("create_pr", "REPO_URL_MISSING", "Project repo_url is required for PR publishing");
        }
        String trimmed = repoUrl.trim();
        String path;
        if (trimmed.startsWith("git@github.com:")) {
            path = trimmed.substring("git@github.com:".length());
        } else {
            try {
                URI parsed = URI.create(trimmed);
                if (!"github.com".equalsIgnoreCase(parsed.getHost())) {
                    throw new PublishException("create_pr", "PR_UNSUPPORTED_REPO", "Only github.com is supported for PR mode");
                }
                path = parsed.getPath();
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
            } catch (IllegalArgumentException ex) {
                throw new PublishException("create_pr", "REPO_URL_INVALID", "Invalid repository URL: " + repoUrl);
            }
        }
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        String[] parts = path.split("/");
        if (parts.length < 2) {
            throw new PublishException("create_pr", "REPO_URL_INVALID", "Repository URL must include owner/repo: " + repoUrl);
        }
        return new RepoCoordinates(parts[0], parts[1]);
    }

    private CommandResult runGitOrThrow(RunEntity run, String opName, List<String> command, String step) {
        CommandResult result = runGit(run, opName, command);
        if (result.exitCode() == 0) {
            return result;
        }
        throw new PublishException(
                step,
                "PUBLISH_STEP_FAILED",
                step + " failed with exit code " + result.exitCode()
                        + "; stdout_log=" + result.stdoutPath()
                        + "; stderr_log=" + result.stderrPath()
                        + "; stderr=" + truncate(result.stderr(), 2000)
        );
    }

    private CommandResult runGit(RunEntity run, String opName, List<String> command) {
        Path operationRoot = resolvePublishOperationRoot(run);
        String suffix = opName + "-" + System.nanoTime();
        Path stdoutPath = operationRoot.resolve(suffix + ".stdout.log");
        Path stderrPath = operationRoot.resolve(suffix + ".stderr.log");
        try {
            ProcessExecutionPort.ProcessExecutionResult result = processExecutionPort.execute(
                    new ProcessExecutionPort.ProcessExecutionRequest(
                            run.getId(),
                            command,
                            resolveProjectRoot(run),
                            Math.max(10, settingsService.getAiTimeoutSeconds()),
                            stdoutPath,
                            stderrPath,
                            true
                    )
            );
            return new CommandResult(
                    result.exitCode(),
                    result.stdout(),
                    result.stderr(),
                    result.stdoutPath(),
                    result.stderrPath()
            );
        } catch (IOException ex) {
            throw new PublishException("publish", "GIT_COMMAND_IO_FAILED", "Failed to execute git command: " + ex.getMessage());
        }
    }

    private String authenticatedRepoUrl(String repoUrl, String username, String passwordOrPat) {
        String trimmedRepo = trimToNull(repoUrl);
        String token = trimToNull(passwordOrPat);
        if (trimmedRepo == null || token == null) {
            return repoUrl;
        }
        try {
            URI uri = URI.create(trimmedRepo);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                return trimmedRepo;
            }
            String user = trimToNull(username);
            if (user == null) {
                user = DEFAULT_PUSH_USER;
            }
            String userInfo = user + ":" + token;
            return new URI(uri.getScheme(), userInfo, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment())
                    .toString();
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return trimmedRepo;
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private RunEntity getRun(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Run not found: " + runId));
    }

    private Project resolveProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
    }

    private Path resolveRunWorkspaceRoot(RunEntity run) {
        String storedRoot = trimToNull(run.getWorkspaceRoot());
        if (storedRoot == null) {
            return Path.of(settingsService.getWorkspaceRoot(), run.getId().toString()).toAbsolutePath().normalize();
        }
        return Path.of(storedRoot).toAbsolutePath().normalize();
    }

    private Path resolveProjectRoot(RunEntity run) {
        return resolveRunWorkspaceRoot(run);
    }

    private Path resolvePublishOperationRoot(RunEntity run) {
        return resolveRunWorkspaceRoot(run).resolve(".hgsdlc").resolve(".runtime").resolve("publish").toAbsolutePath().normalize();
    }

    private boolean isGitWorkspace(RunEntity run) {
        Path root = resolveProjectRoot(run);
        CommandResult result = runGit(
                run,
                "workspace_probe",
                List.of("git", "-C", root.toString(), "rev-parse", "--is-inside-work-tree")
        );
        return result.exitCode() == 0;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record CommandResult(
            int exitCode,
            String stdout,
            String stderr,
            String stdoutPath,
            String stderrPath
    ) {}

    private record RepoCoordinates(String owner, String repo) {}

    private record PrResult(String url, Integer number) {}

    private static class PublishException extends RuntimeException {
        private final String step;
        private final String errorCode;

        private PublishException(String step, String errorCode, String message) {
            super(message);
            this.step = step;
            this.errorCode = errorCode;
        }

        private String step() {
            return step;
        }

        private String errorCode() {
            return errorCode;
        }
    }
}

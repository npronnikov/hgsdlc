package ru.hgd.sdlc.settings.application;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.common.ValidationException;

@Service
public class CatalogGitService {

    /**
     * Resolves the local mirror path and syncs it with the remote repository.
     *
     * @param repoUrl       remote catalog repository URL
     * @param branch        branch to sync
     * @param workspaceRoot workspace root (obtained from SettingsService by the caller)
     * @return local mirror path, ready for scanning
     */
    public Path syncAndGetMirrorPath(String repoUrl, String branch, String workspaceRoot)
            throws IOException, InterruptedException {
        Path mirrorPath = resolveCatalogMirrorPath(workspaceRoot, repoUrl);
        syncMirror(repoUrl, branch, mirrorPath);
        return mirrorPath;
    }

    Path resolveCatalogMirrorPath(String workspaceRoot, String repoUrl) {
        Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        String suffix = Integer.toHexString(repoUrl.toLowerCase(Locale.ROOT).hashCode());
        return root.resolve(".catalog-repo").resolve(suffix);
    }

    private void syncMirror(String repoUrl, String branch, Path mirrorPath) throws IOException, InterruptedException {
        try {
            syncMirrorInternal(repoUrl, branch, mirrorPath);
            return;
        } catch (ValidationException ex) {
            if (!isGitAuthorizationError(ex.getMessage())) {
                throw ex;
            }
        }
        String anonymousRepoUrl = toAnonymousRepoUrl(repoUrl);
        if (anonymousRepoUrl.equals(repoUrl)) {
            throw new ValidationException("Git authorization failed for repository: " + repoUrl);
        }
        syncMirrorInternal(anonymousRepoUrl, branch, mirrorPath);
    }

    private void syncMirrorInternal(String repoUrl, String branch, Path mirrorPath) throws IOException, InterruptedException {
        Files.createDirectories(mirrorPath.getParent());
        if (!Files.isDirectory(mirrorPath.resolve(".git"))) {
            runCommand(
                    List.of("git", "clone", "--branch", branch, "--single-branch", repoUrl, mirrorPath.toString()),
                    null,
                    Duration.ofMinutes(5)
            );
        } else {
            runCommand(List.of("git", "-C", mirrorPath.toString(), "remote", "set-url", "origin", repoUrl), null, Duration.ofMinutes(1));
            runCommand(List.of("git", "-C", mirrorPath.toString(), "fetch", "--prune", "--tags", "origin"), null, Duration.ofMinutes(5));
        }
        runCommand(List.of("git", "-C", mirrorPath.toString(), "reset", "--hard"), null, Duration.ofMinutes(1));
        runCommand(List.of("git", "-C", mirrorPath.toString(), "clean", "-fd"), null, Duration.ofMinutes(1));
        runCommand(List.of("git", "-C", mirrorPath.toString(), "checkout", "-B", branch, "origin/" + branch), null, Duration.ofMinutes(1));
        runCommand(List.of("git", "-C", mirrorPath.toString(), "reset", "--hard", "origin/" + branch), null, Duration.ofMinutes(1));
        runCommand(List.of("git", "-C", mirrorPath.toString(), "clean", "-fd"), null, Duration.ofMinutes(1));
    }

    private boolean isGitAuthorizationError(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("401")
                || normalized.contains("unauthorized")
                || normalized.contains("authentication failed")
                || normalized.contains("access denied");
    }

    private String toAnonymousRepoUrl(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return "";
        }
        try {
            URI parsed = URI.create(repoUrl.trim());
            if (!"http".equalsIgnoreCase(parsed.getScheme()) && !"https".equalsIgnoreCase(parsed.getScheme())) {
                return repoUrl;
            }
            if (parsed.getUserInfo() == null || parsed.getUserInfo().isBlank()) {
                return repoUrl;
            }
            return new URI(
                    parsed.getScheme(),
                    null,
                    parsed.getHost(),
                    parsed.getPort(),
                    parsed.getPath(),
                    parsed.getQuery(),
                    parsed.getFragment()
            ).toString();
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return repoUrl;
        }
    }

    private void runCommand(List<String> command, Path workdir, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workdir != null) {
            builder.directory(workdir.toFile());
        }
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] outputBytes = process.getInputStream().readAllBytes();
        boolean finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        String output = new String(outputBytes, StandardCharsets.UTF_8);
        if (!finished) {
            process.destroyForcibly();
            throw new ValidationException("Command timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new ValidationException("Command failed (" + process.exitValue() + "): " + String.join(" ", command)
                    + ". Output: " + truncate(output, 2000));
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }
}

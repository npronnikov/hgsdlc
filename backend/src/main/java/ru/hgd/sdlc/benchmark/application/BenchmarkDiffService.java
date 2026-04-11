package ru.hgd.sdlc.benchmark.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.runtime.application.port.ProcessExecutionPort;
import ru.hgd.sdlc.runtime.application.port.WorkspacePort;
import ru.hgd.sdlc.runtime.domain.RunEntity;

@Service
public class BenchmarkDiffService {
    private static final long MAX_CONTENT_BYTES = 256 * 1024;

    private final ProcessExecutionPort processExecutionPort;
    private final WorkspacePort workspacePort;

    public BenchmarkDiffService(ProcessExecutionPort processExecutionPort, WorkspacePort workspacePort) {
        this.processExecutionPort = processExecutionPort;
        this.workspacePort = workspacePort;
    }

    public String computeGitDiff(RunEntity run) {
        Path projectRoot = resolveProjectRoot(run);
        if (!workspacePort.exists(projectRoot.resolve(".git"))) {
            return "";
        }
        Path logsDir = projectRoot.resolve(".hgsdlc").resolve("logs");
        try {
            workspacePort.createDirectories(logsDir);
        } catch (IOException ex) {
            // ignore
        }
        String suffix = String.valueOf(System.nanoTime());
        Path stdoutPath = logsDir.resolve("benchmark-diff-" + suffix + ".stdout.log");
        Path stderrPath = logsDir.resolve("benchmark-diff-" + suffix + ".stderr.log");
        try {
            String base = (run.getTargetBranch() != null && !run.getTargetBranch().isBlank())
                    ? run.getTargetBranch().trim() : "main";
            StringBuilder diff = new StringBuilder();

            appendSection(
                    diff,
                    "Committed changes (" + base + "...HEAD)",
                    runGit(
                            run,
                            projectRoot,
                            List.of("git", "diff", base + "...HEAD", "--unified=10"),
                            stdoutPath,
                            stderrPath
                    )
            );
            appendSection(
                    diff,
                    "Staged but uncommitted changes",
                    runGit(
                            run,
                            projectRoot,
                            List.of("git", "diff", "--cached", "--unified=10"),
                            stdoutPath,
                            stderrPath
                    )
            );
            appendSection(
                    diff,
                    "Unstaged changes",
                    runGit(
                            run,
                            projectRoot,
                            List.of("git", "diff", "--unified=10"),
                            stdoutPath,
                            stderrPath
                    )
            );

            ProcessExecutionPort.ProcessExecutionResult untracked = runGit(
                    run,
                    projectRoot,
                    List.of("git", "ls-files", "--others", "--exclude-standard"),
                    stdoutPath,
                    stderrPath
            );
            String untrackedFiles = trimToEmpty(untracked.stdout());
            if (!untrackedFiles.isBlank()) {
                if (!diff.isEmpty()) {
                    diff.append("\n");
                }
                diff.append("=== Untracked files ===\n");
                diff.append(untrackedFiles.strip()).append("\n");
            }

            return diff.toString();
        } catch (IOException ex) {
            return "";
        }
    }

    public String computeDiffOfDiffs(String diffA, String diffB) {
        if (diffA == null) diffA = "";
        if (diffB == null) diffB = "";

        List<String> linesA = splitLines(diffA);
        List<String> linesB = splitLines(diffB);

        List<String> onlyInA = new ArrayList<>();
        List<String> onlyInB = new ArrayList<>();

        for (String line : linesA) {
            if (line.startsWith("+") && !line.startsWith("+++") && !linesB.contains(line)) {
                onlyInA.add(line);
            }
        }
        for (String line : linesB) {
            if (line.startsWith("+") && !line.startsWith("+++") && !linesA.contains(line)) {
                onlyInB.add(line);
            }
        }

        StringBuilder sb = new StringBuilder();
        if (!onlyInA.isEmpty()) {
            sb.append("=== Only in Run A (with skill/rule) ===\n");
            for (String line : onlyInA) {
                sb.append(line).append("\n");
            }
        }
        if (!onlyInB.isEmpty()) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append("=== Only in Run B (control) ===\n");
            for (String line : onlyInB) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    public List<FileComparisonEntry> compareRunFiles(RunEntity runA, RunEntity runB) {
        RunFilesSnapshot snapshotA = collectRunFilesSnapshot(runA);
        RunFilesSnapshot snapshotB = collectRunFilesSnapshot(runB);
        Set<String> allPaths = new LinkedHashSet<>();
        allPaths.addAll(snapshotA.paths());
        allPaths.addAll(snapshotB.paths());

        List<FileComparisonEntry> entries = new ArrayList<>();
        for (String path : allPaths) {
            FileContentEntry contentA = readFileContent(snapshotA.projectRoot(), path);
            FileContentEntry contentB = readFileContent(snapshotB.projectRoot(), path);
            entries.add(new FileComparisonEntry(
                    path,
                    snapshotA.statusByPath().getOrDefault(path, "unchanged"),
                    snapshotB.statusByPath().getOrDefault(path, "unchanged"),
                    contentA.content(),
                    contentB.content(),
                    contentA.exists(),
                    contentB.exists(),
                    contentA.binary(),
                    contentB.binary(),
                    contentA.truncated(),
                    contentB.truncated(),
                    contentA.sizeBytes(),
                    contentB.sizeBytes()
            ));
        }
        return entries;
    }

    private Path resolveProjectRoot(RunEntity run) {
        String storedRoot = run.getWorkspaceRoot();
        if (storedRoot != null && !storedRoot.isBlank()) {
            return Path.of(storedRoot).toAbsolutePath().normalize();
        }
        return Path.of(run.getId().toString()).toAbsolutePath().normalize();
    }

    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(text.split("\n"));
    }

    private RunFilesSnapshot collectRunFilesSnapshot(RunEntity run) {
        Path projectRoot = resolveProjectRoot(run);
        if (!workspacePort.exists(projectRoot.resolve(".git"))) {
            return new RunFilesSnapshot(projectRoot, Set.of(), Map.of());
        }
        Path logsDir = projectRoot.resolve(".hgsdlc").resolve("logs");
        try {
            workspacePort.createDirectories(logsDir);
        } catch (IOException ex) {
            // ignore
        }
        String suffix = String.valueOf(System.nanoTime());
        Path stdoutPath = logsDir.resolve("benchmark-files-" + suffix + ".stdout.log");
        Path stderrPath = logsDir.resolve("benchmark-files-" + suffix + ".stderr.log");

        Map<String, String> statusByPath = parseStatusByPath(
                trimToEmpty(runGitSafe(
                        run,
                        projectRoot,
                        List.of("git", "status", "--porcelain", "--untracked-files=all"),
                        stdoutPath,
                        stderrPath
                ))
        );

        String base = (run.getTargetBranch() != null && !run.getTargetBranch().isBlank())
                ? run.getTargetBranch().trim() : "main";
        String committedRaw = runGitSafe(
                run,
                projectRoot,
                List.of("git", "diff", "--name-only", base + "...HEAD"),
                stdoutPath,
                stderrPath
        );
        Set<String> committedPaths = parseNameOnlyPaths(committedRaw);

        Set<String> paths = new LinkedHashSet<>();
        paths.addAll(statusByPath.keySet());
        paths.addAll(committedPaths);
        for (String committedPath : committedPaths) {
            statusByPath.putIfAbsent(committedPath, "committed");
        }
        return new RunFilesSnapshot(projectRoot, paths, statusByPath);
    }

    private FileContentEntry readFileContent(Path projectRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return new FileContentEntry("", false, false, false, 0L);
        }
        Path resolved = projectRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(projectRoot) || !workspacePort.exists(resolved) || workspacePort.isDirectory(resolved)) {
            return new FileContentEntry("", false, false, false, 0L);
        }
        try {
            long sizeBytes = workspacePort.size(resolved);
            boolean truncated = sizeBytes > MAX_CONTENT_BYTES;
            byte[] bytes = workspacePort.readAllBytes(resolved);
            boolean binary = isBinary(bytes);
            if (binary) {
                return new FileContentEntry("", true, true, false, sizeBytes);
            }
            String content = truncated
                    ? new String(bytes, 0, (int) MAX_CONTENT_BYTES, StandardCharsets.UTF_8)
                    : new String(bytes, StandardCharsets.UTF_8);
            if (truncated) {
                content = content + "\n\n[truncated: file is larger than " + MAX_CONTENT_BYTES + " bytes]";
            }
            return new FileContentEntry(content, true, false, truncated, sizeBytes);
        } catch (IOException ex) {
            return new FileContentEntry("", true, false, false, 0L);
        }
    }

    private boolean isBinary(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        int sample = Math.min(bytes.length, 8192);
        for (int i = 0; i < sample; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private String runGitSafe(
            RunEntity run,
            Path projectRoot,
            List<String> command,
            Path stdoutPath,
            Path stderrPath
    ) {
        try {
            ProcessExecutionPort.ProcessExecutionResult result = runGit(run, projectRoot, command, stdoutPath, stderrPath);
            if (result.exitCode() != 0) {
                return "";
            }
            return trimToEmpty(result.stdout());
        } catch (IOException ex) {
            return "";
        }
    }

    private Map<String, String> parseStatusByPath(String rawStatus) {
        Map<String, String> statusByPath = new LinkedHashMap<>();
        if (rawStatus == null || rawStatus.isBlank()) {
            return statusByPath;
        }
        for (String line : rawStatus.split("\n")) {
            if (line == null || line.isBlank() || line.length() < 4) {
                continue;
            }
            String path = line.substring(3).trim();
            if (path.contains(" -> ")) {
                String[] parts = path.split(" -> ");
                path = parts[parts.length - 1].trim();
            }
            if (path.isBlank()) {
                continue;
            }
            String code = line.substring(0, 2).trim();
            statusByPath.put(path, statusFromCode(code));
        }
        return statusByPath;
    }

    private Set<String> parseNameOnlyPaths(String raw) {
        Set<String> paths = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return paths;
        }
        for (String line : raw.split("\n")) {
            String path = line == null ? "" : line.trim();
            if (!path.isBlank()) {
                paths.add(path);
            }
        }
        return paths;
    }

    private String statusFromCode(String code) {
        if (code == null || code.isBlank()) {
            return "modified";
        }
        if ("??".equals(code)) {
            return "untracked";
        }
        String normalized = code.toUpperCase(Locale.ROOT);
        if (normalized.contains("A")) {
            return "added";
        }
        if (normalized.contains("D")) {
            return "deleted";
        }
        if (normalized.contains("R")) {
            return "renamed";
        }
        if (normalized.contains("M")) {
            return "modified";
        }
        return code.toLowerCase(Locale.ROOT);
    }

    private ProcessExecutionPort.ProcessExecutionResult runGit(
            RunEntity run,
            Path projectRoot,
            List<String> command,
            Path stdoutPath,
            Path stderrPath
    ) throws IOException {
        return processExecutionPort.execute(
                new ProcessExecutionPort.ProcessExecutionRequest(
                        run.getId(),
                        command,
                        projectRoot,
                        60,
                        stdoutPath,
                        stderrPath,
                        false
                )
        );
    }

    private void appendSection(
            StringBuilder out,
            String title,
            ProcessExecutionPort.ProcessExecutionResult result
    ) {
        if (result == null || result.exitCode() != 0) {
            return;
        }
        String body = trimToEmpty(result.stdout());
        if (body.isBlank()) {
            return;
        }
        if (!out.isEmpty()) {
            out.append("\n");
        }
        out.append("=== ").append(title).append(" ===\n");
        out.append(body.strip()).append("\n");
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record RunFilesSnapshot(
            Path projectRoot,
            Set<String> paths,
            Map<String, String> statusByPath
    ) {}

    private record FileContentEntry(
            String content,
            boolean exists,
            boolean binary,
            boolean truncated,
            long sizeBytes
    ) {}

    public record FileComparisonEntry(
            String path,
            String statusA,
            String statusB,
            String contentA,
            String contentB,
            boolean existsA,
            boolean existsB,
            boolean binaryA,
            boolean binaryB,
            boolean truncatedA,
            boolean truncatedB,
            long sizeBytesA,
            long sizeBytesB
    ) {}
}

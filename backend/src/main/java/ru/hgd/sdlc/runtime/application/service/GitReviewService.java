package ru.hgd.sdlc.runtime.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.ForbiddenException;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.runtime.application.dto.GateChangesResult;
import ru.hgd.sdlc.runtime.application.dto.GateDiffResult;
import ru.hgd.sdlc.runtime.application.dto.GitChangeEntry;
import ru.hgd.sdlc.runtime.application.port.ProcessExecutionPort;
import ru.hgd.sdlc.runtime.application.port.WorkspacePort;
import ru.hgd.sdlc.runtime.domain.GateInstanceEntity;
import ru.hgd.sdlc.runtime.domain.GateKind;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.infrastructure.GateInstanceRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;
import ru.hgd.sdlc.settings.application.SettingsService;

@Service
public class GitReviewService {
    private final RunRepository runRepository;
    private final GateInstanceRepository gateInstanceRepository;
    private final ObjectMapper objectMapper;
    private final SettingsService settingsService;
    private final ProcessExecutionPort processExecutionPort;
    private final WorkspacePort workspacePort;

    public GitReviewService(
            RunRepository runRepository,
            GateInstanceRepository gateInstanceRepository,
            ObjectMapper objectMapper,
            SettingsService settingsService,
            ProcessExecutionPort processExecutionPort,
            WorkspacePort workspacePort
    ) {
        this.runRepository = runRepository;
        this.gateInstanceRepository = gateInstanceRepository;
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.processExecutionPort = processExecutionPort;
        this.workspacePort = workspacePort;
    }

    @Transactional(readOnly = true)
    public GateChangesResult collectGateChanges(UUID gateId, User user) {
        GateInstanceEntity gate = getGateEntity(gateId);
        RunEntity run = getRunEntity(gate.getRunId());
        FlowModel flowModel = parseFlowSnapshot(run);
        NodeModel node = requireNode(flowModel, gate.getNodeId());
        enforceGateRole(node, user);
        List<GitChangeEntry> changes = listGitChanges(run);
        String statusLabel = gate.getGateKind() == GateKind.HUMAN_INPUT ? "Awaiting input" : "Ready for review";
        int added = changes.stream().mapToInt(GitChangeEntry::added).sum();
        int removed = changes.stream().mapToInt(GitChangeEntry::removed).sum();
        return new GateChangesResult(
                gate.getId(),
                run.getId(),
                gate.getGateKind().name().toLowerCase(Locale.ROOT),
                gate.getStatus().name().toLowerCase(Locale.ROOT),
                statusLabel,
                changes,
                changes.size(),
                added,
                removed
        );
    }

    @Transactional(readOnly = true)
    public GateDiffResult buildGateDiff(UUID gateId, String path, User user) {
        GateInstanceEntity gate = getGateEntity(gateId);
        RunEntity run = getRunEntity(gate.getRunId());
        FlowModel flowModel = parseFlowSnapshot(run);
        NodeModel node = requireNode(flowModel, gate.getNodeId());
        enforceGateRole(node, user);
        String sanitizedPath = sanitizeGitPath(path);
        CommandResult diffResult = runGitQuery(run, List.of("git", "diff", "--no-color", "--", sanitizedPath));
        if (diffResult.exitCode() != 0) {
            throw new ValidationException("Failed to read git diff for path: " + sanitizedPath);
        }
        String patch = diffResult.stdout();
        if (patch == null || patch.isBlank()) {
            CommandResult untrackedDiff = runGitQuery(
                    run,
                    List.of("git", "diff", "--no-color", "--no-index", "--", "/dev/null", sanitizedPath)
            );
            if (untrackedDiff.exitCode() == 0 || untrackedDiff.exitCode() == 1) {
                patch = untrackedDiff.stdout();
            } else {
                throw new ValidationException("Failed to read git diff for path: " + sanitizedPath);
            }
        }
        String originalContent = readHeadFileContent(run, sanitizedPath);
        String modifiedContent = readCurrentFileContent(run, sanitizedPath);
        return new GateDiffResult(
                gate.getId(),
                run.getId(),
                sanitizedPath,
                patch == null ? "" : patch,
                originalContent,
                modifiedContent
        );
    }

    private List<GitChangeEntry> listGitChanges(RunEntity run) {
        CommandResult statusResult = runGitQuery(run, List.of("git", "status", "--porcelain", "--untracked-files=all"));
        if (statusResult.exitCode() != 0) {
            throw new ValidationException("Failed to read git status for run: " + run.getId());
        }
        List<String> changedPaths = expandGitPaths(run, parseGitStatusPaths(statusResult.stdout()));
        CommandResult numstatResult = runGitQuery(run, List.of("git", "diff", "--numstat"));
        if (numstatResult.exitCode() != 0) {
            throw new ValidationException("Failed to read git numstat for run: " + run.getId());
        }
        Map<String, GitNumstat> numstatByPath = parseGitNumstat(numstatResult.stdout());
        List<GitChangeEntry> result = new ArrayList<>();
        for (String path : changedPaths) {
            String status = inferGitStatus(path, statusResult.stdout());
            GitNumstat stat = numstatByPath.get(path);
            if (stat == null && "untracked".equals(status)) {
                stat = readUntrackedNumstat(run, path);
            }
            int added = stat == null ? 0 : stat.added();
            int removed = stat == null ? 0 : stat.removed();
            boolean binary = stat != null && stat.binary();
            result.add(new GitChangeEntry(path, status, added, removed, binary));
        }
        return result;
    }

    private GitNumstat readUntrackedNumstat(RunEntity run, String path) {
        CommandResult result = runGitQuery(run, List.of(
                "git", "diff", "--numstat", "--no-index", "--", "/dev/null", path
        ));
        if (result.exitCode() != 0 && result.exitCode() != 1) {
            return null;
        }
        Map<String, GitNumstat> parsed = parseGitNumstat(result.stdout());
        if (parsed.containsKey(path)) {
            return parsed.get(path);
        }
        if (!parsed.isEmpty()) {
            return parsed.values().iterator().next();
        }
        return null;
    }

    private List<String> expandGitPaths(RunEntity run, List<String> rawPaths) {
        if (rawPaths == null || rawPaths.isEmpty()) {
            return List.of();
        }
        Path projectRoot = resolveProjectRoot(run);
        List<String> expanded = new ArrayList<>();
        for (String raw : rawPaths) {
            String path = trimToNull(raw);
            if (path == null) {
                continue;
            }
            Path resolved = projectRoot.resolve(path).normalize();
            if (!resolved.startsWith(projectRoot)) {
                continue;
            }
            if (workspacePort.isDirectory(resolved)) {
                try {
                    List<Path> files = workspacePort.listRegularFilesRecursively(resolved);
                    for (Path file : files) {
                        expanded.add(projectRoot.relativize(file).toString().replace('\\', '/'));
                    }
                } catch (IOException ex) {
                    expanded.add(path.replace('\\', '/'));
                }
            } else {
                expanded.add(path.replace('\\', '/'));
            }
        }
        return expanded.stream().distinct().toList();
    }

    private CommandResult runGitQuery(RunEntity run, List<String> command) {
        Path operationRoot = resolveRunScopeRoot(resolveRunWorkspaceRoot(run)).resolve(".runtime").resolve("gate-review");
        String suffix = String.valueOf(System.nanoTime());
        Path stdoutPath = operationRoot.resolve("git-" + suffix + ".stdout.log");
        Path stderrPath = operationRoot.resolve("git-" + suffix + ".stderr.log");
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
            throw new ValidationException("Failed to execute git command for run: " + run.getId());
        }
    }

    private String readHeadFileContent(RunEntity run, String path) {
        CommandResult existsResult = runGitQuery(run, List.of("git", "cat-file", "-e", "HEAD:" + path));
        if (existsResult.exitCode() != 0) {
            return "";
        }
        CommandResult showResult = runGitQuery(run, List.of("git", "show", "HEAD:" + path));
        if (showResult.exitCode() != 0) {
            throw new ValidationException("Failed to read base file content for path: " + path);
        }
        return showResult.stdout() == null ? "" : showResult.stdout();
    }

    private String readCurrentFileContent(RunEntity run, String path) {
        Path projectRoot = resolveProjectRoot(run);
        Path resolved = projectRoot.resolve(path).normalize();
        if (!resolved.startsWith(projectRoot) || !workspacePort.exists(resolved) || workspacePort.isDirectory(resolved)) {
            return "";
        }
        try {
            return workspacePort.readString(resolved, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ValidationException("Failed to read current file content for path: " + path);
        }
    }

    private List<String> parseGitStatusPaths(String rawStatus) {
        List<String> paths = new ArrayList<>();
        if (rawStatus == null || rawStatus.isBlank()) {
            return paths;
        }
        for (String line : rawStatus.split("\n")) {
            if (line == null || line.isBlank() || line.length() < 4) {
                continue;
            }
            String pathPart = line.substring(3).trim();
            if (pathPart.contains(" -> ")) {
                String[] parts = pathPart.split(" -> ");
                pathPart = parts[parts.length - 1].trim();
            }
            if (!pathPart.isBlank()) {
                paths.add(pathPart);
            }
        }
        return paths.stream().distinct().toList();
    }

    private Map<String, GitNumstat> parseGitNumstat(String rawNumstat) {
        Map<String, GitNumstat> stats = new LinkedHashMap<>();
        if (rawNumstat == null || rawNumstat.isBlank()) {
            return stats;
        }
        for (String line : rawNumstat.split("\n")) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t");
            if (parts.length < 3) {
                continue;
            }
            String addedRaw = parts[0].trim();
            String removedRaw = parts[1].trim();
            String path = normalizeNumstatPath(parts[2].trim());
            boolean binary = "-".equals(addedRaw) || "-".equals(removedRaw);
            int added = binary ? 0 : parseIntSafe(addedRaw);
            int removed = binary ? 0 : parseIntSafe(removedRaw);
            stats.put(path, new GitNumstat(added, removed, binary));
        }
        return stats;
    }

    private String normalizeNumstatPath(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.contains(" => ")) {
            String[] parts = path.split(" => ");
            path = parts[parts.length - 1].trim();
        }
        if (path.startsWith("b/")) {
            path = path.substring(2);
        }
        return path;
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String inferGitStatus(String path, String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return "modified";
        }
        for (String line : rawStatus.split("\n")) {
            if (line == null || line.isBlank() || line.length() < 4) {
                continue;
            }
            String candidatePath = line.substring(3).trim();
            if (candidatePath.contains(" -> ")) {
                String[] parts = candidatePath.split(" -> ");
                candidatePath = parts[parts.length - 1].trim();
            }
            if (!path.equals(candidatePath)) {
                continue;
            }
            String code = line.substring(0, 2).trim();
            if (code.isBlank()) {
                return "modified";
            }
            if ("??".equals(code)) {
                return "untracked";
            }
            if (code.contains("A")) {
                return "added";
            }
            if (code.contains("D")) {
                return "deleted";
            }
            if (code.contains("R")) {
                return "renamed";
            }
            if (code.contains("M")) {
                return "modified";
            }
            return code.toLowerCase(Locale.ROOT);
        }
        return "modified";
    }

    private String sanitizeGitPath(String path) {
        String value = trimToNull(path);
        if (value == null) {
            throw new ValidationException("path is required");
        }
        if (Path.of(value).isAbsolute()) {
            throw new ValidationException("absolute path is forbidden");
        }
        Path normalized = Path.of(value).normalize();
        if (normalized.startsWith("..")) {
            throw new ValidationException("path escapes repository root");
        }
        return normalized.toString().replace('\\', '/');
    }

    private FlowModel parseFlowSnapshot(RunEntity run) {
        try {
            return objectMapper.readValue(run.getFlowSnapshotJson(), FlowModel.class);
        } catch (JsonProcessingException ex) {
            throw new ValidationException("Invalid run flow_snapshot_json");
        }
    }

    private NodeModel requireNode(FlowModel flowModel, String nodeId) {
        if (flowModel.getNodes() == null) {
            throw new ValidationException("Flow nodes are empty");
        }
        for (NodeModel node : flowModel.getNodes()) {
            if (nodeId != null && nodeId.equals(node.getId())) {
                return node;
            }
        }
        throw new ValidationException("Node not found in flow: " + nodeId);
    }

    private void enforceGateRole(NodeModel node, User user) {
        List<String> allowedRoles = node.getAllowedRoles() == null ? List.of() : node.getAllowedRoles();
        if (allowedRoles.isEmpty()) {
            return;
        }
        if (user == null || !user.hasAnyRoleName(allowedRoles)) {
            throw new ForbiddenException("Actor role is not allowed for this gate");
        }
    }

    private GateInstanceEntity getGateEntity(UUID gateId) {
        return gateInstanceRepository.findById(gateId)
                .orElseThrow(() -> new NotFoundException("Gate not found: " + gateId));
    }

    private RunEntity getRunEntity(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Run not found: " + runId));
    }

    private Path resolveRunWorkspaceRoot(String workspaceRoot, UUID runId) {
        return Path.of(workspaceRoot).resolve(runId.toString()).toAbsolutePath().normalize();
    }

    private Path resolveRunWorkspaceRoot(RunEntity run) {
        String storedRoot = trimToNull(run.getWorkspaceRoot());
        if (storedRoot != null) {
            return Path.of(storedRoot).toAbsolutePath().normalize();
        }
        return resolveRunWorkspaceRoot(settingsService.getWorkspaceRoot(), run.getId());
    }

    private Path resolveProjectRoot(RunEntity run) {
        return resolveRunWorkspaceRoot(run);
    }

    private Path resolveRunScopeRoot(Path runWorkspaceRoot) {
        return runWorkspaceRoot.resolve(".hgsdlc").toAbsolutePath().normalize();
    }

    private void createDirectories(Path path) {
        try {
            workspacePort.createDirectories(path);
        } catch (IOException ex) {
            throw new ValidationException("Failed to create directories: " + path);
        }
    }

    private String readFile(Path path) throws IOException {
        if (!workspacePort.exists(path)) {
            return "";
        }
        return workspacePort.readString(path, StandardCharsets.UTF_8);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record GitNumstat(
            int added,
            int removed,
            boolean binary
    ) {}

    private record CommandResult(
            int exitCode,
            String stdout,
            String stderr,
            String stdoutPath,
            String stderrPath
    ) {}
}

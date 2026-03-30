package ru.hgd.sdlc.skill.application;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.skill.api.SkillSaveRequest;
import ru.hgd.sdlc.skill.domain.SkillFileRole;

@Service
public class SkillPackageService {
    public static final String CONTENT_FORMAT_PACKAGE = "package";
    private static final int DEFAULT_MAX_FILES = 6;
    private static final long DEFAULT_MAX_FILE_BYTES = 300L * 1024L;
    private static final long DEFAULT_MAX_PACKAGE_BYTES = DEFAULT_MAX_FILES * DEFAULT_MAX_FILE_BYTES;
    private static final String SKILL_ENTRYPOINT = "SKILL.md";

    public PreparedPackage prepareForSave(List<SkillSaveRequest.SkillFileSaveRequest> requestFiles) {
        if (requestFiles == null || requestFiles.isEmpty()) {
            throw new ValidationException("files are required");
        }
        if (requestFiles.size() > DEFAULT_MAX_FILES) {
            throw new ValidationException("files limit exceeded: " + DEFAULT_MAX_FILES);
        }

        List<PreparedSkillFile> files = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        long totalBytes = 0L;
        int skillMdCount = 0;

        for (SkillSaveRequest.SkillFileSaveRequest requestFile : requestFiles) {
            if (requestFile == null) {
                throw new ValidationException("file item is required");
            }
            String path = normalizePath(requestFile.path());
            if (!paths.add(path)) {
                throw new ValidationException("duplicate file path: " + path);
            }
            SkillFileRole role = inferRole(path);
            if (SKILL_ENTRYPOINT.equals(path)) {
                skillMdCount += 1;
            }

            boolean executable = Boolean.TRUE.equals(requestFile.executable());
            if (executable && role != SkillFileRole.SCRIPT) {
                throw new ValidationException("is_executable is allowed only for scripts/*");
            }

            String textContent = normalizeText(requestFile.textContent());
            long sizeBytes = textContent.getBytes(StandardCharsets.UTF_8).length;
            if (sizeBytes > DEFAULT_MAX_FILE_BYTES) {
                throw new ValidationException("file too large: " + path);
            }
            totalBytes += sizeBytes;
            if (totalBytes > DEFAULT_MAX_PACKAGE_BYTES) {
                throw new ValidationException("package size limit exceeded");
            }
            files.add(new PreparedSkillFile(
                    path,
                    role,
                    inferMediaType(path),
                    executable,
                    textContent,
                    sizeBytes
            ));
        }

        if (skillMdCount != 1) {
            throw new ValidationException("package must contain exactly one SKILL.md in root");
        }
        String packageChecksum = computePackageChecksum(files);
        return new PreparedPackage(files, packageChecksum);
    }

    public String computePackageChecksum(List<PreparedSkillFile> files) {
        List<PreparedSkillFile> sorted = files.stream()
                .sorted(Comparator.comparing(PreparedSkillFile::path))
                .toList();
        StringBuilder canonical = new StringBuilder();
        for (PreparedSkillFile file : sorted) {
            String fileChecksum = ChecksumUtil.sha256(normalizeText(file.textContent()));
            canonical.append(file.path())
                    .append('\0')
                    .append(fileChecksum)
                    .append('\n');
        }
        return ChecksumUtil.sha256(canonical.toString());
    }

    public String normalizeText(String value) {
        String safe = value == null ? "" : value;
        return safe.replace("\r\n", "\n").replace('\r', '\n');
    }

    public String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new ValidationException("file path is required");
        }
        if (rawPath.indexOf('\0') >= 0) {
            throw new ValidationException("file path contains null byte");
        }
        String path = rawPath.trim();
        if (path.startsWith("/")) {
            throw new ValidationException("absolute paths are forbidden: " + path);
        }
        if (path.contains("\\")) {
            throw new ValidationException("backslashes are forbidden in path: " + path);
        }

        String[] parts = path.split("/");
        List<String> normalizedParts = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                throw new ValidationException("invalid path segment in: " + path);
            }
            if (".".equals(part) || "..".equals(part)) {
                throw new ValidationException("path traversal is forbidden: " + path);
            }
            if (part.startsWith(".git") || part.startsWith(".svn")) {
                throw new ValidationException("vcs paths are forbidden: " + path);
            }
            for (int i = 0; i < part.length(); i += 1) {
                if (Character.isISOControl(part.charAt(i))) {
                    throw new ValidationException("control characters are forbidden in path: " + path);
                }
            }
            normalizedParts.add(part);
        }
        return String.join("/", normalizedParts);
    }

    public SkillFileRole inferRole(String path) {
        if (SKILL_ENTRYPOINT.equals(path)) {
            return SkillFileRole.INSTRUCTION;
        }
        if (path.startsWith("scripts/")) {
            return SkillFileRole.SCRIPT;
        }
        if (path.startsWith("templates/")) {
            return SkillFileRole.TEMPLATE;
        }
        if (path.startsWith("assets/")) {
            return SkillFileRole.ASSET;
        }
        throw new ValidationException("unsupported package path: " + path);
    }

    public String inferMediaType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            return "text/yaml";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".sh")) {
            return "text/x-shellscript";
        }
        if (lower.endsWith(".xml")) {
            return "application/xml";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain";
        }
        return "text/plain";
    }

    public record PreparedSkillFile(
            String path,
            SkillFileRole role,
            String mediaType,
            boolean executable,
            String textContent,
            long sizeBytes
    ) {
    }

    public record PreparedPackage(List<PreparedSkillFile> files, String packageChecksum) {
    }
}

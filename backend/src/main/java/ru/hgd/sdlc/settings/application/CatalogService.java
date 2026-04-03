package ru.hgd.sdlc.settings.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.skill.application.SkillPackageService;

@Service
public class CatalogService {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final SettingsService settingsService;
    private final CatalogGitService catalogGitService;
    private final CatalogUpsertService catalogUpsertService;
    private final SkillPackageService skillPackageService;
    private final JdbcTemplate jdbcTemplate;
    private final ReentrantLock repairLock = new ReentrantLock();

    public CatalogService(
            SettingsService settingsService,
            CatalogGitService catalogGitService,
            CatalogUpsertService catalogUpsertService,
            SkillPackageService skillPackageService,
            JdbcTemplate jdbcTemplate
    ) {
        this.settingsService = settingsService;
        this.catalogGitService = catalogGitService;
        this.catalogUpsertService = catalogUpsertService;
        this.skillPackageService = skillPackageService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public RepairResult repairCatalog(String actorId, String modeRaw) {
        Instant startedAt = Instant.now();
        String requestedBy = actorId == null || actorId.isBlank() ? "system" : actorId;
        RepairMode mode = RepairMode.from(modeRaw);
        if (!repairLock.tryLock()) {
            return RepairResult.running("Repair is already running", startedAt, requestedBy, mode);
        }
        try {
            return doRepair(startedAt, requestedBy, mode);
        } finally {
            repairLock.unlock();
        }
    }

    private RepairResult doRepair(Instant startedAt, String requestedBy, RepairMode mode) {
        List<RepairError> errors = new ArrayList<>();

        String repoUrl = settingsService.getCatalogRepoUrl().trim();
        String branch = settingsService.getCatalogDefaultBranch();
        if (repoUrl.isBlank()) {
            throw new ValidationException("catalog_repo_url is required before repair");
        }

        Path mirrorRoot;
        try {
            mirrorRoot = catalogGitService.syncAndGetMirrorPath(repoUrl, branch, settingsService.getWorkspaceRoot());
        } catch (Exception ex) {
            return RepairResult.failed(
                    "Catalog sync failed: " + ex.getMessage(),
                    startedAt, Instant.now(), requestedBy, errors, mode
            );
        }

        if (mode == RepairMode.FULL_REPAIR) {
            try {
                purgeCatalogIndex();
            } catch (Exception ex) {
                return RepairResult.failed(
                        "Catalog cleanup failed: " + ex.getMessage(),
                        startedAt, Instant.now(), requestedBy, errors, mode
                );
            }
        }

        int scannedRules = 0;
        int scannedSkills = 0;
        int scannedFlows = 0;
        int inserted = 0;
        int updated = 0;
        int skipped = 0;

        List<Path> metadataPaths = scanMetadataFiles(mirrorRoot);
        metadataPaths.sort(Comparator.naturalOrder());
        for (Path metadataPath : metadataPaths) {
            try {
                ParsedMetadata metadata = parseMetadata(mirrorRoot, metadataPath);
                switch (metadata.entityType()) {
                    case "rule" -> {
                        scannedRules++;
                        UpsertOutcome outcome = catalogUpsertService.upsertRule(metadata, requestedBy);
                        inserted += outcome.inserted();
                        updated += outcome.updated();
                        skipped += outcome.skipped();
                    }
                    case "skill" -> {
                        scannedSkills++;
                        UpsertOutcome outcome = catalogUpsertService.upsertSkill(metadata, requestedBy);
                        inserted += outcome.inserted();
                        updated += outcome.updated();
                        skipped += outcome.skipped();
                    }
                    case "flow" -> {
                        scannedFlows++;
                        UpsertOutcome outcome = catalogUpsertService.upsertFlow(metadata, requestedBy);
                        inserted += outcome.inserted();
                        updated += outcome.updated();
                        skipped += outcome.skipped();
                    }
                    default -> errors.add(new RepairError(
                            relativizeOrAbsolute(mirrorRoot, metadataPath),
                            "Unsupported entity_type: " + metadata.entityType()
                    ));
                }
            } catch (Exception ex) {
                errors.add(new RepairError(relativizeOrAbsolute(mirrorRoot, metadataPath), ex.getMessage()));
            }
        }

        Instant finishedAt = Instant.now();
        String status = errors.isEmpty() ? "completed" : "completed_with_errors";
        String message = "Repair finished. scanned=" + metadataPaths.size()
                + ", inserted=" + inserted + ", updated=" + updated
                + ", skipped=" + skipped + ", errors=" + errors.size();
        return new RepairResult(status, message, startedAt, finishedAt, mode.apiValue(), requestedBy,
                scannedRules, scannedSkills, scannedFlows, inserted, updated, skipped, errors);
    }

    // ---- Scan & parse ----

    private List<Path> scanMetadataFiles(Path mirrorRoot) {
        List<Path> result = new ArrayList<>();
        for (String entityType : List.of("rules", "skills", "flows")) {
            Path root = mirrorRoot.resolve(entityType);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.walk(root)) {
                stream.filter(path -> path.getFileName().toString().equals("metadata.yaml")).forEach(result::add);
            } catch (IOException ex) {
                throw new ValidationException("Failed to scan " + entityType + ": " + ex.getMessage());
            }
        }
        return result;
    }

    private ParsedMetadata parseMetadata(Path mirrorRoot, Path metadataPath) {
        Map<String, Object> metadata = parseYamlMap(readText(metadataPath), "metadata.yaml is not valid: " + metadataPath);
        Path versionDir = metadataPath.getParent();
        if (versionDir == null || versionDir.getParent() == null || versionDir.getParent().getParent() == null) {
            throw new ValidationException("Invalid catalog path: " + metadataPath);
        }
        String folderEntity = versionDir.getParent().getParent().getFileName().toString();
        String entityType = stringValue(metadata.get("entity_type"));
        if (entityType == null || entityType.isBlank()) {
            throw new ValidationException("metadata field is required: entity_type");
        }
        String id = requireString(metadata, "id");
        String version = requireString(metadata, "version");
        String canonicalName = requireString(metadata, "canonical_name");
        if (!canonicalName.equals(id + "@" + version)) {
            throw new ValidationException("canonical_name mismatch: expected " + id + "@" + version + ", got " + canonicalName);
        }
        if (!entityTypeMatchesFolder(entityType, folderEntity)) {
            throw new ValidationException("entity_type does not match path: " + entityType + " vs " + folderEntity);
        }

        String normalizedEntityType = entityType.toLowerCase(Locale.ROOT);
        String content;
        String metadataChecksum;
        List<ParsedSkillPackageFile> packageFiles = List.of();
        if ("skill".equals(normalizedEntityType)) {
            packageFiles = parseSkillPackageFiles(versionDir);
            String metadataChecksumRaw = normalizeChecksum(stringValue(metadata.get("checksum")));
            if (metadataChecksumRaw == null) {
                throw new ValidationException("metadata checksum is required");
            }
            List<SkillPackageService.PreparedSkillFile> preparedFiles = packageFiles.stream()
                    .map(file -> new SkillPackageService.PreparedSkillFile(
                            file.path(), file.role(), file.mediaType(),
                            file.executable(), file.content(), file.sizeBytes()
                    ))
                    .toList();
            String actualChecksum = skillPackageService.computePackageChecksum(preparedFiles);
            String legacySkillMdChecksum = packageFiles.stream()
                    .filter(file -> "SKILL.md".equals(file.path()))
                    .findFirst()
                    .map(file -> ChecksumUtil.sha256(skillPackageService.normalizeText(file.content())))
                    .orElse(null);
            boolean packageChecksumMatches = metadataChecksumRaw.equals(actualChecksum);
            boolean legacyChecksumMatches = legacySkillMdChecksum != null && metadataChecksumRaw.equals(legacySkillMdChecksum);
            if (!packageChecksumMatches && !legacyChecksumMatches) {
                throw new ValidationException("checksum mismatch for package " + canonicalName);
            }
            metadataChecksum = actualChecksum;
            content = packageFiles.stream()
                    .filter(file -> "SKILL.md".equals(file.path()))
                    .findFirst()
                    .map(ParsedSkillPackageFile::content)
                    .orElseThrow(() -> new ValidationException("SKILL.md is required in skill package"));
        } else {
            String contentFileName = switch (normalizedEntityType) {
                case "rule" -> "RULE.md";
                case "flow" -> "FLOW.yaml";
                default -> throw new ValidationException("Unsupported entity_type: " + entityType);
            };
            Path contentPath = versionDir.resolve(contentFileName);
            if (!Files.exists(contentPath)) {
                throw new ValidationException("Missing content file: " + contentFileName);
            }
            content = readText(contentPath);
            metadataChecksum = normalizeChecksum(stringValue(metadata.get("checksum")));
            if (metadataChecksum == null) {
                throw new ValidationException("metadata checksum is required");
            }
            String actualChecksum = ChecksumUtil.sha256(content);
            if (!metadataChecksum.equals(actualChecksum)) {
                throw new ValidationException("checksum mismatch for " + contentFileName);
            }
        }

        String displayName = stringValue(metadata.get("display_name"));
        if (displayName == null || displayName.isBlank()) {
            displayName = stringValue(metadata.get("title"));
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = id;
        }
        String sourcePath = stringValue(metadata.get("source_path"));
        if (sourcePath == null || sourcePath.isBlank()) {
            sourcePath = relativizeOrAbsolute(mirrorRoot, versionDir);
        }
        return new ParsedMetadata(
                entityType.toLowerCase(Locale.ROOT),
                id, version, canonicalName, displayName, sourcePath,
                content, metadataChecksum, packageFiles, metadata
        );
    }

    private List<ParsedSkillPackageFile> parseSkillPackageFiles(Path versionDir) {
        List<ParsedSkillPackageFile> out = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        try (var stream = Files.walk(versionDir)) {
            List<Path> filePaths = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !"metadata.yaml".equals(path.getFileName().toString()))
                    .sorted()
                    .toList();
            for (Path filePath : filePaths) {
                String path = skillPackageService.normalizePath(versionDir.relativize(filePath).toString().replace('\\', '/'));
                if (paths.contains(path)) {
                    throw new ValidationException("duplicate skill package path: " + path);
                }
                paths.add(path);
                var role = skillPackageService.inferRole(path);
                boolean executable = path.startsWith("scripts/") && Files.isExecutable(filePath);
                String mediaType = skillPackageService.inferMediaType(path);
                String content = readText(filePath);
                long sizeBytes;
                try {
                    sizeBytes = Files.size(filePath);
                } catch (IOException ex) {
                    throw new ValidationException("Failed to stat skill package file: " + path);
                }
                out.add(new ParsedSkillPackageFile(path, role, mediaType, executable, content, sizeBytes));
            }
        } catch (IOException ex) {
            throw new ValidationException("Failed to scan skill package files: " + ex.getMessage());
        }
        if (out.isEmpty()) {
            throw new ValidationException("skill package must contain files");
        }
        return out;
    }

    private boolean entityTypeMatchesFolder(String entityType, String folderEntity) {
        if (entityType == null || folderEntity == null) {
            return false;
        }
        String normalizedEntity = entityType.trim().toLowerCase(Locale.ROOT);
        String normalizedFolder = folderEntity.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedEntity) {
            case "rule" -> normalizedFolder.equals("rules");
            case "skill" -> normalizedFolder.equals("skills");
            case "flow" -> normalizedFolder.equals("flows");
            default -> false;
        };
    }

    // ---- DB cleanup ----

    private void purgeCatalogIndex() {
        if (tryExecuteCleanup("TRUNCATE TABLE flows, skills, rules CASCADE")) {
            return;
        }
        if (tryExecuteCleanup("TRUNCATE TABLE flows")
                && tryExecuteCleanup("TRUNCATE TABLE skills")
                && tryExecuteCleanup("TRUNCATE TABLE rules")) {
            return;
        }
        jdbcTemplate.execute("DELETE FROM flows");
        jdbcTemplate.execute("DELETE FROM skills");
        jdbcTemplate.execute("DELETE FROM rules");
    }

    private boolean tryExecuteCleanup(String sql) {
        try {
            jdbcTemplate.execute(sql);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    // ---- Helpers ----

    private String requireString(Map<String, Object> map, String key) {
        String value = stringValue(map.get(key));
        if (value == null || value.isBlank()) {
            throw new ValidationException("metadata field is required: " + key);
        }
        return value.trim();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private Map<String, Object> parseYamlMap(String yaml, String errorMessage) {
        try {
            return YAML.readValue(yaml, MAP_TYPE);
        } catch (IOException ex) {
            throw new ValidationException(errorMessage + ": " + ex.getMessage());
        }
    }

    private String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ValidationException("Failed to read file: " + path + " (" + ex.getMessage() + ")");
        }
    }

    private String normalizeChecksum(String rawChecksum) {
        if (rawChecksum == null || rawChecksum.isBlank()) {
            return null;
        }
        String value = rawChecksum.trim();
        if (value.startsWith("sha256:")) {
            value = value.substring("sha256:".length());
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private String relativizeOrAbsolute(Path root, Path path) {
        try {
            return root.relativize(path).toString().replace('\\', '/');
        } catch (Exception ex) {
            return path.toAbsolutePath().normalize().toString();
        }
    }

    // ---- Public types (previously nested in SettingsService) ----

    public record RepairResult(
            String status,
            String message,
            Instant startedAt,
            Instant finishedAt,
            String mode,
            String requestedBy,
            int scannedRules,
            int scannedSkills,
            int scannedFlows,
            int inserted,
            int updated,
            int skipped,
            List<RepairError> errors
    ) {
        public static RepairResult failed(
                String message,
                Instant startedAt,
                Instant finishedAt,
                String requestedBy,
                List<RepairError> errors,
                RepairMode mode
        ) {
            return new RepairResult(
                    "failed", message, startedAt, finishedAt, mode.apiValue(), requestedBy,
                    0, 0, 0, 0, 0, 0,
                    errors == null ? List.of() : List.copyOf(errors)
            );
        }

        public static RepairResult running(
                String message,
                Instant startedAt,
                String requestedBy,
                RepairMode mode
        ) {
            return new RepairResult(
                    "running", message, startedAt, startedAt, mode.apiValue(), requestedBy,
                    0, 0, 0, 0, 0, 0, List.of()
            );
        }
    }

    public record RepairError(String path, String message) {}

    public enum RepairMode {
        PULL_REMOTE("pull_remote"),
        FULL_REPAIR("full_repair");

        private final String apiValue;

        RepairMode(String apiValue) {
            this.apiValue = apiValue;
        }

        public String apiValue() {
            return apiValue;
        }

        public static RepairMode from(String raw) {
            if (raw == null || raw.isBlank()) {
                return PULL_REMOTE;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "pull_remote", "pull-remote", "pull", "upsert" -> PULL_REMOTE;
                case "full_repair", "full-repair", "from_scratch", "from-scratch", "scratch", "reset" -> FULL_REPAIR;
                default -> throw new ValidationException("repair mode must be pull_remote or full_repair");
            };
        }
    }
}

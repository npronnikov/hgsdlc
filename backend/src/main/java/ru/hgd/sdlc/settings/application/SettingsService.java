package ru.hgd.sdlc.settings.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.common.JsonSchemaValidator;
import ru.hgd.sdlc.common.MarkdownFrontmatterParser;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.flow.application.FlowYamlParser;
import ru.hgd.sdlc.flow.domain.FlowStatus;
import ru.hgd.sdlc.flow.domain.FlowVersion;
import ru.hgd.sdlc.flow.domain.FlowEnvironment;
import ru.hgd.sdlc.flow.domain.FlowApprovalStatus;
import ru.hgd.sdlc.flow.domain.FlowContentSource;
import ru.hgd.sdlc.flow.domain.FlowVisibility;
import ru.hgd.sdlc.flow.domain.FlowLifecycleStatus;
import ru.hgd.sdlc.flow.infrastructure.FlowVersionRepository;
import ru.hgd.sdlc.rule.domain.RuleApprovalStatus;
import ru.hgd.sdlc.rule.domain.RuleContentSource;
import ru.hgd.sdlc.rule.domain.RuleEnvironment;
import ru.hgd.sdlc.rule.domain.RuleLifecycleStatus;
import ru.hgd.sdlc.rule.domain.RuleProvider;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.domain.RuleVisibility;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;
import ru.hgd.sdlc.settings.domain.SystemSetting;
import ru.hgd.sdlc.settings.infrastructure.SystemSettingRepository;
import ru.hgd.sdlc.skill.domain.SkillApprovalStatus;
import ru.hgd.sdlc.skill.domain.SkillContentSource;
import ru.hgd.sdlc.skill.domain.SkillEnvironment;
import ru.hgd.sdlc.skill.domain.SkillLifecycleStatus;
import ru.hgd.sdlc.skill.domain.SkillProvider;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.domain.SkillVisibility;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

@Service
public class SettingsService {
    public static final String WORKSPACE_ROOT_KEY = "runtime.workspace_root";
    public static final String CODING_AGENT_KEY = "runtime.coding_agent";
    public static final String AI_TIMEOUT_SECONDS_KEY = "runtime.ai_timeout_seconds";
    public static final String CATALOG_REPO_URL_KEY = "catalog.repo_url";
    public static final String CATALOG_DEFAULT_BRANCH_KEY = "catalog.default_branch";
    public static final String CATALOG_PUBLISH_MODE_KEY = "catalog.publish_mode";
    public static final String CATALOG_GIT_SSH_PRIVATE_KEY = "catalog.git.ssh_private_key";
    public static final String CATALOG_GIT_SSH_PUBLIC_KEY = "catalog.git.ssh_public_key";
    public static final String CATALOG_GIT_SSH_PASSPHRASE = "catalog.git.ssh_passphrase";
    public static final String CATALOG_GIT_CERTIFICATE = "catalog.git.certificate";
    public static final String CATALOG_GIT_CERTIFICATE_KEY = "catalog.git.certificate_key";
    public static final String CATALOG_GIT_USERNAME_KEY = "catalog.git.username";
    public static final String CATALOG_GIT_PASSWORD_KEY = "catalog.git.password_or_pat";
    public static final String CATALOG_LOCAL_GIT_USERNAME_KEY = "catalog.local_git.username";
    public static final String CATALOG_LOCAL_GIT_EMAIL_KEY = "catalog.local_git.email";
    public static final String DEFAULT_LOCAL_GIT_USERNAME = "hgsdlc";
    public static final String DEFAULT_LOCAL_GIT_EMAIL = "hgsdlc@sdlc.com";
    private static final String DEFAULT_WORKSPACE_ROOT = "/tmp/workspace";
    private static final String DEFAULT_CODING_AGENT = "qwen";
    private static final int DEFAULT_AI_TIMEOUT_SECONDS = 900;
    private static final String DEFAULT_CATALOG_REPO_URL = "https://github.com/npronnikov/catalog.git";
    private static final String DEFAULT_CATALOG_DEFAULT_BRANCH = "main";
    private static final String DEFAULT_CATALOG_PUBLISH_MODE = "pr";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final SystemSettingRepository repository;
    private final RuleVersionRepository ruleVersionRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final FlowYamlParser flowYamlParser;
    private final JsonSchemaValidator schemaValidator;
    private final MarkdownFrontmatterParser frontmatterParser;
    private final JdbcTemplate jdbcTemplate;
    private final ReentrantLock repairLock = new ReentrantLock();

    public SettingsService(
            SystemSettingRepository repository,
            RuleVersionRepository ruleVersionRepository,
            SkillVersionRepository skillVersionRepository,
            FlowVersionRepository flowVersionRepository,
            FlowYamlParser flowYamlParser,
            JsonSchemaValidator schemaValidator,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.ruleVersionRepository = ruleVersionRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.flowVersionRepository = flowVersionRepository;
        this.flowYamlParser = flowYamlParser;
        this.schemaValidator = schemaValidator;
        this.frontmatterParser = new MarkdownFrontmatterParser();
        this.jdbcTemplate = jdbcTemplate;
    }

    public String getWorkspaceRoot() {
        return repository.findById(WORKSPACE_ROOT_KEY)
                .map(SystemSetting::getSettingValue)
                .filter((value) -> !value.isBlank())
                .orElse(DEFAULT_WORKSPACE_ROOT);
    }

    public Optional<SystemSetting> getWorkspaceRootSetting() {
        return repository.findById(WORKSPACE_ROOT_KEY);
    }

    public String getRuntimeCodingAgent() {
        return repository.findById(CODING_AGENT_KEY)
                .map(SystemSetting::getSettingValue)
                .filter((value) -> !value.isBlank())
                .map(this::normalizeCodingAgent)
                .orElse(DEFAULT_CODING_AGENT);
    }

    public Optional<SystemSetting> getRuntimeCodingAgentSetting() {
        return repository.findById(CODING_AGENT_KEY);
    }

    public int getAiTimeoutSeconds() {
        return repository.findById(AI_TIMEOUT_SECONDS_KEY)
                .map(SystemSetting::getSettingValue)
                .filter((value) -> !value.isBlank())
                .map(Integer::parseInt)
                .orElse(DEFAULT_AI_TIMEOUT_SECONDS);
    }

    public String getCatalogRepoUrl() {
        return repository.findById(CATALOG_REPO_URL_KEY)
                .map(SystemSetting::getSettingValue)
                .filter((value) -> !value.isBlank())
                .orElse(DEFAULT_CATALOG_REPO_URL);
    }

    public Optional<SystemSetting> getAiTimeoutSecondsSetting() {
        return repository.findById(AI_TIMEOUT_SECONDS_KEY);
    }

    public SystemSetting updateWorkspaceRoot(String workspaceRoot, String actorId) {
        String normalized = normalizeWorkspaceRoot(workspaceRoot);
        SystemSetting setting = repository.findById(WORKSPACE_ROOT_KEY)
                .orElseGet(() -> SystemSetting.builder().settingKey(WORKSPACE_ROOT_KEY).build());
        setting.setSettingValue(normalized);
        setting.setUpdatedAt(Instant.now());
        setting.setUpdatedBy(actorId == null || actorId.isBlank() ? "system" : actorId);
        return repository.save(setting);
    }

    public SystemSetting updateRuntimeCodingAgent(String codingAgent, String actorId) {
        String normalized = normalizeCodingAgent(codingAgent);
        SystemSetting setting = repository.findById(CODING_AGENT_KEY)
                .orElseGet(() -> SystemSetting.builder().settingKey(CODING_AGENT_KEY).build());
        setting.setSettingValue(normalized);
        setting.setUpdatedAt(Instant.now());
        setting.setUpdatedBy(actorId == null || actorId.isBlank() ? "system" : actorId);
        return repository.save(setting);
    }

    public SystemSetting updateAiTimeoutSeconds(int aiTimeoutSeconds, String actorId) {
        validateAiTimeoutSeconds(aiTimeoutSeconds);
        SystemSetting setting = repository.findById(AI_TIMEOUT_SECONDS_KEY)
                .orElseGet(() -> SystemSetting.builder().settingKey(AI_TIMEOUT_SECONDS_KEY).build());
        setting.setSettingValue(String.valueOf(aiTimeoutSeconds));
        setting.setUpdatedAt(Instant.now());
        setting.setUpdatedBy(actorId == null || actorId.isBlank() ? "system" : actorId);
        return repository.save(setting);
    }

    @Transactional
    public RuntimeSettings getRuntimeSettings() {
        Optional<SystemSetting> workspaceSetting = getWorkspaceRootSetting();
        Optional<SystemSetting> codingAgentSetting = getRuntimeCodingAgentSetting();
        Optional<SystemSetting> aiTimeoutSetting = getAiTimeoutSecondsSetting();
        Optional<SystemSetting> catalogRepoUrl = repository.findById(CATALOG_REPO_URL_KEY);
        Optional<SystemSetting> catalogBranch = repository.findById(CATALOG_DEFAULT_BRANCH_KEY);
        Optional<SystemSetting> publishMode = repository.findById(CATALOG_PUBLISH_MODE_KEY);
        Optional<SystemSetting> sshPrivate = repository.findById(CATALOG_GIT_SSH_PRIVATE_KEY);
        Optional<SystemSetting> sshPublic = repository.findById(CATALOG_GIT_SSH_PUBLIC_KEY);
        Optional<SystemSetting> sshPass = repository.findById(CATALOG_GIT_SSH_PASSPHRASE);
        Optional<SystemSetting> cert = repository.findById(CATALOG_GIT_CERTIFICATE);
        Optional<SystemSetting> certKey = repository.findById(CATALOG_GIT_CERTIFICATE_KEY);
        Optional<SystemSetting> gitUser = repository.findById(CATALOG_GIT_USERNAME_KEY);
        Optional<SystemSetting> gitPassword = repository.findById(CATALOG_GIT_PASSWORD_KEY);
        Optional<SystemSetting> localGitUser = repository.findById(CATALOG_LOCAL_GIT_USERNAME_KEY);
        Optional<SystemSetting> localGitEmail = repository.findById(CATALOG_LOCAL_GIT_EMAIL_KEY);
        SystemSetting latestSetting = latestOf(
                workspaceSetting.orElse(null),
                codingAgentSetting.orElse(null),
                aiTimeoutSetting.orElse(null),
                catalogRepoUrl.orElse(null),
                catalogBranch.orElse(null),
                publishMode.orElse(null),
                sshPrivate.orElse(null),
                sshPublic.orElse(null),
                sshPass.orElse(null),
                cert.orElse(null),
                certKey.orElse(null),
                gitUser.orElse(null),
                gitPassword.orElse(null),
                localGitUser.orElse(null),
                localGitEmail.orElse(null)
        );
        return new RuntimeSettings(
                workspaceSetting.map(SystemSetting::getSettingValue).orElse(getWorkspaceRoot()),
                codingAgentSetting.map(SystemSetting::getSettingValue).orElse(getRuntimeCodingAgent()),
                aiTimeoutSetting.map(SystemSetting::getSettingValue).map(Integer::parseInt).orElse(DEFAULT_AI_TIMEOUT_SECONDS),
                catalogRepoUrl.map(SystemSetting::getSettingValue).filter((value) -> !value.isBlank()).orElse(DEFAULT_CATALOG_REPO_URL),
                catalogBranch.map(SystemSetting::getSettingValue).orElse(DEFAULT_CATALOG_DEFAULT_BRANCH),
                publishMode.map(SystemSetting::getSettingValue).orElse(DEFAULT_CATALOG_PUBLISH_MODE),
                sshPrivate.map(SystemSetting::getSettingValue).orElse(""),
                sshPublic.map(SystemSetting::getSettingValue).orElse(""),
                sshPass.map(SystemSetting::getSettingValue).orElse(""),
                cert.map(SystemSetting::getSettingValue).orElse(""),
                certKey.map(SystemSetting::getSettingValue).orElse(""),
                gitUser.map(SystemSetting::getSettingValue).orElse(""),
                gitPassword.map(SystemSetting::getSettingValue).orElse(""),
                resolveSettingValue(localGitUser, DEFAULT_LOCAL_GIT_USERNAME),
                resolveSettingValue(localGitEmail, DEFAULT_LOCAL_GIT_EMAIL),
                latestSetting == null ? null : latestSetting.getUpdatedAt(),
                latestSetting == null ? null : latestSetting.getUpdatedBy()
        );
    }

    @Transactional
    public RuntimeSettings updateRuntimeSettings(String workspaceRoot, String codingAgent, int aiTimeoutSeconds, String actorId) {
        SystemSetting workspaceSetting = updateWorkspaceRoot(workspaceRoot, actorId);
        SystemSetting codingAgentSetting = updateRuntimeCodingAgent(codingAgent, actorId);
        SystemSetting aiTimeoutSetting = updateAiTimeoutSeconds(aiTimeoutSeconds, actorId);
        SystemSetting latestSetting = latestOf(workspaceSetting, codingAgentSetting, aiTimeoutSetting);
        return new RuntimeSettings(
                workspaceSetting.getSettingValue(),
                codingAgentSetting.getSettingValue(),
                Integer.parseInt(aiTimeoutSetting.getSettingValue()),
                repository.findById(CATALOG_REPO_URL_KEY).map(SystemSetting::getSettingValue).filter((value) -> !value.isBlank()).orElse(DEFAULT_CATALOG_REPO_URL),
                repository.findById(CATALOG_DEFAULT_BRANCH_KEY).map(SystemSetting::getSettingValue).orElse(DEFAULT_CATALOG_DEFAULT_BRANCH),
                repository.findById(CATALOG_PUBLISH_MODE_KEY).map(SystemSetting::getSettingValue).orElse(DEFAULT_CATALOG_PUBLISH_MODE),
                repository.findById(CATALOG_GIT_SSH_PRIVATE_KEY).map(SystemSetting::getSettingValue).orElse(""),
                repository.findById(CATALOG_GIT_SSH_PUBLIC_KEY).map(SystemSetting::getSettingValue).orElse(""),
                repository.findById(CATALOG_GIT_SSH_PASSPHRASE).map(SystemSetting::getSettingValue).orElse(""),
                repository.findById(CATALOG_GIT_CERTIFICATE).map(SystemSetting::getSettingValue).orElse(""),
                repository.findById(CATALOG_GIT_CERTIFICATE_KEY).map(SystemSetting::getSettingValue).orElse(""),
                repository.findById(CATALOG_GIT_USERNAME_KEY).map(SystemSetting::getSettingValue).orElse(""),
                repository.findById(CATALOG_GIT_PASSWORD_KEY).map(SystemSetting::getSettingValue).orElse(""),
                resolveSettingValue(repository.findById(CATALOG_LOCAL_GIT_USERNAME_KEY), DEFAULT_LOCAL_GIT_USERNAME),
                resolveSettingValue(repository.findById(CATALOG_LOCAL_GIT_EMAIL_KEY), DEFAULT_LOCAL_GIT_EMAIL),
                latestSetting.getUpdatedAt(),
                latestSetting.getUpdatedBy()
        );
    }

    @Transactional
    public RuntimeSettings updateCatalogSettings(
            String catalogRepoUrl,
            String catalogDefaultBranch,
            String publishMode,
            String gitSshPrivateKey,
            String gitSshPublicKey,
            String gitSshPassphrase,
            String gitCertificate,
            String gitCertificateKey,
            String gitUsername,
            String gitPasswordOrPat,
            String localGitUsername,
            String localGitEmail,
            String actorId
    ) {
        SystemSetting repo = upsert(CATALOG_REPO_URL_KEY, catalogRepoUrl == null ? "" : catalogRepoUrl.trim(), actorId);
        SystemSetting branch = upsert(CATALOG_DEFAULT_BRANCH_KEY, normalizeBranch(catalogDefaultBranch), actorId);
        SystemSetting mode = upsert(CATALOG_PUBLISH_MODE_KEY, normalizePublishMode(publishMode), actorId);
        SystemSetting sshPr = upsert(CATALOG_GIT_SSH_PRIVATE_KEY, gitSshPrivateKey == null ? "" : gitSshPrivateKey, actorId);
        SystemSetting sshPb = upsert(CATALOG_GIT_SSH_PUBLIC_KEY, gitSshPublicKey == null ? "" : gitSshPublicKey, actorId);
        SystemSetting sshPs = upsert(CATALOG_GIT_SSH_PASSPHRASE, gitSshPassphrase == null ? "" : gitSshPassphrase, actorId);
        SystemSetting cert = upsert(CATALOG_GIT_CERTIFICATE, gitCertificate == null ? "" : gitCertificate, actorId);
        SystemSetting certKey = upsert(CATALOG_GIT_CERTIFICATE_KEY, gitCertificateKey == null ? "" : gitCertificateKey, actorId);
        SystemSetting user = upsert(CATALOG_GIT_USERNAME_KEY, gitUsername == null ? "" : gitUsername.trim(), actorId);
        SystemSetting pass = upsert(CATALOG_GIT_PASSWORD_KEY, gitPasswordOrPat == null ? "" : gitPasswordOrPat, actorId);
        SystemSetting localUser = upsert(CATALOG_LOCAL_GIT_USERNAME_KEY, normalizeLocalGitUsername(localGitUsername), actorId);
        SystemSetting localEmail = upsert(CATALOG_LOCAL_GIT_EMAIL_KEY, normalizeLocalGitEmail(localGitEmail), actorId);
        SystemSetting latest = latestOf(repo, branch, mode, sshPr, sshPb, sshPs, cert, certKey, user, pass, localUser, localEmail);
        return new RuntimeSettings(
                getWorkspaceRoot(),
                getRuntimeCodingAgent(),
                getAiTimeoutSeconds(),
                repo.getSettingValue(),
                branch.getSettingValue(),
                mode.getSettingValue(),
                sshPr.getSettingValue(),
                sshPb.getSettingValue(),
                sshPs.getSettingValue(),
                cert.getSettingValue(),
                certKey.getSettingValue(),
                user.getSettingValue(),
                pass.getSettingValue(),
                resolveSettingValue(Optional.of(localUser), DEFAULT_LOCAL_GIT_USERNAME),
                resolveSettingValue(Optional.of(localEmail), DEFAULT_LOCAL_GIT_EMAIL),
                latest.getUpdatedAt(),
                latest.getUpdatedBy()
        );
    }

    @Transactional
    public RepairResult repairCatalog(String actorId, String modeRaw) {
        Instant startedAt = Instant.now();
        String requestedBy = actorId == null || actorId.isBlank() ? "system" : actorId;
        RepairMode mode = RepairMode.from(modeRaw);
        if (!repairLock.tryLock()) {
            return RepairResult.running(
                    "Repair is already running",
                    startedAt,
                    requestedBy,
                    mode
            );
        }
        try {
        List<RepairError> errors = new ArrayList<>();

        String repoUrl = repository.findById(CATALOG_REPO_URL_KEY)
                .map(SystemSetting::getSettingValue)
                .filter((value) -> !value.isBlank())
                .orElse(DEFAULT_CATALOG_REPO_URL)
                .trim();
        String branch = repository.findById(CATALOG_DEFAULT_BRANCH_KEY).map(SystemSetting::getSettingValue).orElse(DEFAULT_CATALOG_DEFAULT_BRANCH);
        if (repoUrl.isBlank()) {
            throw new ValidationException("catalog_repo_url is required before repair");
        }
        if (branch == null || branch.isBlank()) {
            branch = DEFAULT_CATALOG_DEFAULT_BRANCH;
        }
        branch = branch.trim();

        Path mirrorRoot = resolveCatalogMirrorPath(getWorkspaceRoot(), repoUrl);
        try {
            syncMirror(repoUrl, branch, mirrorRoot);
        } catch (Exception ex) {
            return RepairResult.failed(
                    "Catalog sync failed: " + ex.getMessage(),
                    startedAt,
                    Instant.now(),
                    requestedBy,
                    errors,
                    mode
            );
        }

        if (mode == RepairMode.FROM_SCRATCH) {
            try {
                purgeCatalogIndex();
            } catch (Exception ex) {
                return RepairResult.failed(
                        "Catalog cleanup failed: " + ex.getMessage(),
                        startedAt,
                        Instant.now(),
                        requestedBy,
                        errors,
                        mode
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
                        UpsertOutcome outcome = upsertRule(metadata, requestedBy);
                        inserted += outcome.inserted();
                        updated += outcome.updated();
                        skipped += outcome.skipped();
                    }
                    case "skill" -> {
                        scannedSkills++;
                        UpsertOutcome outcome = upsertSkill(metadata, requestedBy);
                        inserted += outcome.inserted();
                        updated += outcome.updated();
                        skipped += outcome.skipped();
                    }
                    case "flow" -> {
                        scannedFlows++;
                        UpsertOutcome outcome = upsertFlow(metadata, requestedBy);
                        inserted += outcome.inserted();
                        updated += outcome.updated();
                        skipped += outcome.skipped();
                    }
                    default -> errors.add(new RepairError(relativizeOrAbsolute(mirrorRoot, metadataPath), "Unsupported entity_type: " + metadata.entityType()));
                }
            } catch (Exception ex) {
                errors.add(new RepairError(relativizeOrAbsolute(mirrorRoot, metadataPath), ex.getMessage()));
            }
        }

        Instant finishedAt = Instant.now();
        String status = errors.isEmpty() ? "completed" : "completed_with_errors";
        String message = "Repair finished. scanned=" + metadataPaths.size() + ", inserted=" + inserted + ", updated=" + updated
                + ", skipped=" + skipped + ", errors=" + errors.size();
        return new RepairResult(
                status,
                message,
                startedAt,
                finishedAt,
                mode.apiValue(),
                requestedBy,
                scannedRules,
                scannedSkills,
                scannedFlows,
                inserted,
                updated,
                skipped,
                errors
        );
        } finally {
            repairLock.unlock();
        }
    }

    private UpsertOutcome upsertRule(ParsedMetadata metadata, String actorId) {
        validateRuleContentAgainstSchema(metadata);
        RuleProvider provider = RuleProvider.from(metadata.require("coding_agent"));
        Optional<RuleVersion> existingOptional = ruleVersionRepository.findFirstByCanonicalName(metadata.canonicalName());
        if (existingOptional.isPresent()) {
            RuleVersion existing = existingOptional.get();
            if (existing.getStatus() == RuleStatus.PUBLISHED) {
                String incomingChecksum = withShaPrefix(metadata.checksum());
                if (incomingChecksum.equals(existing.getChecksum())) {
                    return UpsertOutcome.skippedOne();
                }
                throw new ValidationException("Published rule already exists with different checksum: " + metadata.canonicalName());
            }
            applyRuleFields(existing, metadata, provider, actorId);
            ruleVersionRepository.save(existing);
            return UpsertOutcome.updatedOne();
        }
        RuleVersion created = RuleVersion.builder().id(UUID.randomUUID()).build();
        applyRuleFields(created, metadata, provider, actorId);
        ruleVersionRepository.save(created);
        return UpsertOutcome.insertedOne();
    }

    private void applyRuleFields(RuleVersion target, ParsedMetadata metadata, RuleProvider provider, String actorId) {
        target.setRuleId(metadata.id());
        target.setVersion(metadata.version());
        target.setCanonicalName(metadata.canonicalName());
        target.setStatus(RuleStatus.PUBLISHED);
        target.setTitle(metadata.displayName());
        target.setDescription(metadata.optional("description"));
        target.setCodingAgent(provider);
        target.setRuleMarkdown(metadata.content());
        target.setChecksum(withShaPrefix(metadata.checksum()));
        target.setTeamCode(metadata.optional("team_code"));
        target.setPlatformCode(metadata.optional("platform_code"));
        target.setTags(metadata.tags());
        target.setRuleKind(metadata.optional("rule_kind"));
        target.setScope(parseCatalogScope(metadata.optional("scope"), metadata.id()));
        target.setEnvironment(parseRuleEnvironment(metadata.optional("environment")));
        target.setApprovalStatus(RuleApprovalStatus.PUBLISHED);
        target.setApprovedBy(metadata.optional("approved_by"));
        target.setApprovedAt(parseInstant(metadata.optional("approved_at")));
        target.setPublishedAt(parseInstant(metadata.optional("published_at")));
        target.setSourceRef(metadata.optional("source_ref"));
        target.setSourcePath(metadata.sourcePath());
        target.setContentSource(RuleContentSource.GIT);
        target.setForkedFrom(metadata.optional("forked_from"));
        target.setForkedBy(metadata.optional("forked_by"));
        if (target.getVisibility() == null) {
            target.setVisibility(RuleVisibility.INTERNAL);
        }
        target.setLifecycleStatus(parseRuleLifecycle(metadata.optional("lifecycle_status")));
        target.setSavedBy(actorId);
        target.setSavedAt(Instant.now());
    }

    private UpsertOutcome upsertSkill(ParsedMetadata metadata, String actorId) {
        validateSkillContentAgainstSchema(metadata);
        SkillProvider provider = SkillProvider.from(metadata.require("coding_agent"));
        Optional<SkillVersion> existingOptional = skillVersionRepository.findFirstByCanonicalName(metadata.canonicalName());
        if (existingOptional.isPresent()) {
            SkillVersion existing = existingOptional.get();
            if (existing.getStatus() == SkillStatus.PUBLISHED || existing.getApprovalStatus() == SkillApprovalStatus.PUBLISHED) {
                String incomingChecksum = withShaPrefix(metadata.checksum());
                if (incomingChecksum.equals(existing.getChecksum())) {
                    return UpsertOutcome.skippedOne();
                }
                throw new ValidationException("Published skill already exists with different checksum: " + metadata.canonicalName());
            }
            applySkillFields(existing, metadata, provider, actorId);
            skillVersionRepository.save(existing);
            return UpsertOutcome.updatedOne();
        }
        SkillVersion created = SkillVersion.builder()
                .id(UUID.randomUUID())
                .build();
        applySkillFields(created, metadata, provider, actorId);
        skillVersionRepository.save(created);
        return UpsertOutcome.insertedOne();
    }

    private void applySkillFields(SkillVersion target, ParsedMetadata metadata, SkillProvider provider, String actorId) {
        target.setSkillId(metadata.id());
        target.setVersion(metadata.version());
        target.setCanonicalName(metadata.canonicalName());
        target.setStatus(SkillStatus.PUBLISHED);
        target.setName(metadata.displayName());
        target.setDescription(metadata.optional("description") == null ? "" : metadata.optional("description"));
        target.setCodingAgent(provider);
        target.setSkillMarkdown(metadata.content());
        target.setChecksum(withShaPrefix(metadata.checksum()));
        target.setTeamCode(metadata.optional("team_code"));
        target.setPlatformCode(metadata.optional("platform_code"));
        target.setTags(metadata.tags());
        target.setSkillKind(metadata.optional("skill_kind"));
        target.setScope(parseCatalogScope(metadata.optional("scope"), metadata.id()));
        target.setEnvironment(parseSkillEnvironment(metadata.optional("environment")));
        target.setApprovalStatus(SkillApprovalStatus.PUBLISHED);
        target.setApprovedBy(metadata.optional("approved_by"));
        target.setApprovedAt(parseInstant(metadata.optional("approved_at")));
        target.setPublishedAt(parseInstant(metadata.optional("published_at")));
        target.setSourceRef(metadata.optional("source_ref"));
        target.setSourcePath(metadata.sourcePath());
        target.setContentSource(SkillContentSource.GIT);
        target.setForkedFrom(metadata.optional("forked_from"));
        target.setForkedBy(metadata.optional("forked_by"));
        if (target.getVisibility() == null) {
            target.setVisibility(SkillVisibility.INTERNAL);
        }
        target.setLifecycleStatus(parseLifecycle(metadata.optional("lifecycle_status")));
        target.setSavedBy(actorId);
        target.setSavedAt(Instant.now());
    }

    private UpsertOutcome upsertFlow(ParsedMetadata metadata, String actorId) {
        validateFlowContentAgainstSchema(metadata);
        Map<String, Object> flowDoc = parseYamlMap(metadata.content(), "Flow yaml is not valid");
        String startNodeId = stringValue(flowDoc.get("start_node_id"));
        if (startNodeId == null || startNodeId.isBlank()) {
            throw new ValidationException("Flow yaml missing start_node_id");
        }
        List<String> ruleRefs = parseStringList(flowDoc.get("rule_refs"));
        Optional<FlowVersion> existingOptional = flowVersionRepository.findFirstByCanonicalName(metadata.canonicalName());
        if (existingOptional.isPresent()) {
            FlowVersion existing = existingOptional.get();
            if (existing.getStatus() == FlowStatus.PUBLISHED) {
                String incomingChecksum = withShaPrefix(metadata.checksum());
                if (incomingChecksum.equals(existing.getChecksum())) {
                    return UpsertOutcome.skippedOne();
                }
                throw new ValidationException("Published flow already exists with different checksum: " + metadata.canonicalName());
            }
            applyFlowFields(existing, metadata, startNodeId.trim(), ruleRefs, actorId);
            flowVersionRepository.save(existing);
            return UpsertOutcome.updatedOne();
        }
        FlowVersion created = FlowVersion.builder().id(UUID.randomUUID()).build();
        applyFlowFields(created, metadata, startNodeId.trim(), ruleRefs, actorId);
        flowVersionRepository.save(created);
        return UpsertOutcome.insertedOne();
    }

    private void applyFlowFields(FlowVersion target, ParsedMetadata metadata, String startNodeId, List<String> ruleRefs, String actorId) {
        target.setFlowId(metadata.id());
        target.setVersion(metadata.version());
        target.setCanonicalName(metadata.canonicalName());
        target.setStatus(FlowStatus.PUBLISHED);
        target.setTitle(metadata.displayName());
        target.setDescription(metadata.optional("description"));
        target.setStartNodeId(startNodeId);
        target.setRuleRefs(ruleRefs);
        target.setCodingAgent(metadata.require("coding_agent"));
        target.setFlowYaml(metadata.content());
        target.setChecksum(withShaPrefix(metadata.checksum()));
        target.setTeamCode(metadata.optional("team_code"));
        target.setPlatformCode(metadata.optional("platform_code"));
        target.setTags(metadata.tags());
        target.setFlowKind(metadata.optional("flow_kind"));
        target.setRiskLevel(metadata.optional("risk_level"));
        target.setScope(parseCatalogScope(metadata.optional("scope"), metadata.id()));
        target.setEnvironment(parseFlowEnvironment(metadata.optional("environment")));
        target.setApprovalStatus(FlowApprovalStatus.PUBLISHED);
        target.setApprovedBy(metadata.optional("approved_by"));
        target.setApprovedAt(parseInstant(metadata.optional("approved_at")));
        target.setPublishedAt(parseInstant(metadata.optional("published_at")));
        target.setSourceRef(metadata.optional("source_ref"));
        target.setSourcePath(metadata.sourcePath());
        target.setContentSource(FlowContentSource.GIT);
        target.setForkedFrom(metadata.optional("forked_from"));
        target.setForkedBy(metadata.optional("forked_by"));
        if (target.getVisibility() == null) {
            target.setVisibility(FlowVisibility.INTERNAL);
        }
        target.setLifecycleStatus(parseFlowLifecycle(metadata.optional("lifecycle_status")));
        target.setSavedBy(actorId);
        target.setSavedAt(Instant.now());
    }

    private void validateFlowContentAgainstSchema(ParsedMetadata metadata) {
        try {
            flowYamlParser.parse(metadata.content());
        } catch (ValidationException ex) {
            throw new ValidationException("Catalog flow content does not match schema: "
                    + metadata.canonicalName() + ": " + ex.getMessage());
        }
    }

    private void validateRuleContentAgainstSchema(ParsedMetadata metadata) {
        MarkdownFrontmatterParser.ParsedMarkdown parsed;
        try {
            parsed = frontmatterParser.parse(metadata.content());
        } catch (ValidationException ex) {
            throw new ValidationException("Catalog rule markdown is invalid: "
                    + metadata.canonicalName() + ": " + ex.getMessage());
        }
        JsonNode merged = parsed.frontmatter().deepCopy();
        if (!(merged instanceof ObjectNode objectNode)) {
            throw new ValidationException("Catalog rule frontmatter must be a YAML object: " + metadata.canonicalName());
        }
        putIfMissing(objectNode, "id", metadata.id());
        putIfMissing(objectNode, "version", metadata.version());
        putIfMissing(objectNode, "canonical_name", metadata.canonicalName());
        putIfMissing(objectNode, "title", metadata.displayName());
        putIfMissing(objectNode, "description", metadata.optional("description"));
        try {
            schemaValidator.validate(objectNode, "schemas/rule.schema.json");
        } catch (ValidationException ex) {
            throw new ValidationException("Catalog rule content does not match schema: "
                    + metadata.canonicalName() + ": " + ex.getMessage());
        }
    }

    private void validateSkillContentAgainstSchema(ParsedMetadata metadata) {
        MarkdownFrontmatterParser.ParsedMarkdown parsed;
        try {
            parsed = frontmatterParser.parse(metadata.content());
        } catch (ValidationException ex) {
            throw new ValidationException("Catalog skill markdown is invalid: "
                    + metadata.canonicalName() + ": " + ex.getMessage());
        }
        JsonNode merged = parsed.frontmatter().deepCopy();
        if (!(merged instanceof ObjectNode objectNode)) {
            throw new ValidationException("Catalog skill frontmatter must be a YAML object: " + metadata.canonicalName());
        }
        putIfMissing(objectNode, "id", metadata.id());
        putIfMissing(objectNode, "version", metadata.version());
        putIfMissing(objectNode, "canonical_name", metadata.canonicalName());
        putIfMissing(objectNode, "name", metadata.displayName());
        putIfMissing(objectNode, "description", metadata.optional("description"));
        try {
            schemaValidator.validate(objectNode, "schemas/skill.schema.json");
        } catch (ValidationException ex) {
            throw new ValidationException("Catalog skill content does not match schema: "
                    + metadata.canonicalName() + ": " + ex.getMessage());
        }
    }

    private void putIfMissing(ObjectNode node, String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        JsonNode existing = node.get(fieldName);
        if (existing == null || existing.isNull() || (existing.isTextual() && existing.asText().isBlank())) {
            node.put(fieldName, value);
        }
    }

    private SkillEnvironment parseSkillEnvironment(String value) {
        if (value == null || value.isBlank()) {
            return SkillEnvironment.DEV;
        }
        return SkillEnvironment.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private SkillVisibility parseSkillVisibility(String value) {
        if (value == null || value.isBlank()) {
            return SkillVisibility.INTERNAL;
        }
        return SkillVisibility.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private SkillLifecycleStatus parseLifecycle(String value) {
        if (value == null || value.isBlank()) {
            return SkillLifecycleStatus.ACTIVE;
        }
        return SkillLifecycleStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private RuleEnvironment parseRuleEnvironment(String value) {
        if (value == null || value.isBlank()) {
            return RuleEnvironment.DEV;
        }
        return RuleEnvironment.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private RuleVisibility parseRuleVisibility(String value) {
        if (value == null || value.isBlank()) {
            return RuleVisibility.INTERNAL;
        }
        return RuleVisibility.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private RuleLifecycleStatus parseRuleLifecycle(String value) {
        if (value == null || value.isBlank()) {
            return RuleLifecycleStatus.ACTIVE;
        }
        return RuleLifecycleStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private FlowEnvironment parseFlowEnvironment(String value) {
        if (value == null || value.isBlank()) {
            return FlowEnvironment.DEV;
        }
        return FlowEnvironment.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private FlowVisibility parseFlowVisibility(String value) {
        if (value == null || value.isBlank()) {
            return FlowVisibility.INTERNAL;
        }
        return FlowVisibility.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private FlowLifecycleStatus parseFlowLifecycle(String value) {
        if (value == null || value.isBlank()) {
            return FlowLifecycleStatus.ACTIVE;
        }
        return FlowLifecycleStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value.trim());
    }

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

        String contentFileName = switch (entityType.toLowerCase(Locale.ROOT)) {
            case "rule" -> "RULE.md";
            case "skill" -> "SKILL.md";
            case "flow" -> "FLOW.yaml";
            default -> throw new ValidationException("Unsupported entity_type: " + entityType);
        };
        Path contentPath = versionDir.resolve(contentFileName);
        if (!Files.exists(contentPath)) {
            throw new ValidationException("Missing content file: " + contentFileName);
        }
        String content = readText(contentPath);
        String metadataChecksum = normalizeChecksum(stringValue(metadata.get("checksum")));
        if (metadataChecksum == null) {
            throw new ValidationException("metadata checksum is required");
        }
        String actualChecksum = ChecksumUtil.sha256(content);
        if (!metadataChecksum.equals(actualChecksum)) {
            throw new ValidationException("checksum mismatch for " + contentFileName);
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
                id,
                version,
                canonicalName,
                displayName,
                sourcePath,
                content,
                metadataChecksum,
                metadata
        );
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

    private List<String> parseStringList(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        if (rawValue instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                String value = stringValue(item);
                if (value != null && !value.isBlank()) {
                    out.add(value.trim());
                }
            }
            return out;
        }
        String value = stringValue(rawValue);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.trim());
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

    private String withShaPrefix(String checksumWithoutPrefix) {
        return "sha256:" + checksumWithoutPrefix;
    }

    private String parseCatalogScope(String rawScope, String entityId) {
        String normalized = normalizeOptional(rawScope);
        if (normalized != null) {
            String value = normalized.toLowerCase(Locale.ROOT);
            if ("team".equals(value) || "organization".equals(value)) {
                return value;
            }
        }
        if (entityId != null && entityId.startsWith("team-")) {
            return "team";
        }
        return "organization";
    }

    private Path resolveCatalogMirrorPath(String workspaceRoot, String repoUrl) {
        Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        String suffix = Integer.toHexString(repoUrl.toLowerCase(Locale.ROOT).hashCode());
        return root.resolve(".catalog-mirror").resolve(suffix);
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
            try {
                runCommand(List.of("git", "-C", mirrorPath.toString(), "checkout", branch), null, Duration.ofMinutes(1));
            } catch (ValidationException ex) {
                runCommand(List.of("git", "-C", mirrorPath.toString(), "checkout", "-B", branch, "origin/" + branch), null, Duration.ofMinutes(1));
            }
        }
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

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String relativizeOrAbsolute(Path root, Path path) {
        try {
            return root.relativize(path).toString().replace('\\', '/');
        } catch (Exception ex) {
            return path.toAbsolutePath().normalize().toString();
        }
    }

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

    private SystemSetting upsert(String key, String value, String actorId) {
        SystemSetting setting = repository.findById(key).orElseGet(() -> SystemSetting.builder().settingKey(key).build());
        setting.setSettingValue(value == null ? "" : value);
        setting.setUpdatedAt(Instant.now());
        setting.setUpdatedBy(actorId == null || actorId.isBlank() ? "system" : actorId);
        return repository.save(setting);
    }

    private String normalizeBranch(String branch) {
        if (branch == null || branch.isBlank()) {
            return DEFAULT_CATALOG_DEFAULT_BRANCH;
        }
        return branch.trim();
    }

    private String normalizePublishMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return DEFAULT_CATALOG_PUBLISH_MODE;
        }
        String normalized = mode.trim().toLowerCase();
        if (!normalized.equals("local") && !normalized.equals("pr")) {
            throw new ValidationException("publish_mode must be local or pr");
        }
        return normalized;
    }

    private String normalizeLocalGitUsername(String username) {
        if (username == null || username.isBlank()) {
            return DEFAULT_LOCAL_GIT_USERNAME;
        }
        return username.trim();
    }

    private String normalizeLocalGitEmail(String email) {
        if (email == null || email.isBlank()) {
            return DEFAULT_LOCAL_GIT_EMAIL;
        }
        return email.trim();
    }

    private String normalizeWorkspaceRoot(String workspaceRoot) {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            throw new ValidationException("workspace_root is required");
        }
        Path path = Path.of(workspaceRoot.trim()).toAbsolutePath().normalize();
        if (!path.isAbsolute()) {
            throw new ValidationException("workspace_root must be an absolute path");
        }
        return path.toString();
    }

    private String normalizeCodingAgent(String codingAgent) {
        if (codingAgent == null || codingAgent.isBlank()) {
            throw new ValidationException("coding_agent is required");
        }
        return codingAgent.trim().toLowerCase().replace('-', '_');
    }

    private void validateAiTimeoutSeconds(int aiTimeoutSeconds) {
        if (aiTimeoutSeconds < 10) {
            throw new ValidationException("ai_timeout_seconds must be at least 10");
        }
        if (aiTimeoutSeconds > 7200) {
            throw new ValidationException("ai_timeout_seconds must not exceed 7200");
        }
    }

    private SystemSetting latestOf(SystemSetting... settings) {
        SystemSetting latest = null;
        for (SystemSetting s : settings) {
            if (s == null) {
                continue;
            }
            if (latest == null || s.getUpdatedAt().isAfter(latest.getUpdatedAt())) {
                latest = s;
            }
        }
        return latest;
    }

    private String resolveSettingValue(Optional<SystemSetting> setting, String defaultValue) {
        return setting
                .map(SystemSetting::getSettingValue)
                .map(String::trim)
                .filter((value) -> !value.isBlank())
                .orElse(defaultValue);
    }

    public record RuntimeSettings(
            String workspaceRoot,
            String codingAgent,
            int aiTimeoutSeconds,
            String catalogRepoUrl,
            String catalogDefaultBranch,
            String publishMode,
            String gitSshPrivateKey,
            String gitSshPublicKey,
            String gitSshPassphrase,
            String gitCertificate,
            String gitCertificateKey,
            String gitUsername,
            String gitPasswordOrPat,
            String localGitUsername,
            String localGitEmail,
            Instant updatedAt,
            String updatedBy
    ) {}

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
                    "failed",
                    message,
                    startedAt,
                    finishedAt,
                    mode.apiValue(),
                    requestedBy,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
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
                    "running",
                    message,
                    startedAt,
                    startedAt,
                    mode.apiValue(),
                    requestedBy,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of()
            );
        }
    }

    public record RepairError(String path, String message) {}

    private record ParsedMetadata(
            String entityType,
            String id,
            String version,
            String canonicalName,
            String displayName,
            String sourcePath,
            String content,
            String checksum,
            Map<String, Object> raw
    ) {
        String require(String key) {
            String value = optional(key);
            if (value == null || value.isBlank()) {
                throw new ValidationException("metadata field is required: " + key);
            }
            return value.trim();
        }

        String optional(String key) {
            Object value = raw.get(key);
            if (value == null) {
                return null;
            }
            String text = String.valueOf(value);
            return text.isBlank() ? null : text;
        }

        @SuppressWarnings("unchecked")
        List<String> tags() {
            Object value = raw.get("tags");
            if (value == null) {
                return List.of();
            }
            if (value instanceof List<?> list) {
                List<String> tags = new ArrayList<>();
                for (Object item : list) {
                    String text = String.valueOf(item).trim();
                    if (!text.isBlank()) {
                        tags.add(text);
                    }
                }
                return tags;
            }
            return List.of();
        }
    }

    private record UpsertOutcome(int inserted, int updated, int skipped) {
        static UpsertOutcome insertedOne() {
            return new UpsertOutcome(1, 0, 0);
        }

        static UpsertOutcome updatedOne() {
            return new UpsertOutcome(0, 1, 0);
        }

        static UpsertOutcome skippedOne() {
            return new UpsertOutcome(0, 0, 1);
        }
    }

    public enum RepairMode {
        UPSERT("upsert"),
        FROM_SCRATCH("from_scratch");

        private final String apiValue;

        RepairMode(String apiValue) {
            this.apiValue = apiValue;
        }

        public String apiValue() {
            return apiValue;
        }

        public static RepairMode from(String raw) {
            if (raw == null || raw.isBlank()) {
                return UPSERT;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "upsert" -> UPSERT;
                case "from_scratch", "from-scratch", "scratch", "reset" -> FROM_SCRATCH;
                default -> throw new ValidationException("repair mode must be upsert or from_scratch");
            };
        }
    }
}

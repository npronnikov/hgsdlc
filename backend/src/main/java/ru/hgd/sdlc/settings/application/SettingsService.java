package ru.hgd.sdlc.settings.application;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.settings.domain.SystemSetting;
import ru.hgd.sdlc.settings.infrastructure.SystemSettingRepository;

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
    private static final String DEFAULT_WORKSPACE_ROOT = "/tmp/workspace";
    private static final String DEFAULT_CODING_AGENT = "qwen";
    private static final int DEFAULT_AI_TIMEOUT_SECONDS = 900;
    private static final String DEFAULT_CATALOG_REPO_URL = "";
    private static final String DEFAULT_CATALOG_DEFAULT_BRANCH = "main";
    private static final String DEFAULT_CATALOG_PUBLISH_MODE = "pr";

    private final SystemSettingRepository repository;

    public SettingsService(SystemSettingRepository repository) {
        this.repository = repository;
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
                gitPassword.orElse(null)
        );
        return new RuntimeSettings(
                workspaceSetting.map(SystemSetting::getSettingValue).orElse(getWorkspaceRoot()),
                codingAgentSetting.map(SystemSetting::getSettingValue).orElse(getRuntimeCodingAgent()),
                aiTimeoutSetting.map(SystemSetting::getSettingValue).map(Integer::parseInt).orElse(DEFAULT_AI_TIMEOUT_SECONDS),
                catalogRepoUrl.map(SystemSetting::getSettingValue).orElse(DEFAULT_CATALOG_REPO_URL),
                catalogBranch.map(SystemSetting::getSettingValue).orElse(DEFAULT_CATALOG_DEFAULT_BRANCH),
                publishMode.map(SystemSetting::getSettingValue).orElse(DEFAULT_CATALOG_PUBLISH_MODE),
                sshPrivate.map(SystemSetting::getSettingValue).orElse(""),
                sshPublic.map(SystemSetting::getSettingValue).orElse(""),
                sshPass.map(SystemSetting::getSettingValue).orElse(""),
                cert.map(SystemSetting::getSettingValue).orElse(""),
                certKey.map(SystemSetting::getSettingValue).orElse(""),
                gitUser.map(SystemSetting::getSettingValue).orElse(""),
                gitPassword.map(SystemSetting::getSettingValue).orElse(""),
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
                repository.findById(CATALOG_REPO_URL_KEY).map(SystemSetting::getSettingValue).orElse(DEFAULT_CATALOG_REPO_URL),
                repository.findById(CATALOG_DEFAULT_BRANCH_KEY).map(SystemSetting::getSettingValue).orElse(DEFAULT_CATALOG_DEFAULT_BRANCH),
                repository.findById(CATALOG_PUBLISH_MODE_KEY).map(SystemSetting::getSettingValue).orElse(DEFAULT_CATALOG_PUBLISH_MODE),
                repository.findById(CATALOG_GIT_SSH_PRIVATE_KEY).map(SystemSetting::getSettingValue).orElse(""),
                repository.findById(CATALOG_GIT_SSH_PUBLIC_KEY).map(SystemSetting::getSettingValue).orElse(""),
                repository.findById(CATALOG_GIT_SSH_PASSPHRASE).map(SystemSetting::getSettingValue).orElse(""),
                repository.findById(CATALOG_GIT_CERTIFICATE).map(SystemSetting::getSettingValue).orElse(""),
                repository.findById(CATALOG_GIT_CERTIFICATE_KEY).map(SystemSetting::getSettingValue).orElse(""),
                repository.findById(CATALOG_GIT_USERNAME_KEY).map(SystemSetting::getSettingValue).orElse(""),
                repository.findById(CATALOG_GIT_PASSWORD_KEY).map(SystemSetting::getSettingValue).orElse(""),
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
        SystemSetting latest = latestOf(repo, branch, mode, sshPr, sshPb, sshPs, cert, certKey, user, pass);
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
                latest.getUpdatedAt(),
                latest.getUpdatedBy()
        );
    }

    @Transactional(readOnly = true)
    public RepairResult repairCatalog(String actorId) {
        return new RepairResult("queued", "Repair started", Instant.now(), actorId == null ? "system" : actorId);
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
            Instant updatedAt,
            String updatedBy
    ) {}

    public record RepairResult(
            String status,
            String message,
            Instant startedAt,
            String requestedBy
    ) {}
}

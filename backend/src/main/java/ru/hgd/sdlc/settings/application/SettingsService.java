package ru.hgd.sdlc.settings.application;

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
    public static final String PROMPT_LANGUAGE_KEY = "runtime.prompt_language";
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
    private static final String DEFAULT_PROMPT_LANGUAGE = "en";
    private static final String DEFAULT_CATALOG_REPO_URL = "https://github.com/npronnikov/catalog.git";
    private static final String DEFAULT_CATALOG_DEFAULT_BRANCH = "main";
    private static final String DEFAULT_CATALOG_PUBLISH_MODE = "pr";

    private final SystemSettingRepository repository;

    public SettingsService(SystemSettingRepository repository) {
        this.repository = repository;
    }

    // ---- Runtime settings — getters ----

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

    public String getPromptLanguage() {
        return repository.findById(PROMPT_LANGUAGE_KEY)
                .map(SystemSetting::getSettingValue)
                .filter(v -> !v.isBlank())
                .map(this::normalizePromptLanguage)
                .orElse(DEFAULT_PROMPT_LANGUAGE);
    }

    public String getCatalogRepoUrl() {
        return repository.findById(CATALOG_REPO_URL_KEY)
                .map(SystemSetting::getSettingValue)
                .filter((value) -> !value.isBlank())
                .orElse(DEFAULT_CATALOG_REPO_URL);
    }

    public String getCatalogDefaultBranch() {
        return repository.findById(CATALOG_DEFAULT_BRANCH_KEY)
                .map(SystemSetting::getSettingValue)
                .filter(v -> !v.isBlank())
                .orElse(DEFAULT_CATALOG_DEFAULT_BRANCH);
    }

    // ---- Runtime settings — updates ----

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

    public SystemSetting updatePromptLanguage(String language, String actorId) {
        String normalized = normalizePromptLanguage(language);
        SystemSetting setting = repository.findById(PROMPT_LANGUAGE_KEY)
                .orElseGet(() -> SystemSetting.builder().settingKey(PROMPT_LANGUAGE_KEY).build());
        setting.setSettingValue(normalized);
        setting.setUpdatedAt(Instant.now());
        setting.setUpdatedBy(actorId == null || actorId.isBlank() ? "system" : actorId);
        return repository.save(setting);
    }

    // ---- Composite settings reads/writes ----

    @Transactional
    public RuntimeSettings getRuntimeSettings() {
        Optional<SystemSetting> workspaceSetting    = getWorkspaceRootSetting();
        Optional<SystemSetting> codingAgentSetting  = getRuntimeCodingAgentSetting();
        Optional<SystemSetting> aiTimeoutSetting    = getAiTimeoutSecondsSetting();
        Optional<SystemSetting> catalogRepoUrl      = repository.findById(CATALOG_REPO_URL_KEY);
        Optional<SystemSetting> catalogBranch       = repository.findById(CATALOG_DEFAULT_BRANCH_KEY);
        Optional<SystemSetting> publishMode         = repository.findById(CATALOG_PUBLISH_MODE_KEY);
        Optional<SystemSetting> sshPrivate          = repository.findById(CATALOG_GIT_SSH_PRIVATE_KEY);
        Optional<SystemSetting> sshPublic           = repository.findById(CATALOG_GIT_SSH_PUBLIC_KEY);
        Optional<SystemSetting> sshPass             = repository.findById(CATALOG_GIT_SSH_PASSPHRASE);
        Optional<SystemSetting> cert                = repository.findById(CATALOG_GIT_CERTIFICATE);
        Optional<SystemSetting> certKey             = repository.findById(CATALOG_GIT_CERTIFICATE_KEY);
        Optional<SystemSetting> gitUser             = repository.findById(CATALOG_GIT_USERNAME_KEY);
        Optional<SystemSetting> gitPassword         = repository.findById(CATALOG_GIT_PASSWORD_KEY);
        Optional<SystemSetting> localGitUser        = repository.findById(CATALOG_LOCAL_GIT_USERNAME_KEY);
        Optional<SystemSetting> localGitEmail       = repository.findById(CATALOG_LOCAL_GIT_EMAIL_KEY);
        Optional<SystemSetting> promptLanguageSetting = repository.findById(PROMPT_LANGUAGE_KEY);
        SystemSetting latestSetting = latestOf(
                workspaceSetting.orElse(null), codingAgentSetting.orElse(null), aiTimeoutSetting.orElse(null),
                catalogRepoUrl.orElse(null), catalogBranch.orElse(null), publishMode.orElse(null),
                sshPrivate.orElse(null), sshPublic.orElse(null), sshPass.orElse(null),
                cert.orElse(null), certKey.orElse(null), gitUser.orElse(null), gitPassword.orElse(null),
                localGitUser.orElse(null), localGitEmail.orElse(null), promptLanguageSetting.orElse(null)
        );
        return new RuntimeSettings(
                workspaceSetting.map(SystemSetting::getSettingValue).orElse(getWorkspaceRoot()),
                codingAgentSetting.map(SystemSetting::getSettingValue).orElse(getRuntimeCodingAgent()),
                aiTimeoutSetting.map(SystemSetting::getSettingValue).map(Integer::parseInt).orElse(DEFAULT_AI_TIMEOUT_SECONDS),
                promptLanguageSetting.map(SystemSetting::getSettingValue).map(this::normalizePromptLanguage).orElse(DEFAULT_PROMPT_LANGUAGE),
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
    public RuntimeSettings updateRuntimeSettings(String workspaceRoot, String codingAgent, int aiTimeoutSeconds, String promptLanguage, String actorId) {
        SystemSetting workspaceSetting      = updateWorkspaceRoot(workspaceRoot, actorId);
        SystemSetting codingAgentSetting    = updateRuntimeCodingAgent(codingAgent, actorId);
        SystemSetting aiTimeoutSetting      = updateAiTimeoutSeconds(aiTimeoutSeconds, actorId);
        SystemSetting promptLanguageSetting = updatePromptLanguage(promptLanguage, actorId);
        SystemSetting latestSetting         = latestOf(workspaceSetting, codingAgentSetting, aiTimeoutSetting, promptLanguageSetting);
        return new RuntimeSettings(
                workspaceSetting.getSettingValue(),
                codingAgentSetting.getSettingValue(),
                Integer.parseInt(aiTimeoutSetting.getSettingValue()),
                promptLanguageSetting.getSettingValue(),
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
        SystemSetting repo      = upsert(CATALOG_REPO_URL_KEY, catalogRepoUrl == null ? "" : catalogRepoUrl.trim(), actorId);
        SystemSetting branch    = upsert(CATALOG_DEFAULT_BRANCH_KEY, normalizeBranch(catalogDefaultBranch), actorId);
        SystemSetting mode      = upsert(CATALOG_PUBLISH_MODE_KEY, normalizePublishMode(publishMode), actorId);
        SystemSetting sshPr     = upsert(CATALOG_GIT_SSH_PRIVATE_KEY, gitSshPrivateKey == null ? "" : gitSshPrivateKey, actorId);
        SystemSetting sshPb     = upsert(CATALOG_GIT_SSH_PUBLIC_KEY, gitSshPublicKey == null ? "" : gitSshPublicKey, actorId);
        SystemSetting sshPs     = upsert(CATALOG_GIT_SSH_PASSPHRASE, gitSshPassphrase == null ? "" : gitSshPassphrase, actorId);
        SystemSetting cert      = upsert(CATALOG_GIT_CERTIFICATE, gitCertificate == null ? "" : gitCertificate, actorId);
        SystemSetting certKey   = upsert(CATALOG_GIT_CERTIFICATE_KEY, gitCertificateKey == null ? "" : gitCertificateKey, actorId);
        SystemSetting user      = upsert(CATALOG_GIT_USERNAME_KEY, gitUsername == null ? "" : gitUsername.trim(), actorId);
        SystemSetting pass      = upsert(CATALOG_GIT_PASSWORD_KEY, gitPasswordOrPat == null ? "" : gitPasswordOrPat, actorId);
        SystemSetting localUser = upsert(CATALOG_LOCAL_GIT_USERNAME_KEY, normalizeLocalGitUsername(localGitUsername), actorId);
        SystemSetting localEmail = upsert(CATALOG_LOCAL_GIT_EMAIL_KEY, normalizeLocalGitEmail(localGitEmail), actorId);
        SystemSetting latest    = latestOf(repo, branch, mode, sshPr, sshPb, sshPs, cert, certKey, user, pass, localUser, localEmail);
        return new RuntimeSettings(
                getWorkspaceRoot(),
                getRuntimeCodingAgent(),
                getAiTimeoutSeconds(),
                getPromptLanguage(),
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

    // ---- Helpers ----

    private SystemSetting upsert(String key, String value, String actorId) {
        SystemSetting setting = repository.findById(key).orElseGet(() -> SystemSetting.builder().settingKey(key).build());
        setting.setSettingValue(value == null ? "" : value);
        setting.setUpdatedAt(Instant.now());
        setting.setUpdatedBy(actorId == null || actorId.isBlank() ? "system" : actorId);
        return repository.save(setting);
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

    // ---- Normalization ----

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
        java.nio.file.Path path = java.nio.file.Path.of(workspaceRoot.trim()).toAbsolutePath().normalize();
        if (!path.isAbsolute()) {
            throw new ValidationException("workspace_root must be an absolute path");
        }
        return path.toString();
    }

    private String normalizePromptLanguage(String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_PROMPT_LANGUAGE;
        }
        String normalized = language.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.equals("en") && !normalized.equals("ru")) {
            throw new ValidationException("prompt_language must be en or ru");
        }
        return normalized;
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

    // ---- Public types ----

    public record RuntimeSettings(
            String workspaceRoot,
            String codingAgent,
            int aiTimeoutSeconds,
            String promptLanguage,
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
}

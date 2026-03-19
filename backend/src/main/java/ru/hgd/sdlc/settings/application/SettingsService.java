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
    private static final String DEFAULT_WORKSPACE_ROOT = "/tmp/workspace";
    private static final String DEFAULT_CODING_AGENT = "qwen";
    private static final int DEFAULT_AI_TIMEOUT_SECONDS = 900;

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
        SystemSetting latestSetting = latestOf(workspaceSetting.orElse(null), codingAgentSetting.orElse(null), aiTimeoutSetting.orElse(null));
        return new RuntimeSettings(
                workspaceSetting.map(SystemSetting::getSettingValue).orElse(getWorkspaceRoot()),
                codingAgentSetting.map(SystemSetting::getSettingValue).orElse(getRuntimeCodingAgent()),
                aiTimeoutSetting.map(SystemSetting::getSettingValue).map(Integer::parseInt).orElse(DEFAULT_AI_TIMEOUT_SECONDS),
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
                latestSetting.getUpdatedAt(),
                latestSetting.getUpdatedBy()
        );
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
            Instant updatedAt,
            String updatedBy
    ) {}
}

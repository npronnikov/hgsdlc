package ru.hgd.sdlc.settings.application;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.settings.domain.SystemSetting;
import ru.hgd.sdlc.settings.infrastructure.SystemSettingRepository;

@Service
public class SettingsService {
    public static final String WORKSPACE_ROOT_KEY = "runtime.workspace_root";
    private static final String DEFAULT_WORKSPACE_ROOT = "/tmp";

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

    public SystemSetting updateWorkspaceRoot(String workspaceRoot, String actorId) {
        String normalized = normalizeWorkspaceRoot(workspaceRoot);
        SystemSetting setting = repository.findById(WORKSPACE_ROOT_KEY)
                .orElseGet(() -> SystemSetting.builder().settingKey(WORKSPACE_ROOT_KEY).build());
        setting.setSettingValue(normalized);
        setting.setUpdatedAt(Instant.now());
        setting.setUpdatedBy(actorId == null || actorId.isBlank() ? "system" : actorId);
        return repository.save(setting);
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
}

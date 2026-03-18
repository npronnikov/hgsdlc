package ru.hgd.sdlc.settings.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.settings.application.SettingsService;
import ru.hgd.sdlc.settings.domain.SystemSetting;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/runtime")
    public RuntimeSettingsResponse getRuntimeSettings() {
        Optional<SystemSetting> setting = settingsService.getWorkspaceRootSetting();
        String workspaceRoot = setting.map(SystemSetting::getSettingValue).orElse(settingsService.getWorkspaceRoot());
        return new RuntimeSettingsResponse(
                workspaceRoot,
                setting.map(SystemSetting::getUpdatedAt).orElse(null),
                setting.map(SystemSetting::getUpdatedBy).orElse(null)
        );
    }

    @PutMapping("/runtime")
    public ResponseEntity<RuntimeSettingsResponse> updateRuntimeSettings(
            @RequestBody RuntimeSettingsRequest request,
            @AuthenticationPrincipal User user
    ) {
        if (request == null || request.workspaceRoot() == null || request.workspaceRoot().isBlank()) {
            throw new ValidationException("workspace_root is required");
        }
        SystemSetting updated = settingsService.updateWorkspaceRoot(request.workspaceRoot(), user == null ? "system" : user.getUsername());
        RuntimeSettingsResponse response = new RuntimeSettingsResponse(
                updated.getSettingValue(),
                updated.getUpdatedAt(),
                updated.getUpdatedBy()
        );
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    public record RuntimeSettingsRequest(
            @JsonProperty("workspace_root") String workspaceRoot
    ) {}

    public record RuntimeSettingsResponse(
            @JsonProperty("workspace_root") String workspaceRoot,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("updated_by") String updatedBy
    ) {}
}

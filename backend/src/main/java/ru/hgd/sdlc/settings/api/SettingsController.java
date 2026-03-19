package ru.hgd.sdlc.settings.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
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

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/runtime")
    public RuntimeSettingsResponse getRuntimeSettings() {
        SettingsService.RuntimeSettings settings = settingsService.getRuntimeSettings();
        return new RuntimeSettingsResponse(
                settings.workspaceRoot(),
                settings.codingAgent(),
                settings.aiTimeoutSeconds(),
                settings.updatedAt(),
                settings.updatedBy()
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
        if (request.codingAgent() == null || request.codingAgent().isBlank()) {
            throw new ValidationException("coding_agent is required");
        }
        if (request.aiTimeoutSeconds() == null) {
            throw new ValidationException("ai_timeout_seconds is required");
        }
        SettingsService.RuntimeSettings updated = settingsService.updateRuntimeSettings(
                request.workspaceRoot(),
                request.codingAgent(),
                request.aiTimeoutSeconds(),
                user == null ? "system" : user.getUsername()
        );
        RuntimeSettingsResponse response = new RuntimeSettingsResponse(
                updated.workspaceRoot(),
                updated.codingAgent(),
                updated.aiTimeoutSeconds(),
                updated.updatedAt(),
                updated.updatedBy()
        );
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    public record RuntimeSettingsRequest(
            @JsonProperty("workspace_root") String workspaceRoot,
            @JsonProperty("coding_agent") String codingAgent,
            @JsonProperty("ai_timeout_seconds") Integer aiTimeoutSeconds
    ) {}

    public record RuntimeSettingsResponse(
            @JsonProperty("workspace_root") String workspaceRoot,
            @JsonProperty("coding_agent") String codingAgent,
            @JsonProperty("ai_timeout_seconds") int aiTimeoutSeconds,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("updated_by") String updatedBy
    ) {}
}

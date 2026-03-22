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
                settings.catalogRepoUrl(),
                settings.catalogDefaultBranch(),
                settings.publishMode(),
                settings.gitSshPrivateKey(),
                settings.gitSshPublicKey(),
                settings.gitSshPassphrase(),
                settings.gitCertificate(),
                settings.gitCertificateKey(),
                settings.gitUsername(),
                settings.gitPasswordOrPat(),
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
                updated.catalogRepoUrl(),
                updated.catalogDefaultBranch(),
                updated.publishMode(),
                updated.gitSshPrivateKey(),
                updated.gitSshPublicKey(),
                updated.gitSshPassphrase(),
                updated.gitCertificate(),
                updated.gitCertificateKey(),
                updated.gitUsername(),
                updated.gitPasswordOrPat(),
                updated.updatedAt(),
                updated.updatedBy()
        );
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PutMapping("/catalog")
    public ResponseEntity<RuntimeSettingsResponse> updateCatalogSettings(
            @RequestBody CatalogSettingsRequest request,
            @AuthenticationPrincipal User user
    ) {
        SettingsService.RuntimeSettings updated = settingsService.updateCatalogSettings(
                request.catalogRepoUrl(),
                request.catalogDefaultBranch(),
                request.publishMode(),
                request.gitSshPrivateKey(),
                request.gitSshPublicKey(),
                request.gitSshPassphrase(),
                request.gitCertificate(),
                request.gitCertificateKey(),
                request.gitUsername(),
                request.gitPasswordOrPat(),
                user == null ? "system" : user.getUsername()
        );
        RuntimeSettingsResponse response = new RuntimeSettingsResponse(
                updated.workspaceRoot(),
                updated.codingAgent(),
                updated.aiTimeoutSeconds(),
                updated.catalogRepoUrl(),
                updated.catalogDefaultBranch(),
                updated.publishMode(),
                updated.gitSshPrivateKey(),
                updated.gitSshPublicKey(),
                updated.gitSshPassphrase(),
                updated.gitCertificate(),
                updated.gitCertificateKey(),
                updated.gitUsername(),
                updated.gitPasswordOrPat(),
                updated.updatedAt(),
                updated.updatedBy()
        );
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PutMapping("/catalog/repair")
    public ResponseEntity<RepairResponse> repairCatalog(@AuthenticationPrincipal User user) {
        SettingsService.RepairResult result = settingsService.repairCatalog(user == null ? "system" : user.getUsername());
        return ResponseEntity.status(HttpStatus.OK)
                .body(new RepairResponse(result.status(), result.message(), result.startedAt(), result.requestedBy()));
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
            @JsonProperty("catalog_repo_url") String catalogRepoUrl,
            @JsonProperty("catalog_default_branch") String catalogDefaultBranch,
            @JsonProperty("publish_mode") String publishMode,
            @JsonProperty("git_ssh_private_key") String gitSshPrivateKey,
            @JsonProperty("git_ssh_public_key") String gitSshPublicKey,
            @JsonProperty("git_ssh_passphrase") String gitSshPassphrase,
            @JsonProperty("git_certificate") String gitCertificate,
            @JsonProperty("git_certificate_key") String gitCertificateKey,
            @JsonProperty("git_username") String gitUsername,
            @JsonProperty("git_password_or_pat") String gitPasswordOrPat,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("updated_by") String updatedBy
    ) {}

    public record CatalogSettingsRequest(
            @JsonProperty("catalog_repo_url") String catalogRepoUrl,
            @JsonProperty("catalog_default_branch") String catalogDefaultBranch,
            @JsonProperty("publish_mode") String publishMode,
            @JsonProperty("git_ssh_private_key") String gitSshPrivateKey,
            @JsonProperty("git_ssh_public_key") String gitSshPublicKey,
            @JsonProperty("git_ssh_passphrase") String gitSshPassphrase,
            @JsonProperty("git_certificate") String gitCertificate,
            @JsonProperty("git_certificate_key") String gitCertificateKey,
            @JsonProperty("git_username") String gitUsername,
            @JsonProperty("git_password_or_pat") String gitPasswordOrPat
    ) {}

    public record RepairResponse(
            @JsonProperty("status") String status,
            @JsonProperty("message") String message,
            @JsonProperty("started_at") Instant startedAt,
            @JsonProperty("requested_by") String requestedBy
    ) {}
}

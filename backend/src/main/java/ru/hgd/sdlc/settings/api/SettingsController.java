package ru.hgd.sdlc.settings.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
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
import ru.hgd.sdlc.settings.application.CatalogService;
import ru.hgd.sdlc.settings.application.SettingsService;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final CatalogService catalogService;

    public SettingsController(SettingsService settingsService, CatalogService catalogService) {
        this.settingsService = settingsService;
        this.catalogService = catalogService;
    }

    @GetMapping("/runtime")
    public RuntimeSettingsResponse getRuntimeSettings() {
        SettingsService.RuntimeSettings settings = settingsService.getRuntimeSettings();
        return toResponse(settings);
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
                request.promptLanguage(),
                user == null ? "system" : user.getUsername()
        );
        return ResponseEntity.status(HttpStatus.OK).body(toResponse(updated));
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
                request.localGitUsername(),
                request.localGitEmail(),
                user == null ? "system" : user.getUsername()
        );
        return ResponseEntity.status(HttpStatus.OK).body(toResponse(updated));
    }

    @PutMapping("/catalog/repair")
    public ResponseEntity<RepairResponse> repairCatalog(
            @RequestBody(required = false) RepairRequest request,
            @AuthenticationPrincipal User user
    ) {
        CatalogService.RepairResult result = catalogService.repairCatalog(
                user == null ? "system" : user.getUsername(),
                request == null ? null : request.mode()
        );
        return ResponseEntity.status(HttpStatus.OK).body(new RepairResponse(
                result.status(),
                result.message(),
                result.startedAt(),
                result.finishedAt(),
                result.mode(),
                result.requestedBy(),
                result.scannedRules(),
                result.scannedSkills(),
                result.scannedFlows(),
                result.inserted(),
                result.updated(),
                result.skipped(),
                result.errors() == null ? List.of() : result.errors().stream()
                        .map(err -> new RepairErrorItem(err.path(), err.message()))
                        .toList()
        ));
    }

    private RuntimeSettingsResponse toResponse(SettingsService.RuntimeSettings s) {
        return new RuntimeSettingsResponse(
                s.workspaceRoot(), s.codingAgent(), s.aiTimeoutSeconds(), s.promptLanguage(),
                s.catalogRepoUrl(), s.catalogDefaultBranch(), s.publishMode(),
                s.gitSshPrivateKey(), s.gitSshPublicKey(), s.gitSshPassphrase(),
                s.gitCertificate(), s.gitCertificateKey(), s.gitUsername(), s.gitPasswordOrPat(),
                s.localGitUsername(), s.localGitEmail(), s.updatedAt(), s.updatedBy()
        );
    }

    // ---- Request / Response types ----

    public record RuntimeSettingsRequest(
            @JsonProperty("workspace_root") String workspaceRoot,
            @JsonProperty("coding_agent") String codingAgent,
            @JsonProperty("ai_timeout_seconds") Integer aiTimeoutSeconds,
            @JsonProperty("prompt_language") String promptLanguage
    ) {}

    public record RuntimeSettingsResponse(
            @JsonProperty("workspace_root") String workspaceRoot,
            @JsonProperty("coding_agent") String codingAgent,
            @JsonProperty("ai_timeout_seconds") int aiTimeoutSeconds,
            @JsonProperty("prompt_language") String promptLanguage,
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
            @JsonProperty("local_git_username") String localGitUsername,
            @JsonProperty("local_git_email") String localGitEmail,
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
            @JsonProperty("git_password_or_pat") String gitPasswordOrPat,
            @JsonProperty("local_git_username") String localGitUsername,
            @JsonProperty("local_git_email") String localGitEmail
    ) {}

    public record RepairResponse(
            @JsonProperty("status") String status,
            @JsonProperty("message") String message,
            @JsonProperty("started_at") Instant startedAt,
            @JsonProperty("finished_at") Instant finishedAt,
            @JsonProperty("mode") String mode,
            @JsonProperty("requested_by") String requestedBy,
            @JsonProperty("scanned_rules") int scannedRules,
            @JsonProperty("scanned_skills") int scannedSkills,
            @JsonProperty("scanned_flows") int scannedFlows,
            @JsonProperty("inserted") int inserted,
            @JsonProperty("updated") int updated,
            @JsonProperty("skipped") int skipped,
            @JsonProperty("errors") List<RepairErrorItem> errors
    ) {}

    public record RepairRequest(
            @JsonProperty("mode") String mode
    ) {}

    public record RepairErrorItem(
            @JsonProperty("path") String path,
            @JsonProperty("message") String message
    ) {}
}

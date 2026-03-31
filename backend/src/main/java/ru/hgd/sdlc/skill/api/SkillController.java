package ru.hgd.sdlc.skill.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.idempotency.application.IdempotencyService;
import ru.hgd.sdlc.skill.application.SkillService;

@RestController
@RequestMapping("/api/skills")
public class SkillController {
    private final SkillService skillService;
    private final IdempotencyService idempotencyService;

    public SkillController(SkillService skillService, IdempotencyService idempotencyService) {
        this.skillService = skillService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    public List<SkillSummaryResponse> list() {
        return skillService.listLatest().stream().map(SkillSummaryResponse::from).toList();
    }

    @GetMapping("/query")
    public SkillCatalogQueryResponse query(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String codingAgent,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String approvalStatus,
            @RequestParam(required = false) String teamCode,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String platformCode,
            @RequestParam(required = false) String skillKind,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean hasDescription
    ) {
        SkillService.SkillCatalogPage page = skillService.queryLatestForCatalog(
                new SkillService.SkillCatalogQuery(
                        cursor,
                        limit,
                        search,
                        codingAgent,
                        status,
                        approvalStatus,
                        teamCode,
                        scope,
                        platformCode,
                        skillKind,
                        version,
                        tag,
                        hasDescription
                )
        );
        return new SkillCatalogQueryResponse(
                page.items().stream().map(SkillSummaryResponse::from).toList(),
                page.nextCursor(),
                page.hasMore()
        );
    }

    @GetMapping("/tags")
    public List<String> tags() {
        return skillService.listTags();
    }

    @GetMapping("/pending-publication")
    public List<SkillSummaryResponse> pendingPublication(@AuthenticationPrincipal User user) {
        return skillService.listPendingPublication(user).stream().map(SkillSummaryResponse::from).toList();
    }

    @PostMapping("/{skillId}/versions/{version}/approve")
    public SkillResponse approve(
            @PathVariable String skillId,
            @PathVariable String version,
            @AuthenticationPrincipal User user
    ) {
        return SkillResponse.from(skillService.approvePublication(skillId, version, user));
    }

    @PostMapping("/{skillId}/versions/{version}/reject")
    public SkillResponse reject(
            @PathVariable String skillId,
            @PathVariable String version,
            @AuthenticationPrincipal User user
    ) {
        return SkillResponse.from(skillService.rejectPublication(skillId, version, user));
    }

    @DeleteMapping("/{skillId}/versions/{version}/draft")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable String skillId,
            @PathVariable String version,
            @AuthenticationPrincipal User user
    ) {
        skillService.deleteDraft(skillId, version, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{skillId}")
    public SkillResponse get(@PathVariable String skillId) {
        return SkillResponse.from(skillService.getLatest(skillId));
    }

    @GetMapping("/{skillId}/versions")
    public List<SkillSummaryResponse> versions(@PathVariable String skillId) {
        return skillService.getVersions(skillId).stream().map(SkillSummaryResponse::from).toList();
    }

    @GetMapping("/{skillId}/versions/{version}")
    public SkillResponse getVersion(@PathVariable String skillId, @PathVariable String version) {
        return SkillResponse.from(skillService.getVersion(skillId, version));
    }

    @GetMapping("/{skillId}/versions/{version}/files")
    public List<SkillFileMetadataResponse> listFiles(@PathVariable String skillId, @PathVariable String version) {
        return skillService.listFiles(skillId, version).stream()
                .map(SkillFileMetadataResponse::from)
                .toList();
    }

    @PostMapping("/{skillId}/versions/{version}/files/content")
    public SkillFileContentResponse fileContent(
            @PathVariable String skillId,
            @PathVariable String version,
            @RequestBody SkillFileContentRequest request
    ) {
        if (request == null || request.path() == null || request.path().isBlank()) {
            throw new ValidationException("path is required");
        }
        String content = skillService.getFileContent(skillId, version, request.path());
        return new SkillFileContentResponse(request.path(), content);
    }

    @PostMapping("/{skillId}/save")
    public SkillResponse save(
            @PathVariable String skillId,
            @RequestBody SkillSaveRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal User user
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("skillId", skillId);
        payload.put("request", request);
        payload.put("user", user == null ? null : user.getUsername());
        String requestHash = idempotencyService.hashPayload(payload);

        return idempotencyService.execute(
                idempotencyKey,
                "skills.save",
                requestHash,
                SkillResponse.class,
                () -> SkillResponse.from(skillService.save(skillId, request, user))
        );
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<String> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<String> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    public record SkillCatalogQueryResponse(
            @JsonProperty("items") List<SkillSummaryResponse> items,
            @JsonProperty("next_cursor") String nextCursor,
            @JsonProperty("has_more") boolean hasMore
    ) {
    }

    public record SkillFileContentRequest(
            @JsonProperty("path") String path
    ) {
    }

    public record SkillFileContentResponse(
            @JsonProperty("path") String path,
            @JsonProperty("text_content") String textContent
    ) {
    }
}

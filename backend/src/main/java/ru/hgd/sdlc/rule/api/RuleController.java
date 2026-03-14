package ru.hgd.sdlc.rule.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.idempotency.application.IdempotencyService;
import ru.hgd.sdlc.rule.application.RuleService;

@RestController
@RequestMapping("/api/rules")
public class RuleController {
    private final RuleService ruleService;
    private final IdempotencyService idempotencyService;

    public RuleController(RuleService ruleService, IdempotencyService idempotencyService) {
        this.ruleService = ruleService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    public List<RuleSummaryResponse> list() {
        return ruleService.listLatest().stream().map(RuleSummaryResponse::from).toList();
    }

    @GetMapping("/{ruleId}")
    public RuleResponse get(@PathVariable String ruleId) {
        return RuleResponse.from(ruleService.getLatest(ruleId));
    }

    @GetMapping("/{ruleId}/versions")
    public List<RuleSummaryResponse> versions(@PathVariable String ruleId) {
        return ruleService.getVersions(ruleId).stream().map(RuleSummaryResponse::from).toList();
    }

    @PostMapping("/{ruleId}/save")
    public RuleResponse save(
            @PathVariable String ruleId,
            @RequestBody RuleSaveRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal User user
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ruleId", ruleId);
        payload.put("request", request);
        payload.put("user", user == null ? null : user.getUsername());
        String requestHash = idempotencyService.hashPayload(payload);

        return idempotencyService.execute(
                idempotencyKey,
                "rules.save",
                requestHash,
                RuleResponse.class,
                () -> RuleResponse.from(ruleService.save(ruleId, request, user))
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
}

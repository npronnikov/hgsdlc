package ru.hgd.sdlc.flow.api;

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
import ru.hgd.sdlc.flow.application.FlowService;
import ru.hgd.sdlc.idempotency.application.IdempotencyService;

@RestController
@RequestMapping("/api/flows")
public class FlowController {
    private final FlowService flowService;
    private final IdempotencyService idempotencyService;

    public FlowController(FlowService flowService, IdempotencyService idempotencyService) {
        this.flowService = flowService;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    public List<FlowSummaryResponse> list() {
        return flowService.listLatest().stream().map(FlowSummaryResponse::from).toList();
    }

    @GetMapping("/{flowId}")
    public FlowResponse get(@PathVariable String flowId) {
        return FlowResponse.from(flowService.getLatest(flowId));
    }

    @GetMapping("/{flowId}/versions")
    public List<FlowSummaryResponse> versions(@PathVariable String flowId) {
        return flowService.getVersions(flowId).stream().map(FlowSummaryResponse::from).toList();
    }

    @GetMapping("/{flowId}/versions/{version}")
    public FlowResponse getVersion(@PathVariable String flowId, @PathVariable String version) {
        return FlowResponse.from(flowService.getVersion(flowId, version));
    }

    @PostMapping("/{flowId}/save")
    public FlowResponse save(
            @PathVariable String flowId,
            @RequestBody FlowSaveRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal User user
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("flowId", flowId);
        payload.put("request", request);
        payload.put("user", user == null ? null : user.getUsername());
        String requestHash = idempotencyService.hashPayload(payload);

        return idempotencyService.execute(
                idempotencyKey,
                "flows.save",
                requestHash,
                FlowResponse.class,
                () -> FlowResponse.from(flowService.save(flowId, request, user))
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

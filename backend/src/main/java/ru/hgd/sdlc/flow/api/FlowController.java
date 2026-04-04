package ru.hgd.sdlc.flow.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
import ru.hgd.sdlc.flow.application.FlowYamlParser;
import ru.hgd.sdlc.flow.application.FlowService;
import ru.hgd.sdlc.idempotency.application.IdempotencyService;

@RestController
@RequestMapping("/api/flows")
public class FlowController {
    private final FlowService flowService;
    private final FlowYamlParser flowYamlParser;
    private final IdempotencyService idempotencyService;

    public FlowController(FlowService flowService, FlowYamlParser flowYamlParser, IdempotencyService idempotencyService) {
        this.flowService = flowService;
        this.flowYamlParser = flowYamlParser;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping
    public List<FlowSummaryResponse> list() {
        return flowService.listLatest().stream().map(this::toFlowSummary).toList();
    }

    @GetMapping("/query")
    public FlowCatalogQueryResponse query(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String codingAgent,
            @RequestParam(required = false) String teamCode,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String platformCode,
            @RequestParam(required = false) String flowKind,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String lifecycleStatus,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) Boolean hasDescription
    ) {
        FlowService.FlowCatalogPage page = flowService.queryLatestForCatalog(
                new FlowService.FlowCatalogQuery(
                        cursor,
                        limit,
                        search,
                        codingAgent,
                        teamCode,
                        scope,
                        platformCode,
                        flowKind,
                        riskLevel,
                        lifecycleStatus,
                        tag,
                        status,
                        version,
                        hasDescription
                )
        );
        return new FlowCatalogQueryResponse(
                page.items().stream().map(this::toFlowSummary).toList(),
                page.nextCursor(),
                page.hasMore()
        );
    }

    @GetMapping("/{flowId}")
    public FlowResponse get(@PathVariable String flowId) {
        var version = flowService.getLatest(flowId);
        var model = flowYamlParser.parse(version.getFlowYaml());
        return FlowResponse.from(version, model);
    }

    @GetMapping("/{flowId}/versions")
    public List<FlowSummaryResponse> versions(@PathVariable String flowId) {
        return flowService.getVersions(flowId).stream().map(FlowSummaryResponse::from).toList();
    }

    @GetMapping("/{flowId}/versions/{version}")
    public FlowResponse getVersion(@PathVariable String flowId, @PathVariable String version) {
        var flowVersion = flowService.getVersion(flowId, version);
        var model = flowYamlParser.parse(flowVersion.getFlowYaml());
        return FlowResponse.from(flowVersion, model);
    }

    @DeleteMapping("/{flowId}/versions/{version}/draft")
    @PreAuthorize("hasAnyRole('ADMIN','FLOW_CONFIGURATOR')")
    public ResponseEntity<Void> deleteDraft(
            @PathVariable String flowId,
            @PathVariable String version,
            @AuthenticationPrincipal User user
    ) {
        flowService.deleteDraft(flowId, version, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{flowId}/save")
    @PreAuthorize("hasAnyRole('ADMIN','FLOW_CONFIGURATOR')")
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
                () -> {
                    var saved = flowService.save(flowId, request, user);
                    var model = flowYamlParser.parse(saved.getFlowYaml());
                    return FlowResponse.from(saved, model);
                }
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

    private FlowSummaryResponse toFlowSummary(ru.hgd.sdlc.flow.domain.FlowVersion version) {
        var model = flowYamlParser.parse(version.getFlowYaml());
        Integer nodeCount = model.getNodes() == null ? 0 : model.getNodes().size();
        return FlowSummaryResponse.from(version, nodeCount);
    }

    public record FlowCatalogQueryResponse(
            @JsonProperty("items") List<FlowSummaryResponse> items,
            @JsonProperty("next_cursor") String nextCursor,
            @JsonProperty("has_more") boolean hasMore
    ) {
    }
}

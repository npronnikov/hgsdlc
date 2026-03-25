package ru.hgd.sdlc.dashboard.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.dashboard.application.OverviewService;

@RestController
@RequestMapping("/api/overview")
public class OverviewController {
    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping
    public OverviewResponse get(@AuthenticationPrincipal User user) {
        OverviewService.OverviewData data = overviewService.getOverview(user);
        return new OverviewResponse(
                new MetricsResponse(
                        data.metrics().activeRuns(),
                        data.metrics().waitingGates(),
                        data.metrics().awaitingDecisionGates(),
                        data.metrics().publishedFlows(),
                        data.metrics().draftFlows(),
                        data.metrics().auditEvents24h()
                ),
                data.recentRuns().stream()
                        .map((item) -> new RecentRunResponse(
                                item.runId(),
                                item.project(),
                                item.flow(),
                                item.status(),
                                item.createdAt()
                        ))
                        .toList(),
                data.gateInbox().stream()
                        .map((item) -> new GateInboxResponse(
                                item.gateId(),
                                item.runId(),
                                item.title(),
                                item.gateKind(),
                                item.status(),
                                item.role(),
                                item.openedAt()
                        ))
                        .toList()
        );
    }

    public record OverviewResponse(
            @JsonProperty("metrics") MetricsResponse metrics,
            @JsonProperty("recent_runs") List<RecentRunResponse> recentRuns,
            @JsonProperty("gate_inbox") List<GateInboxResponse> gateInbox
    ) {
    }

    public record MetricsResponse(
            @JsonProperty("active_runs") long activeRuns,
            @JsonProperty("waiting_gates") long waitingGates,
            @JsonProperty("awaiting_decision_gates") long awaitingDecisionGates,
            @JsonProperty("published_flows") long publishedFlows,
            @JsonProperty("draft_flows") long draftFlows,
            @JsonProperty("audit_events_24h") long auditEvents24h
    ) {
    }

    public record RecentRunResponse(
            @JsonProperty("run_id") UUID runId,
            @JsonProperty("project") String project,
            @JsonProperty("flow") String flow,
            @JsonProperty("status") String status,
            @JsonProperty("created_at") Instant createdAt
    ) {
    }

    public record GateInboxResponse(
            @JsonProperty("gate_id") UUID gateId,
            @JsonProperty("run_id") UUID runId,
            @JsonProperty("title") String title,
            @JsonProperty("gate_kind") String gateKind,
            @JsonProperty("status") String status,
            @JsonProperty("role") String role,
            @JsonProperty("opened_at") Instant openedAt
    ) {
    }
}

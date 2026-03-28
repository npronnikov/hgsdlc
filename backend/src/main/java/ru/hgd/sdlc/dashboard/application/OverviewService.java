package ru.hgd.sdlc.dashboard.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.flow.application.FlowService;
import ru.hgd.sdlc.flow.domain.FlowStatus;
import ru.hgd.sdlc.flow.domain.FlowVersion;
import ru.hgd.sdlc.project.domain.Project;
import ru.hgd.sdlc.project.infrastructure.ProjectRepository;
import ru.hgd.sdlc.runtime.domain.GateStatus;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.application.service.RuntimeQueryService;
import ru.hgd.sdlc.runtime.infrastructure.AuditEventRepository;
import ru.hgd.sdlc.runtime.infrastructure.GateInstanceRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;

@Service
public class OverviewService {
    private static final List<RunStatus> ACTIVE_RUN_STATUSES = List.of(
            RunStatus.CREATED,
            RunStatus.RUNNING,
            RunStatus.WAITING_GATE
    );
    private static final List<GateStatus> OPEN_GATE_STATUSES = List.of(
            GateStatus.AWAITING_INPUT,
            GateStatus.AWAITING_DECISION,
            GateStatus.FAILED_VALIDATION
    );

    private final RunRepository runRepository;
    private final GateInstanceRepository gateInstanceRepository;
    private final AuditEventRepository auditEventRepository;
    private final ProjectRepository projectRepository;
    private final FlowService flowService;
    private final RuntimeQueryService runtimeQueryService;

    public OverviewService(
            RunRepository runRepository,
            GateInstanceRepository gateInstanceRepository,
            AuditEventRepository auditEventRepository,
            ProjectRepository projectRepository,
            FlowService flowService,
            RuntimeQueryService runtimeQueryService
    ) {
        this.runRepository = runRepository;
        this.gateInstanceRepository = gateInstanceRepository;
        this.auditEventRepository = auditEventRepository;
        this.projectRepository = projectRepository;
        this.flowService = flowService;
        this.runtimeQueryService = runtimeQueryService;
    }

    @Transactional(readOnly = true)
    public OverviewData getOverview(User user) {
        List<FlowVersion> latestFlows = flowService.listLatest();

        long activeRuns = runRepository.countByStatusIn(ACTIVE_RUN_STATUSES);
        long waitingGates = gateInstanceRepository.countByStatusIn(OPEN_GATE_STATUSES);
        long awaitingDecisionGates = gateInstanceRepository.countByStatusIn(List.of(GateStatus.AWAITING_DECISION));
        long publishedFlows = latestFlows.stream().filter((flow) -> flow.getStatus() == FlowStatus.PUBLISHED).count();
        long draftFlows = latestFlows.stream().filter((flow) -> flow.getStatus() == FlowStatus.DRAFT).count();
        long auditEvents24h = auditEventRepository.countByEventTimeGreaterThanEqual(Instant.now().minus(24, ChronoUnit.HOURS));

        List<RunEntity> latestRuns = take(runRepository.findAllByOrderByCreatedAtDesc(), 5);
        Map<UUID, Project> projectsById = projectRepository.findAllById(extractProjectIds(latestRuns)).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));

        List<RecentRunItem> recentRuns = latestRuns.stream()
                .map((run) -> new RecentRunItem(
                        run.getId(),
                        resolveProjectName(run.getProjectId(), projectsById),
                        run.getFlowCanonicalName(),
                        run.getStatus().name().toLowerCase(),
                        run.getCreatedAt()
                ))
                .toList();

        List<GateInboxItem> gateInbox = take(runtimeQueryService.findGateInbox(user), 5).stream()
                .map((gate) -> new GateInboxItem(
                        gate.getId(),
                        gate.getRunId(),
                        gate.getNodeId(),
                        gate.getGateKind().name().toLowerCase(),
                        gate.getStatus().name().toLowerCase(),
                        gate.getAssigneeRole(),
                        gate.getOpenedAt()
                ))
                .toList();

        Metrics metrics = new Metrics(
                activeRuns,
                waitingGates,
                awaitingDecisionGates,
                publishedFlows,
                draftFlows,
                auditEvents24h
        );
        return new OverviewData(metrics, recentRuns, gateInbox);
    }

    private String resolveProjectName(UUID projectId, Map<UUID, Project> projectsById) {
        Project project = projectsById.get(projectId);
        return project == null ? projectId.toString() : project.getName();
    }

    private Set<UUID> extractProjectIds(List<RunEntity> runs) {
        return runs.stream().map(RunEntity::getProjectId).collect(Collectors.toSet());
    }

    private <T> List<T> take(List<T> items, int limit) {
        if (items.size() <= limit) {
            return items;
        }
        return items.subList(0, limit);
    }

    public record OverviewData(
            Metrics metrics,
            List<RecentRunItem> recentRuns,
            List<GateInboxItem> gateInbox
    ) {
    }

    public record Metrics(
            long activeRuns,
            long waitingGates,
            long awaitingDecisionGates,
            long publishedFlows,
            long draftFlows,
            long auditEvents24h
    ) {
    }

    public record RecentRunItem(
            UUID runId,
            String project,
            String flow,
            String status,
            Instant createdAt
    ) {
    }

    public record GateInboxItem(
            UUID gateId,
            UUID runId,
            String title,
            String gateKind,
            String status,
            String role,
            Instant openedAt
    ) {
    }
}

package ru.hgd.sdlc.runtime.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import ru.hgd.sdlc.auth.domain.Role;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.runtime.application.dto.GateChangesResult;
import ru.hgd.sdlc.runtime.application.port.ProcessExecutionPort;
import ru.hgd.sdlc.runtime.application.port.WorkspacePort;
import ru.hgd.sdlc.runtime.domain.GateInstanceEntity;
import ru.hgd.sdlc.runtime.domain.GateKind;
import ru.hgd.sdlc.runtime.domain.GateStatus;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.infrastructure.GateInstanceRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;
import ru.hgd.sdlc.settings.application.SettingsService;

class GitReviewServicePortTest {

    @Test
    void collectGateChangesUsesGitAndWorkspacePorts() throws Exception {
        UUID gateId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        RunRepository runRepository = Mockito.mock(RunRepository.class);
        GateInstanceRepository gateInstanceRepository = Mockito.mock(GateInstanceRepository.class);
        SettingsService settingsService = Mockito.mock(SettingsService.class);
        ProcessExecutionPort processExecutionPort = Mockito.mock(ProcessExecutionPort.class);
        WorkspacePort workspacePort = Mockito.mock(WorkspacePort.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GitReviewService service = new GitReviewService(
                runRepository,
                gateInstanceRepository,
                objectMapper,
                settingsService,
                processExecutionPort,
                workspacePort
        );

        String flowSnapshotJson = objectMapper.writeValueAsString(Map.of(
                "nodes", List.of(Map.of("id", "review-node"))
        ));
        RunEntity run = RunEntity.builder()
                .id(runId)
                .workspaceRoot("/tmp/runtime-review-" + runId)
                .flowSnapshotJson(flowSnapshotJson)
                .build();
        GateInstanceEntity gate = GateInstanceEntity.builder()
                .id(gateId)
                .runId(runId)
                .nodeId("review-node")
                .gateKind(GateKind.HUMAN_APPROVAL)
                .status(GateStatus.AWAITING_DECISION)
                .openedAt(Instant.now())
                .build();
        User reviewer = User.builder()
                .id(UUID.randomUUID())
                .username("reviewer")
                .displayName("Reviewer")
                .role(Role.TECH_APPROVER)
                .roles(Set.of(Role.TECH_APPROVER))
                .passwordHash("test")
                .enabled(true)
                .createdAt(Instant.now())
                .build();

        Mockito.when(gateInstanceRepository.findById(gateId)).thenReturn(Optional.of(gate));
        Mockito.when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        Mockito.when(settingsService.getAiTimeoutSeconds()).thenReturn(30);
        Mockito.when(workspacePort.isDirectory(Mockito.any())).thenReturn(false);
        Mockito.when(processExecutionPort.execute(Mockito.any()))
                .thenReturn(new ProcessExecutionPort.ProcessExecutionResult(
                        0,
                        " M README.md\n",
                        "",
                        "status.out",
                        "status.err"
                ))
                .thenReturn(new ProcessExecutionPort.ProcessExecutionResult(
                        0,
                        "2\t1\tREADME.md\n",
                        "",
                        "numstat.out",
                        "numstat.err"
                ));

        GateChangesResult result = service.collectGateChanges(gateId, reviewer);

        Assertions.assertEquals(1, result.filesChanged());
        Assertions.assertEquals(2, result.addedLines());
        Assertions.assertEquals(1, result.removedLines());
        Assertions.assertEquals("README.md", result.gitChanges().get(0).path());
        Assertions.assertEquals("modified", result.gitChanges().get(0).status());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProcessExecutionPort.ProcessExecutionRequest> captor =
                ArgumentCaptor.forClass(ProcessExecutionPort.ProcessExecutionRequest.class);
        Mockito.verify(processExecutionPort, Mockito.times(2)).execute(captor.capture());
        List<ProcessExecutionPort.ProcessExecutionRequest> requests = captor.getAllValues();
        Assertions.assertEquals(
                List.of("git", "status", "--porcelain", "--untracked-files=all"),
                requests.get(0).command()
        );
        Assertions.assertEquals(List.of("git", "diff", "--numstat"), requests.get(1).command());
    }
}

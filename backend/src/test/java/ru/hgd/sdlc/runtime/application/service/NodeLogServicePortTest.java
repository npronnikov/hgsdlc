package ru.hgd.sdlc.runtime.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.hgd.sdlc.runtime.application.dto.NodeLogResult;
import ru.hgd.sdlc.runtime.application.port.WorkspacePort;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.NodeExecutionStatus;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.infrastructure.NodeExecutionRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;
import ru.hgd.sdlc.settings.application.SettingsService;

class NodeLogServicePortTest {

    @Test
    void readsRawLogViaWorkspacePort() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID nodeExecutionId = UUID.randomUUID();
        RunRepository runRepository = Mockito.mock(RunRepository.class);
        NodeExecutionRepository nodeExecutionRepository = Mockito.mock(NodeExecutionRepository.class);
        SettingsService settingsService = Mockito.mock(SettingsService.class);
        WorkspacePort workspacePort = Mockito.mock(WorkspacePort.class);
        NodeLogService service = new NodeLogService(
                runRepository,
                nodeExecutionRepository,
                settingsService,
                new ObjectMapper(),
                workspacePort
        );

        RunEntity run = RunEntity.builder().id(runId).workspaceRoot("/tmp/runtime-" + runId).build();
        NodeExecutionEntity execution = NodeExecutionEntity.builder()
                .id(nodeExecutionId)
                .runId(runId)
                .nodeId("command-node")
                .nodeKind("command")
                .attemptNo(1)
                .status(NodeExecutionStatus.RUNNING)
                .startedAt(Instant.now())
                .build();
        Path logPath = Path.of(run.getWorkspaceRoot())
                .resolve(".hgsdlc")
                .resolve("nodes")
                .resolve("command-node")
                .resolve("attempt-1")
                .resolve("command.stdout.log");

        Mockito.when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        Mockito.when(nodeExecutionRepository.findByIdAndRunId(nodeExecutionId, runId)).thenReturn(Optional.of(execution));
        Mockito.when(workspacePort.exists(logPath)).thenReturn(true);
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        Mockito.when(workspacePort.readChunk(logPath, 0L, 256 * 1024))
                .thenReturn(new WorkspacePort.ReadChunkResult(0L, bytes.length, bytes, bytes.length));

        NodeLogResult result = service.readNodeLog(runId, nodeExecutionId, 0L);

        Assertions.assertEquals("hello", result.content());
        Assertions.assertEquals(5L, result.offset());
        Assertions.assertTrue(result.running());
    }

    @Test
    void parsesAiChunkViaWorkspacePort() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID nodeExecutionId = UUID.randomUUID();
        RunRepository runRepository = Mockito.mock(RunRepository.class);
        NodeExecutionRepository nodeExecutionRepository = Mockito.mock(NodeExecutionRepository.class);
        SettingsService settingsService = Mockito.mock(SettingsService.class);
        WorkspacePort workspacePort = Mockito.mock(WorkspacePort.class);
        NodeLogService service = new NodeLogService(
                runRepository,
                nodeExecutionRepository,
                settingsService,
                new ObjectMapper(),
                workspacePort
        );

        RunEntity run = RunEntity.builder().id(runId).workspaceRoot("/tmp/runtime-" + runId).build();
        NodeExecutionEntity execution = NodeExecutionEntity.builder()
                .id(nodeExecutionId)
                .runId(runId)
                .nodeId("ai-node")
                .nodeKind("ai")
                .attemptNo(2)
                .status(NodeExecutionStatus.SUCCEEDED)
                .startedAt(Instant.now())
                .finishedAt(Instant.now())
                .build();
        Path logPath = Path.of(run.getWorkspaceRoot())
                .resolve(".hgsdlc")
                .resolve("nodes")
                .resolve("ai-node")
                .resolve("attempt-2")
                .resolve("agent.stdout.log");

        String jsonLine = "{\"type\":\"result\",\"output_text\":\"Generated\"}\n";
        byte[] bytes = jsonLine.getBytes(StandardCharsets.UTF_8);
        Mockito.when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        Mockito.when(nodeExecutionRepository.findByIdAndRunId(nodeExecutionId, runId)).thenReturn(Optional.of(execution));
        Mockito.when(workspacePort.exists(logPath)).thenReturn(true);
        Mockito.when(workspacePort.readChunk(logPath, 0L, 256 * 1024))
                .thenReturn(new WorkspacePort.ReadChunkResult(0L, bytes.length, bytes, bytes.length));

        NodeLogResult result = service.readNodeLog(runId, nodeExecutionId, 0L);

        Assertions.assertEquals("Generated", result.content());
        Assertions.assertEquals(bytes.length, result.offset());
        Assertions.assertFalse(result.running());
    }
}

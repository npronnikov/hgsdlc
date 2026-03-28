package ru.hgd.sdlc.runtime.infrastructure.git;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.hgd.sdlc.runtime.application.port.ProcessExecutionPort;

class DefaultGitAdapterTest {

    @Test
    void delegatesToProcessExecutionPort() throws Exception {
        ProcessExecutionPort processExecutionPort = Mockito.mock(ProcessExecutionPort.class);
        DefaultGitAdapter adapter = new DefaultGitAdapter(processExecutionPort);
        ProcessExecutionPort.ProcessExecutionRequest request = new ProcessExecutionPort.ProcessExecutionRequest(
                UUID.randomUUID(),
                List.of("git", "status"),
                Path.of("/tmp"),
                10,
                Path.of("/tmp/stdout.log"),
                Path.of("/tmp/stderr.log"),
                true
        );
        ProcessExecutionPort.ProcessExecutionResult expected = new ProcessExecutionPort.ProcessExecutionResult(
                0,
                "ok",
                "",
                "/tmp/stdout.log",
                "/tmp/stderr.log"
        );
        Mockito.when(processExecutionPort.execute(request)).thenReturn(expected);

        ProcessExecutionPort.ProcessExecutionResult actual = adapter.runGit(request);

        Assertions.assertEquals(expected, actual);
    }

    @Test
    void propagatesExecutionErrors() throws Exception {
        ProcessExecutionPort processExecutionPort = Mockito.mock(ProcessExecutionPort.class);
        DefaultGitAdapter adapter = new DefaultGitAdapter(processExecutionPort);
        ProcessExecutionPort.ProcessExecutionRequest request = new ProcessExecutionPort.ProcessExecutionRequest(
                UUID.randomUUID(),
                List.of("git", "status"),
                Path.of("/tmp"),
                10,
                Path.of("/tmp/stdout.log"),
                Path.of("/tmp/stderr.log"),
                true
        );
        Mockito.when(processExecutionPort.execute(request)).thenThrow(new IOException("boom"));

        Assertions.assertThrows(IOException.class, () -> adapter.runGit(request));
    }
}


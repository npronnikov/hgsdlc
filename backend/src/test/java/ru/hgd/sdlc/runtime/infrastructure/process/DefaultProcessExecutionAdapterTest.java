package ru.hgd.sdlc.runtime.infrastructure.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import ru.hgd.sdlc.runtime.application.port.ProcessExecutionPort;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;

class DefaultProcessExecutionAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void executesCommandAndReadsLogs() throws Exception {
        RunRepository runRepository = Mockito.mock(RunRepository.class);
        DefaultProcessExecutionAdapter adapter = new DefaultProcessExecutionAdapter(runRepository);
        Path stdout = tempDir.resolve("logs/stdout.log");
        Path stderr = tempDir.resolve("logs/stderr.log");

        ProcessExecutionPort.ProcessExecutionResult result = adapter.execute(
                new ProcessExecutionPort.ProcessExecutionRequest(
                        null,
                        List.of("zsh", "-lc", "printf 'ok'; printf 'err' 1>&2"),
                        tempDir,
                        5,
                        stdout,
                        stderr,
                        true
                )
        );

        Assertions.assertEquals(0, result.exitCode());
        Assertions.assertEquals("ok", result.stdout());
        Assertions.assertEquals("err", result.stderr());
        Assertions.assertEquals(stdout.toString(), result.stdoutPath());
        Assertions.assertEquals(stderr.toString(), result.stderrPath());
    }

    @Test
    void throwsOnTimeout() {
        RunRepository runRepository = Mockito.mock(RunRepository.class);
        DefaultProcessExecutionAdapter adapter = new DefaultProcessExecutionAdapter(runRepository);
        Path stdout = tempDir.resolve("timeout/stdout.log");
        Path stderr = tempDir.resolve("timeout/stderr.log");

        IOException ex = Assertions.assertThrows(
                IOException.class,
                () -> adapter.execute(
                        new ProcessExecutionPort.ProcessExecutionRequest(
                                UUID.randomUUID(),
                                List.of("zsh", "-lc", "sleep 2"),
                                tempDir,
                                1,
                                stdout,
                                stderr,
                                true
                        )
                )
        );
        Assertions.assertTrue(ex.getMessage().contains("Process timeout"));
    }
}


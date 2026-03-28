package ru.hgd.sdlc.runtime.application.port;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public interface ProcessExecutionPort {
    ProcessExecutionResult execute(ProcessExecutionRequest request) throws IOException;

    record ProcessExecutionRequest(
            UUID runId,
            List<String> command,
            Path workingDirectory,
            int timeoutSeconds,
            Path stdoutPath,
            Path stderrPath,
            boolean cancelOnRunCancellation
    ) {}

    record ProcessExecutionResult(
            int exitCode,
            String stdout,
            String stderr,
            String stdoutPath,
            String stderrPath
    ) {}

    class ProcessCancelledException extends IOException {
        public ProcessCancelledException(String message) {
            super(message);
        }
    }
}

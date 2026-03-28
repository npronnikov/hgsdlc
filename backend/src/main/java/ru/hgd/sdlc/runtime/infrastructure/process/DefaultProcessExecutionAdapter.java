package ru.hgd.sdlc.runtime.infrastructure.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import ru.hgd.sdlc.runtime.application.port.ProcessExecutionPort;
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;

@Component
public class DefaultProcessExecutionAdapter implements ProcessExecutionPort {
    private static final int CANCEL_POLL_INTERVAL_SECONDS = 5;

    private final RunRepository runRepository;

    public DefaultProcessExecutionAdapter(RunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    public ProcessExecutionResult execute(ProcessExecutionRequest request) throws IOException {
        if (request.stdoutPath() != null && request.stdoutPath().getParent() != null) {
            Files.createDirectories(request.stdoutPath().getParent());
        }
        if (request.stderrPath() != null && request.stderrPath().getParent() != null) {
            Files.createDirectories(request.stderrPath().getParent());
        }
        ProcessBuilder pb = new ProcessBuilder(request.command());
        if (request.workingDirectory() != null) {
            pb.directory(request.workingDirectory().toFile());
        }
        pb.redirectOutput(request.stdoutPath().toFile());
        pb.redirectError(request.stderrPath().toFile());
        Process process = pb.start();
        try {
            long deadlineMs = System.currentTimeMillis() + (long) request.timeoutSeconds() * 1000L;
            while (process.isAlive()) {
                long remainingMs = deadlineMs - System.currentTimeMillis();
                if (remainingMs <= 0) {
                    process.destroyForcibly();
                    throw new IOException("Process timeout after " + request.timeoutSeconds() + "s");
                }
                long pollMs = Math.min(CANCEL_POLL_INTERVAL_SECONDS * 1000L, remainingMs);
                boolean finished = process.waitFor(pollMs, TimeUnit.MILLISECONDS);
                if (finished) {
                    break;
                }
                if (request.cancelOnRunCancellation() && request.runId() != null) {
                    RunStatus currentStatus = runRepository.findById(request.runId())
                            .map((run) -> run.getStatus())
                            .orElse(null);
                    if (currentStatus == RunStatus.CANCELLED) {
                        process.destroyForcibly();
                        throw new ProcessCancelledException("Run cancelled by user");
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Process interrupted", ex);
        }
        return new ProcessExecutionResult(
                process.exitValue(),
                readFile(request.stdoutPath()),
                readFile(request.stderrPath()),
                request.stdoutPath().toString(),
                request.stderrPath().toString()
        );
    }

    private String readFile(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return "";
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

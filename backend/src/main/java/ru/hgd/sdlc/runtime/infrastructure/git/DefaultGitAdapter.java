package ru.hgd.sdlc.runtime.infrastructure.git;

import java.io.IOException;
import org.springframework.stereotype.Component;
import ru.hgd.sdlc.runtime.application.port.GitPort;
import ru.hgd.sdlc.runtime.application.port.ProcessExecutionPort;

@Component
public class DefaultGitAdapter implements GitPort {
    private final ProcessExecutionPort processExecutionPort;

    public DefaultGitAdapter(ProcessExecutionPort processExecutionPort) {
        this.processExecutionPort = processExecutionPort;
    }

    @Override
    public ProcessExecutionPort.ProcessExecutionResult runGit(ProcessExecutionPort.ProcessExecutionRequest request)
            throws IOException {
        return processExecutionPort.execute(request);
    }
}

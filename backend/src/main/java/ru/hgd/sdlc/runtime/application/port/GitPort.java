package ru.hgd.sdlc.runtime.application.port;

import java.io.IOException;

public interface GitPort {
    ProcessExecutionPort.ProcessExecutionResult runGit(ProcessExecutionPort.ProcessExecutionRequest request) throws IOException;
}

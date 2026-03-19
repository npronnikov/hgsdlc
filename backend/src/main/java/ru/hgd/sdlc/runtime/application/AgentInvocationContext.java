package ru.hgd.sdlc.runtime.application;

import java.nio.file.Path;
import java.util.List;

record AgentInvocationContext(
        Path workingDirectory,
        List<String> command,
        Path promptPath,
        Path rulePath,
        Path skillsRoot,
        Path stdoutPath,
        Path stderrPath,
        AgentPromptBuilder.AgentPromptPackage promptPackage
) {}

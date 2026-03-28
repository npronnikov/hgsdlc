package ru.hgd.sdlc.runtime.application.service;

import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;

public interface NodeExecutor {
    String nodeKind();

    boolean execute(
            RunStepService stepService,
            RunEntity run,
            NodeModel node,
            NodeExecutionEntity execution
    );
}

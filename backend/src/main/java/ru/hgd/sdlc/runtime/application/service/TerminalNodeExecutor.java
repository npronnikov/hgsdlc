package ru.hgd.sdlc.runtime.application.service;

import org.springframework.stereotype.Service;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;

@Service
public class TerminalNodeExecutor implements NodeExecutor {
    @Override
    public String nodeKind() {
        return "terminal";
    }

    @Override
    public boolean execute(RunStepService stepService, RunEntity run, NodeModel node, NodeExecutionEntity execution) {
        return stepService.completeTerminalNode(run, node, execution);
    }
}

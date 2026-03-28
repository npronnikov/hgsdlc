package ru.hgd.sdlc.runtime.application.service;

import org.springframework.stereotype.Service;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.runtime.domain.GateKind;
import ru.hgd.sdlc.runtime.domain.GateStatus;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;

@Service
public class HumanInputNodeExecutor implements NodeExecutor {
    @Override
    public String nodeKind() {
        return "human_input";
    }

    @Override
    public boolean execute(RunStepService stepService, RunEntity run, NodeModel node, NodeExecutionEntity execution) {
        return stepService.openGate(run, node, execution, GateKind.HUMAN_INPUT, GateStatus.AWAITING_INPUT);
    }
}

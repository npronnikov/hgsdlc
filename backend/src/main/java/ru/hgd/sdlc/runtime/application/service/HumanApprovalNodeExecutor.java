package ru.hgd.sdlc.runtime.application.service;

import org.springframework.stereotype.Service;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.runtime.domain.GateKind;
import ru.hgd.sdlc.runtime.domain.GateStatus;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;

@Service
public class HumanApprovalNodeExecutor implements NodeExecutor {
    @Override
    public String nodeKind() {
        return "human_approval";
    }

    @Override
    public boolean execute(RunStepService stepService, RunEntity run, NodeModel node, NodeExecutionEntity execution) {
        return stepService.skipOrOpenGate(run, node, execution, GateKind.HUMAN_APPROVAL, GateStatus.AWAITING_DECISION);
    }
}

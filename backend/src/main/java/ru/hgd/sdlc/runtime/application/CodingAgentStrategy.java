package ru.hgd.sdlc.runtime.application;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;

public interface CodingAgentStrategy {
    String codingAgent();

    AgentInvocationContext materializeWorkspace(MaterializationRequest request) throws CodingAgentException;

    record MaterializationRequest(
            RunEntity run,
            FlowModel flowModel,
            NodeModel node,
            NodeExecutionEntity execution,
            List<Map<String, Object>> resolvedContext,
            Path projectRoot,
            Path nodeExecutionRoot,
            List<AgentPromptBuilder.WorkflowProgressEntry> workflowProgress
    ) {}
}

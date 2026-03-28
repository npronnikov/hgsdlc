package ru.hgd.sdlc.runtime.application.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;

@Service
public class NodeExecutionRouter {
    private final Map<String, NodeExecutor> executorsByKind;

    public NodeExecutionRouter(List<NodeExecutor> executors) {
        this.executorsByKind = (executors == null ? List.<NodeExecutor>of() : executors)
                .stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        NodeExecutor::nodeKind,
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    public boolean execute(
            RunStepService stepService,
            RunEntity run,
            NodeModel node,
            NodeExecutionEntity execution,
            String nodeKind
    ) {
        NodeExecutor executor = executorsByKind.get(nodeKind);
        if (executor == null) {
            throw new RunStepService.NodeFailureException(
                    "UNSUPPORTED_NODE_KIND",
                    "Unsupported node kind: " + nodeKind,
                    true
            );
        }
        return executor.execute(stepService, run, node, execution);
    }
}

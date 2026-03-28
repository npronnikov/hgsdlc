package ru.hgd.sdlc.runtime;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.runtime.application.service.NodeExecutionRouter;
import ru.hgd.sdlc.runtime.application.service.NodeExecutor;
import ru.hgd.sdlc.runtime.application.service.RunStepService;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;

class NodeExecutionRouterTest {

    @Test
    void dispatchesToMatchingExecutor() {
        AtomicBoolean called = new AtomicBoolean(false);
        NodeExecutor aiExecutor = new NodeExecutor() {
            @Override
            public String nodeKind() {
                return "ai";
            }

            @Override
            public boolean execute(RunStepService stepService, RunEntity run, NodeModel node, NodeExecutionEntity execution) {
                called.set(true);
                return true;
            }
        };

        NodeExecutionRouter router = new NodeExecutionRouter(List.of(aiExecutor));
        boolean result = router.execute(
                null,
                RunEntity.builder().id(UUID.randomUUID()).build(),
                NodeModel.builder().id("n1").nodeKind("ai").build(),
                NodeExecutionEntity.builder().id(UUID.randomUUID()).build(),
                "ai"
        );

        Assertions.assertTrue(result);
        Assertions.assertTrue(called.get());
    }

    @Test
    void throwsForUnknownNodeKind() {
        NodeExecutionRouter router = new NodeExecutionRouter(List.of());

        RunStepService.NodeFailureException ex = Assertions.assertThrows(
                RunStepService.NodeFailureException.class,
                () -> router.execute(
                        null,
                        RunEntity.builder().id(UUID.randomUUID()).build(),
                        NodeModel.builder().id("n1").nodeKind("unknown").build(),
                        NodeExecutionEntity.builder().id(UUID.randomUUID()).build(),
                        "unknown"
                )
        );

        Assertions.assertEquals("UNSUPPORTED_NODE_KIND", ex.getErrorCode());
        Assertions.assertTrue(ex.getMessage().contains("Unsupported node kind"));
    }
}

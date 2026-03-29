package ru.hgd.sdlc.runtime.application.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.auth.domain.User;
import ru.hgd.sdlc.runtime.application.command.ApproveGateCommand;
import ru.hgd.sdlc.runtime.application.command.CreateRunCommand;
import ru.hgd.sdlc.runtime.application.command.ReworkGateCommand;
import ru.hgd.sdlc.runtime.application.command.SubmitInputCommand;
import ru.hgd.sdlc.runtime.application.dto.GateActionResult;
import ru.hgd.sdlc.runtime.domain.RunEntity;

@Service
public class RuntimeCommandService {
    private static final Logger log = LoggerFactory.getLogger(RuntimeCommandService.class);

    private final RunLifecycleService runLifecycleService;
    private final RunStepService runStepService;
    private final GateDecisionService gateDecisionService;
    private final RunPublishService runPublishService;
    private final TaskExecutor taskExecutor;

    public RuntimeCommandService(
            RunLifecycleService runLifecycleService,
            RunStepService runStepService,
            GateDecisionService gateDecisionService,
            RunPublishService runPublishService,
            TaskExecutor taskExecutor
    ) {
        this.runLifecycleService = runLifecycleService;
        this.runStepService = runStepService;
        this.gateDecisionService = gateDecisionService;
        this.runPublishService = runPublishService;
        this.taskExecutor = taskExecutor;
    }

    public RunEntity createRun(CreateRunCommand command, User user) {
        return runLifecycleService.createRun(command, user);
    }

    public void startRun(UUID runId) {
        runLifecycleService.startRun(runId);
    }

    public RunEntity resumeRun(UUID runId) {
        return runLifecycleService.resumeRun(runId);
    }

    public RunEntity cancelRun(UUID runId, User user) {
        return runLifecycleService.cancelRun(runId, user);
    }

    public GateActionResult submitInput(UUID gateId, SubmitInputCommand command, User user) {
        return gateDecisionService.submitInput(gateId, command, user);
    }

    public GateActionResult approveGate(UUID gateId, ApproveGateCommand command, User user) {
        return gateDecisionService.approveGate(gateId, command, user);
    }

    public GateActionResult requestRework(UUID gateId, ReworkGateCommand command, User user) {
        return gateDecisionService.requestRework(gateId, command, user);
    }

    public void recoverActiveRuns() {
        runLifecycleService.recoverActiveRuns();
    }

    public void processRunStep(UUID runId) {
        runStepService.processRunStep(runId);
    }

    public RunEntity retryPublish(UUID runId, User user) {
        return runPublishService.retryPublish(runId, user == null ? "system" : user.getUsername());
    }

    public void dispatchPublishRun(UUID runId) {
        runPublishService.dispatchPublish(runId);
    }

    public void dispatchStartRun(UUID runId) {
        taskExecutor.execute(() -> {
            try {
                startRun(runId);
            } catch (Exception ex) {
                log.error("Failed to start run {}", runId, ex);
            }
        });
    }

    public void dispatchProcessRunStep(UUID runId) {
        taskExecutor.execute(() -> {
            try {
                processRunStep(runId);
            } catch (Exception ex) {
                log.error("Async tick failed for run {}", runId, ex);
            }
        });
    }
}

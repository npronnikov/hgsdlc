package ru.hgd.sdlc.runtime.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class RuntimeRecoveryInitializer implements ApplicationRunner {
    private final RuntimeService runtimeService;

    public RuntimeRecoveryInitializer(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        runtimeService.recoverActiveRuns();
    }
}

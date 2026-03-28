package ru.hgd.sdlc.runtime.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.hgd.sdlc.runtime.application.service.RuntimeCommandService;

@Component
public class RuntimeRecoveryInitializer implements ApplicationRunner {
    private final RuntimeCommandService runtimeCommandService;

    public RuntimeRecoveryInitializer(RuntimeCommandService runtimeCommandService) {
        this.runtimeCommandService = runtimeCommandService;
    }

    @Override
    public void run(ApplicationArguments args) {
        runtimeCommandService.recoverActiveRuns();
    }
}

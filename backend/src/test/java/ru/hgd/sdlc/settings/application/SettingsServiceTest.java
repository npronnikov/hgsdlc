package ru.hgd.sdlc.settings.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.hgd.sdlc.settings.domain.SystemSetting;
import ru.hgd.sdlc.settings.infrastructure.SystemSettingRepository;

class SettingsServiceTest {

    @Test
    void defaultAgentInitCommandsMatchConfiguredBootstrapPrompts() {
        SystemSettingRepository repository = Mockito.mock(SystemSettingRepository.class);
        Mockito.when(repository.findById(Mockito.anyString())).thenReturn(Optional.empty());

        SettingsService service = new SettingsService(repository);

        Assertions.assertEquals(
                "qwen -p \"/init\" --approval-mode yolo",
                service.getRuntimeAgentInitCommand("qwen")
        );
        Assertions.assertEquals(
                "claude -p \"/init\" --permission-mode acceptEdits",
                service.getRuntimeAgentInitCommand("claude")
        );
    }

    @Test
    void autoInitWhenNoRuleIsPersistedAndReturnedInRuntimeSettings() {
        Map<String, SystemSetting> storage = new HashMap<>();
        SystemSettingRepository repository = Mockito.mock(SystemSettingRepository.class);
        Mockito.when(repository.findById(Mockito.anyString()))
                .thenAnswer((invocation) -> Optional.ofNullable(storage.get(invocation.getArgument(0))));
        Mockito.when(repository.save(Mockito.any(SystemSetting.class)))
                .thenAnswer((invocation) -> {
                    SystemSetting setting = invocation.getArgument(0);
                    storage.put(setting.getSettingKey(), setting);
                    return setting;
                });

        SettingsService service = new SettingsService(repository);
        service.updateRuntimeSettings(
                "/tmp/workspace",
                "qwen",
                900,
                "en",
                "qwen --approval-mode yolo --channel CI --output-format stream-json --include-partial-messages {{PROMPT}}",
                "qwen init",
                true,
                "",
                false,
                "tester"
        );

        String key = SettingsService.AGENT_AUTO_INIT_WHEN_NO_RULE_KEY_PREFIX + "qwen";
        Assertions.assertEquals("true", storage.get(key).getSettingValue());
        Assertions.assertTrue(service.isRuntimeAgentAutoInitWhenNoRule("qwen"));
        Assertions.assertTrue(service.getRuntimeSettings().autoInitWhenNoRule());
    }

    @Test
    void autoInitWhenNoRuleKeepsPreviousValueWhenUpdateInputIsNull() {
        Map<String, SystemSetting> storage = new HashMap<>();
        SystemSettingRepository repository = Mockito.mock(SystemSettingRepository.class);
        Mockito.when(repository.findById(Mockito.anyString()))
                .thenAnswer((invocation) -> Optional.ofNullable(storage.get(invocation.getArgument(0))));
        Mockito.when(repository.save(Mockito.any(SystemSetting.class)))
                .thenAnswer((invocation) -> {
                    SystemSetting setting = invocation.getArgument(0);
                    storage.put(setting.getSettingKey(), setting);
                    return setting;
                });

        SettingsService service = new SettingsService(repository);
        service.updateRuntimeSettings(
                "/tmp/workspace",
                "qwen",
                900,
                "en",
                "qwen --approval-mode yolo --channel CI --output-format stream-json --include-partial-messages {{PROMPT}}",
                "qwen init",
                true,
                "",
                false,
                "tester"
        );

        service.updateRuntimeSettings(
                "/tmp/workspace",
                "qwen",
                900,
                "en",
                "qwen --approval-mode yolo --channel CI --output-format stream-json --include-partial-messages {{PROMPT}}",
                "qwen init",
                null,
                "",
                false,
                "tester"
        );

        Assertions.assertTrue(service.isRuntimeAgentAutoInitWhenNoRule("qwen"));
        Assertions.assertTrue(service.getRuntimeSettings().autoInitWhenNoRule());
    }
}

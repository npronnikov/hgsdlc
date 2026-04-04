package ru.hgd.sdlc.settings.application;

import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.settings.domain.SystemSetting;
import ru.hgd.sdlc.settings.infrastructure.SystemSettingRepository;

@Component
public class CatalogSettingsSeedInitializer implements ApplicationRunner {
    private final SystemSettingRepository repository;
    private final CatalogSeedProperties properties;

    public CatalogSettingsSeedInitializer(SystemSettingRepository repository, CatalogSeedProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedIfAbsent(SettingsService.CATALOG_REPO_URL_KEY, properties.getRepoUrl());
        seedIfAbsent(SettingsService.CATALOG_DEFAULT_BRANCH_KEY, properties.getDefaultBranch());
    }

    private void seedIfAbsent(String key, String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || repository.existsById(key)) {
            return;
        }
        repository.save(SystemSetting.builder()
                .settingKey(key)
                .settingValue(normalized)
                .updatedAt(Instant.now())
                .updatedBy("seed")
                .build());
    }
}

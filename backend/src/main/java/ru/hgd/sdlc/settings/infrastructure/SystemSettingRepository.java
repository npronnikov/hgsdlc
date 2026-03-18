package ru.hgd.sdlc.settings.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.settings.domain.SystemSetting;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
}

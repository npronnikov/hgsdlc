package ru.hgd.sdlc.skill.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.domain.SkillVersion;

public interface SkillVersionRepository extends JpaRepository<SkillVersion, UUID> {
    Optional<SkillVersion> findFirstBySkillIdAndStatusOrderBySavedAtDesc(String skillId, SkillStatus status);

    Optional<SkillVersion> findFirstBySkillIdAndVersionOrderBySavedAtDesc(String skillId, String version);

    Optional<SkillVersion> findFirstByCanonicalNameAndStatus(String canonicalName, SkillStatus status);

    List<SkillVersion> findBySkillIdOrderBySavedAtDesc(String skillId);

    List<SkillVersion> findAllByOrderBySavedAtDesc();
}

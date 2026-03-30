package ru.hgd.sdlc.skill.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.skill.domain.SkillFileEntity;

public interface SkillFileRepository extends JpaRepository<SkillFileEntity, UUID> {
    List<SkillFileEntity> findBySkillVersionIdOrderByPathAsc(UUID skillVersionId);

    void deleteBySkillVersionId(UUID skillVersionId);
}

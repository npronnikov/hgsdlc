package ru.hgd.sdlc.skill.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.skill.domain.TagEntity;

public interface TagRepository extends JpaRepository<TagEntity, String> {
    List<TagEntity> findByCodeIn(List<String> codes);
}

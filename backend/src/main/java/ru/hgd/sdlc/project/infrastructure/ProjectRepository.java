package ru.hgd.sdlc.project.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hgd.sdlc.project.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findAllByOrderByUpdatedAtDesc();

    Optional<Project> findFirstByRepoUrl(String repoUrl);
}

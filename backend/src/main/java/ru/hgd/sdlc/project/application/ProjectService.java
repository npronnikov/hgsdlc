package ru.hgd.sdlc.project.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.project.api.ProjectCreateRequest;
import ru.hgd.sdlc.project.api.ProjectUpdateRequest;
import ru.hgd.sdlc.project.domain.Project;
import ru.hgd.sdlc.project.domain.ProjectStatus;
import ru.hgd.sdlc.project.infrastructure.ProjectRepository;

@Service
public class ProjectService {
    private static final String DEFAULT_BRANCH = "main";

    private final ProjectRepository repository;

    public ProjectService(ProjectRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Project> list() {
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Project get(UUID projectId) {
        return repository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
    }

    @Transactional
    public Project create(ProjectCreateRequest request) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }
        String name = normalize(request.name());
        String repoUrl = normalize(request.repoUrl());
        if (name == null) {
            throw new ValidationException("name is required");
        }
        if (repoUrl == null) {
            throw new ValidationException("repo_url is required");
        }
        String defaultBranch = normalize(request.defaultBranch());
        if (defaultBranch == null) {
            defaultBranch = DEFAULT_BRANCH;
        }

        Instant now = Instant.now();
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName(name);
        project.setRepoUrl(repoUrl);
        project.setDefaultBranch(defaultBranch);
        project.setStatus(ProjectStatus.ACTIVE);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        return repository.save(project);
    }

    @Transactional
    public Project update(UUID projectId, ProjectUpdateRequest request) {
        if (request == null) {
            throw new ValidationException("Request body is required");
        }
        if (request.resourceVersion() == null) {
            throw new ValidationException("resource_version is required");
        }
        Project project = get(projectId);
        if (project.getResourceVersion() != request.resourceVersion()) {
            throw new ConflictException("resource_version mismatch");
        }

        boolean updated = false;
        if (request.name() != null) {
            String name = normalize(request.name());
            if (name == null) {
                throw new ValidationException("name must not be blank");
            }
            project.setName(name);
            updated = true;
        }
        if (request.repoUrl() != null) {
            String repoUrl = normalize(request.repoUrl());
            if (repoUrl == null) {
                throw new ValidationException("repo_url must not be blank");
            }
            project.setRepoUrl(repoUrl);
            updated = true;
        }
        if (request.defaultBranch() != null) {
            String branch = normalize(request.defaultBranch());
            if (branch == null) {
                throw new ValidationException("default_branch must not be blank");
            }
            project.setDefaultBranch(branch);
            updated = true;
        }
        if (!updated) {
            throw new ValidationException("No fields to update");
        }
        project.setUpdatedAt(Instant.now());
        return repository.save(project);
    }

    @Transactional
    public Project archive(UUID projectId) {
        Project project = get(projectId);
        if (project.getStatus() != ProjectStatus.ARCHIVED) {
            project.setStatus(ProjectStatus.ARCHIVED);
            project.setUpdatedAt(Instant.now());
        }
        return repository.save(project);
    }

    @Transactional
    public void delete(UUID projectId) {
        archive(projectId);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

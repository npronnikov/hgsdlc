package ru.hgd.sdlc.project.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.hgd.sdlc.common.ConflictException;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.project.application.ProjectService;
import ru.hgd.sdlc.runtime.application.RuntimeService;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final RuntimeService runtimeService;

    public ProjectController(ProjectService projectService, RuntimeService runtimeService) {
        this.projectService = projectService;
        this.runtimeService = runtimeService;
    }

    @GetMapping
    public List<ProjectSummaryResponse> list() {
        return projectService.list().stream().map(ProjectSummaryResponse::from).toList();
    }

    @PostMapping
    public ProjectResponse create(@RequestBody ProjectCreateRequest request) {
        return ProjectResponse.from(projectService.create(request));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable UUID projectId) {
        return ProjectResponse.from(projectService.get(projectId));
    }

    @PatchMapping("/{projectId}")
    public ProjectResponse update(
            @PathVariable UUID projectId,
            @RequestBody ProjectUpdateRequest request
    ) {
        return ProjectResponse.from(projectService.update(projectId, request));
    }

    @PostMapping("/{projectId}/archive")
    public ProjectResponse archive(@PathVariable UUID projectId) {
        return ProjectResponse.from(projectService.archive(projectId));
    }

    @GetMapping("/{projectId}/runs")
    public List<RunSummaryResponse> runs(
            @PathVariable UUID projectId,
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        projectService.get(projectId);
        return runtimeService.listRunsByProject(projectId, limit).stream()
                .map((run) -> new RunSummaryResponse(
                        run.getId(),
                        run.getStatus().name().toLowerCase(),
                        run.getFlowCanonicalName(),
                        run.getCreatedAt()
                ))
                .toList();
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<String> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<String> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }
}

package ru.hgd.sdlc.skill.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.skill.application.SkillTemplateService;
import ru.hgd.sdlc.skill.domain.SkillProvider;

@RestController
@RequestMapping("/api/skill-templates")
public class SkillTemplateController {
    private final SkillTemplateService templateService;

    public SkillTemplateController(SkillTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/{provider}")
    public SkillTemplateResponse getTemplate(@PathVariable String provider) {
        SkillProvider parsed;
        try {
            parsed = SkillProvider.from(provider);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Unsupported provider: " + provider);
        }
        return SkillTemplateResponse.from(templateService.loadTemplate(parsed));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<String> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<String> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}

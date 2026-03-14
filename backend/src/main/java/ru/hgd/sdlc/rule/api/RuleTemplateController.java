package ru.hgd.sdlc.rule.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.rule.application.RuleTemplateService;
import ru.hgd.sdlc.rule.domain.RuleProvider;

@RestController
@RequestMapping("/api/rule-templates")
public class RuleTemplateController {
    private final RuleTemplateService templateService;

    public RuleTemplateController(RuleTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/{provider}")
    public RuleTemplateResponse getTemplate(@PathVariable String provider) {
        RuleProvider parsed;
        try {
            parsed = RuleProvider.from(provider);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Unsupported provider: " + provider);
        }
        RuleTemplateService.RuleTemplate template = templateService.loadTemplate(parsed);
        return RuleTemplateResponse.from(template);
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

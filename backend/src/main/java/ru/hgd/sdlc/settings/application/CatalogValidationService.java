package ru.hgd.sdlc.settings.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.common.JsonSchemaValidator;
import ru.hgd.sdlc.common.MarkdownFrontmatterParser;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.flow.application.FlowYamlParser;

@Service
public class CatalogValidationService {

    private final FlowYamlParser flowYamlParser;
    private final JsonSchemaValidator schemaValidator;
    private final MarkdownFrontmatterParser frontmatterParser;

    public CatalogValidationService(FlowYamlParser flowYamlParser, JsonSchemaValidator schemaValidator) {
        this.flowYamlParser = flowYamlParser;
        this.schemaValidator = schemaValidator;
        this.frontmatterParser = new MarkdownFrontmatterParser();
    }

    public void validateFlow(ParsedMetadata metadata) {
        try {
            flowYamlParser.parse(metadata.content());
        } catch (ValidationException ex) {
            throw new ValidationException("Catalog flow content does not match schema: "
                    + metadata.canonicalName() + ": " + ex.getMessage());
        }
    }

    public void validateRule(ParsedMetadata metadata) {
        MarkdownFrontmatterParser.ParsedMarkdown parsed;
        try {
            parsed = frontmatterParser.parse(metadata.content());
        } catch (ValidationException ex) {
            throw new ValidationException("Catalog rule markdown is invalid: "
                    + metadata.canonicalName() + ": " + ex.getMessage());
        }
        JsonNode merged = parsed.frontmatter().deepCopy();
        if (!(merged instanceof ObjectNode objectNode)) {
            throw new ValidationException("Catalog rule frontmatter must be a YAML object: " + metadata.canonicalName());
        }
        putIfMissing(objectNode, "id", metadata.id());
        putIfMissing(objectNode, "version", metadata.version());
        putIfMissing(objectNode, "canonical_name", metadata.canonicalName());
        putIfMissing(objectNode, "title", metadata.displayName());
        putIfMissing(objectNode, "description", metadata.optional("description"));
        try {
            schemaValidator.validate(objectNode, "schemas/rule.schema.json");
        } catch (ValidationException ex) {
            throw new ValidationException("Catalog rule content does not match schema: "
                    + metadata.canonicalName() + ": " + ex.getMessage());
        }
    }

    public void validateSkill(ParsedMetadata metadata) {
        MarkdownFrontmatterParser.ParsedMarkdown parsed;
        try {
            parsed = frontmatterParser.parse(metadata.content());
        } catch (ValidationException ex) {
            throw new ValidationException("Catalog skill markdown is invalid: "
                    + metadata.canonicalName() + ": " + ex.getMessage());
        }
        JsonNode merged = parsed.frontmatter().deepCopy();
        if (!(merged instanceof ObjectNode objectNode)) {
            throw new ValidationException("Catalog skill frontmatter must be a YAML object: " + metadata.canonicalName());
        }
        putIfMissing(objectNode, "id", metadata.id());
        putIfMissing(objectNode, "version", metadata.version());
        putIfMissing(objectNode, "canonical_name", metadata.canonicalName());
        putIfMissing(objectNode, "name", metadata.displayName());
        putIfMissing(objectNode, "description", metadata.optional("description"));
        try {
            schemaValidator.validate(objectNode, "schemas/skill.schema.json");
        } catch (ValidationException ex) {
            throw new ValidationException("Catalog skill content does not match schema: "
                    + metadata.canonicalName() + ": " + ex.getMessage());
        }
    }

    private void putIfMissing(ObjectNode node, String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        JsonNode existing = node.get(fieldName);
        if (existing == null || existing.isNull() || (existing.isTextual() && existing.asText().isBlank())) {
            node.put(fieldName, value);
        }
    }
}

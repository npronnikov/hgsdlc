package ru.hgd.sdlc.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class JsonSchemaValidator {
    private final JsonSchemaFactory schemaFactory;
    private final ObjectMapper objectMapper;
    private final Map<String, JsonSchema> schemaCache;

    public JsonSchemaValidator(ObjectMapper objectMapper) {
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        this.objectMapper = objectMapper;
        this.schemaCache = new ConcurrentHashMap<>();
    }

    public void validate(JsonNode instance, String schemaPath) {
        JsonSchema schema = schemaCache.computeIfAbsent(schemaPath, this::loadSchema);
        Set<ValidationMessage> errors = schema.validate(instance);
        if (!errors.isEmpty()) {
            String message = errors.stream().findFirst().map(ValidationMessage::getMessage).orElse("Schema validation failed");
            throw new ValidationException(message);
        }
    }

    private JsonSchema loadSchema(String schemaPath) {
        try (InputStream inputStream = new ClassPathResource(schemaPath).getInputStream()) {
            JsonNode schemaNode = objectMapper.readTree(inputStream);
            return schemaFactory.getSchema(schemaNode);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load schema: " + schemaPath, ex);
        }
    }
}

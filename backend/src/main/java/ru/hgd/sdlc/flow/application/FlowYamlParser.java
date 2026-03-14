package ru.hgd.sdlc.flow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import org.springframework.stereotype.Component;
import ru.hgd.sdlc.common.JsonSchemaValidator;
import ru.hgd.sdlc.common.ValidationException;
import ru.hgd.sdlc.flow.domain.FlowModel;

@Component
public class FlowYamlParser {
    private final ObjectMapper yamlMapper;
    private final JsonSchemaValidator schemaValidator;

    public FlowYamlParser(JsonSchemaValidator schemaValidator) {
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        this.schemaValidator = schemaValidator;
    }

    public FlowModel parse(String flowYaml) {
        if (flowYaml == null || flowYaml.isBlank()) {
            throw new ValidationException("flow_yaml is required");
        }
        JsonNode root = readTree(flowYaml);
        schemaValidator.validate(root, "schemas/flow.schema.json");
        validateNodes(root);
        FlowModel model = toModel(root);
        if ((model.getRuleRefs() == null || model.getRuleRefs().isEmpty()) && model.getRuleRef() != null) {
            model.setRuleRefs(java.util.List.of(model.getRuleRef()));
        }
        if (model.getRuleRefs() == null) {
            model.setRuleRefs(java.util.List.of());
        }
        return model;
    }

    private JsonNode readTree(String yaml) {
        try {
            return yamlMapper.readTree(yaml);
        } catch (IOException ex) {
            throw new ValidationException("Invalid flow_yaml: " + ex.getMessage());
        }
    }

    private FlowModel toModel(JsonNode root) {
        try {
            return yamlMapper.treeToValue(root, FlowModel.class);
        } catch (Exception ex) {
            throw new ValidationException("Unable to parse flow_yaml: " + ex.getMessage());
        }
    }

    private void validateNodes(JsonNode root) {
        JsonNode nodes = root.get("nodes");
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            String type = text(node.get("type"));
            String executorKind = text(node.get("executor_kind"));
            String gateKind = text(node.get("gate_kind"));
            String schemaPath = resolveSchema(type, executorKind, gateKind);
            if (schemaPath == null) {
                throw new ValidationException("Unsupported node type or kind");
            }
            schemaValidator.validate(node, schemaPath);
        }
    }

    private String resolveSchema(String type, String executorKind, String gateKind) {
        if (type == null) {
            return null;
        }
        String normalizedType = normalize(type);
        if ("executor".equals(normalizedType)) {
            String normalizedExecutor = normalize(executorKind);
            if ("ai".equals(normalizedExecutor)) {
                return "schemas/node-ai.schema.json";
            }
            if ("external_command".equals(normalizedExecutor)) {
                return "schemas/node-command.schema.json";
            }
        }
        if ("gate".equals(normalizedType)) {
            String normalizedGate = normalize(gateKind);
            if ("human_input".equals(normalizedGate)) {
                return "schemas/node-human-input-gate.schema.json";
            }
            if ("human_approval".equals(normalizedGate)) {
                return "schemas/node-human-approval-gate.schema.json";
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase().replace(' ', '_').replace('-', '_');
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }
}

package ru.hgd.sdlc.flow.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        normalizeVersion(root);
        normalizeNodeTypes(root);
        schemaValidator.validate(root, "schemas/flow.schema.json");
        validateNodes(root);
        FlowModel model = toModel(root);
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

    private void normalizeVersion(JsonNode root) {
        if (root == null || !root.isObject()) {
            return;
        }
        JsonNode version = root.get("version");
        if (version != null && version.isNumber()) {
            ((ObjectNode) root).put("version", version.asText());
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
            String schemaPath = resolveSchema(type);
            if (schemaPath == null) {
                throw new ValidationException("Unsupported node type or kind");
            }
            schemaValidator.validate(node, schemaPath);
        }
    }

    private void normalizeNodeTypes(JsonNode root) {
        if (root == null || !root.isObject()) {
            return;
        }
        JsonNode nodes = root.get("nodes");
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            if (!node.isObject()) {
                continue;
            }
            String type = text(node.get("type"));
            String nodeKind = text(node.get("node_kind"));
            String normalizedType = normalize(type);
            if ((nodeKind == null || nodeKind.isBlank()) && "executor".equals(normalizedType)) {
                nodeKind = text(node.get("executor_kind"));
            }
            if ((nodeKind == null || nodeKind.isBlank()) && "gate".equals(normalizedType)) {
                nodeKind = text(node.get("gate_kind"));
            }
            if (nodeKind == null || nodeKind.isBlank()) {
                continue;
            }
            if ("executor".equals(normalizedType) || "gate".equals(normalizedType)) {
                ((ObjectNode) node).put("type", nodeKind);
            }
        }
    }

    private String resolveSchema(String type) {
        if (type == null) {
            return null;
        }
        String normalizedType = normalize(type);
        if ("ai".equals(normalizedType)) {
            return "schemas/node-ai.schema.json";
        }
        if ("command".equals(normalizedType)) {
            return "schemas/node-command.schema.json";
        }
        if ("human_input".equals(normalizedType)) {
            return "schemas/node-human-input-gate.schema.json";
        }
        if ("human_approval".equals(normalizedType)) {
            return "schemas/node-human-approval-gate.schema.json";
        }
        if ("terminal".equals(normalizedType)) {
            return "schemas/node-terminal.schema.json";
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

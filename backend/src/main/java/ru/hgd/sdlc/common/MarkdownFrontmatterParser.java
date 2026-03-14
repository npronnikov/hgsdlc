package ru.hgd.sdlc.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MarkdownFrontmatterParser {
    private final ObjectMapper yamlMapper;

    public MarkdownFrontmatterParser() {
        YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .build();
        this.yamlMapper = new ObjectMapper(factory);
    }

    public ParsedMarkdown parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new ValidationException("Markdown content is required");
        }
        List<String> lines = splitLines(markdown);
        if (lines.isEmpty() || !"---".equals(lines.get(0).trim())) {
            throw new ValidationException("Frontmatter block must start with '---'");
        }

        int endIndex = -1;
        for (int i = 1; i < lines.size(); i++) {
            if ("---".equals(lines.get(i).trim())) {
                endIndex = i;
                break;
            }
        }
        if (endIndex == -1) {
            throw new ValidationException("Frontmatter block must end with '---'");
        }

        String yamlBlock = String.join("\n", lines.subList(1, endIndex));
        String body = String.join("\n", lines.subList(endIndex + 1, lines.size()));

        try {
            JsonNode node = yamlMapper.readTree(yamlBlock);
            if (node == null || !node.isObject()) {
                throw new ValidationException("Frontmatter must be a YAML object");
            }
            return new ParsedMarkdown((ObjectNode) node, body);
        } catch (IOException ex) {
            throw new ValidationException("Failed to parse frontmatter YAML: " + ex.getMessage());
        }
    }

    public String render(ObjectNode frontmatter, String body) {
        try {
            String yaml = yamlMapper.writeValueAsString(frontmatter);
            if (!yaml.endsWith("\n")) {
                yaml += "\n";
            }
            String safeBody = body == null ? "" : body;
            if (!safeBody.isEmpty() && !safeBody.startsWith("\n")) {
                return "---\n" + yaml + "---\n" + safeBody;
            }
            return "---\n" + yaml + "---" + safeBody;
        } catch (IOException ex) {
            throw new ValidationException("Failed to render frontmatter YAML: " + ex.getMessage());
        }
    }

    private List<String> splitLines(String markdown) {
        String normalized = markdown.replace("\r\n", "\n").replace("\r", "\n");
        String[] rawLines = normalized.split("\n", -1);
        List<String> lines = new ArrayList<>(rawLines.length);
        for (String line : rawLines) {
            lines.add(line);
        }
        return lines;
    }

    public record ParsedMarkdown(ObjectNode frontmatter, String body) {
    }
}

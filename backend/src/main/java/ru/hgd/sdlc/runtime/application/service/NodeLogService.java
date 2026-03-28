package ru.hgd.sdlc.runtime.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.runtime.application.dto.NodeLogResult;
import ru.hgd.sdlc.runtime.application.port.WorkspacePort;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.NodeExecutionStatus;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.infrastructure.NodeExecutionRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;
import ru.hgd.sdlc.settings.application.SettingsService;

@Service
public class NodeLogService {
    private static final Logger log = LoggerFactory.getLogger(NodeLogService.class);
    private static final int NODE_LOG_CHUNK_SIZE = 256 * 1024;

    private final RunRepository runRepository;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final WorkspacePort workspacePort;

    public NodeLogService(
            RunRepository runRepository,
            NodeExecutionRepository nodeExecutionRepository,
            SettingsService settingsService,
            ObjectMapper objectMapper,
            WorkspacePort workspacePort
    ) {
        this.runRepository = runRepository;
        this.nodeExecutionRepository = nodeExecutionRepository;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.workspacePort = workspacePort;
    }

    @Transactional(readOnly = true)
    public NodeLogResult readNodeLog(UUID runId, UUID nodeExecutionId, long offset) {
        RunEntity run = getRunEntity(runId);
        NodeExecutionEntity execution = nodeExecutionRepository.findByIdAndRunId(nodeExecutionId, runId)
                .orElseThrow(() -> new NotFoundException("Node execution not found: " + nodeExecutionId));
        Path nodeDir = resolveRunScopeRoot(resolveRunWorkspaceRoot(run))
                .resolve("nodes")
                .resolve(execution.getNodeId())
                .resolve("attempt-" + execution.getAttemptNo());
        String logFileName = "ai".equals(execution.getNodeKind()) ? "agent.stdout.log" : "command.stdout.log";
        Path logPath = nodeDir.resolve(logFileName);
        boolean running = execution.getStatus() == NodeExecutionStatus.RUNNING
                || execution.getStatus() == NodeExecutionStatus.CREATED;
        if (!workspacePort.exists(logPath)) {
            return new NodeLogResult("", offset, running);
        }
        if ("ai".equals(execution.getNodeKind())) {
            return readAiNodeLog(logPath, offset, running);
        }
        return readRawNodeLog(logPath, offset, running);
    }

    private NodeLogResult readRawNodeLog(Path logPath, long offset, boolean running) {
        try {
            WorkspacePort.ReadChunkResult chunk = workspacePort.readChunk(logPath, offset, NODE_LOG_CHUNK_SIZE);
            if (chunk.normalizedOffset() >= chunk.fileLength()) {
                return new NodeLogResult("", chunk.fileLength(), running);
            }
            if (chunk.bytesRead() <= 0) {
                return new NodeLogResult("", chunk.normalizedOffset(), running);
            }
            String content = new String(chunk.data(), 0, chunk.bytesRead(), StandardCharsets.UTF_8);
            return new NodeLogResult(content, chunk.normalizedOffset() + chunk.bytesRead(), running);
        } catch (IOException ex) {
            log.warn("Failed to read node log at {}: {}", logPath, ex.getMessage());
            return new NodeLogResult("", offset, running);
        }
    }

    private NodeLogResult readAiNodeLog(Path logPath, long offset, boolean running) {
        try {
            WorkspacePort.ReadChunkResult chunk = workspacePort.readChunk(logPath, offset, NODE_LOG_CHUNK_SIZE);
            if (chunk.normalizedOffset() >= chunk.fileLength()) {
                return new NodeLogResult("", chunk.fileLength(), running);
            }
            if (chunk.bytesRead() <= 0) {
                return new NodeLogResult("", chunk.normalizedOffset(), running);
            }
            int consumedBytes = consumedLineDelimitedBytes(
                    chunk.data(),
                    chunk.bytesRead(),
                    chunk.normalizedOffset() + chunk.bytesRead() >= chunk.fileLength()
            );
            if (consumedBytes <= 0) {
                return new NodeLogResult("", chunk.normalizedOffset(), running);
            }
            String rawChunk = new String(chunk.data(), 0, consumedBytes, StandardCharsets.UTF_8);
            String content = parseAiStreamChunk(rawChunk);
            return new NodeLogResult(content, chunk.normalizedOffset() + consumedBytes, running);
        } catch (IOException ex) {
            log.warn("Failed to read AI node log at {}: {}", logPath, ex.getMessage());
            return new NodeLogResult("", offset, running);
        }
    }

    private int consumedLineDelimitedBytes(byte[] buffer, int readBytes, boolean eof) {
        for (int i = readBytes - 1; i >= 0; i--) {
            if (buffer[i] == '\n') {
                return i + 1;
            }
        }
        return eof ? readBytes : 0;
    }

    private String parseAiStreamChunk(String rawChunk) {
        StringBuilder output = new StringBuilder();
        int start = 0;
        while (start < rawChunk.length()) {
            int newline = rawChunk.indexOf('\n', start);
            int end = newline >= 0 ? newline : rawChunk.length();
            String line = rawChunk.substring(start, end);
            appendAiStreamLine(output, line);
            if (newline < 0) {
                break;
            }
            start = newline + 1;
        }
        return output.toString();
    }

    private void appendAiStreamLine(StringBuilder output, String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            String extracted = extractAiStreamText(root);
            if (extracted != null && !extracted.isEmpty()) {
                output.append(extracted);
            }
        } catch (JsonProcessingException ex) {
            output.append(line).append('\n');
        }
    }

    private String extractAiStreamText(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return "";
        }
        String type = root.path("type").asText("");
        if ("stream_event".equals(type)) {
            return extractStreamEventText(root.path("event"));
        }
        if ("result".equals(type)) {
            return extractFirstText(root.path("result"), root.path("output_text"), root.path("text"));
        }
        if ("error".equals(type)) {
            return extractFirstText(root.path("message"), root.path("error"));
        }
        return "";
    }

    private String extractStreamEventText(JsonNode event) {
        if (event == null || event.isMissingNode() || event.isNull()) {
            return "";
        }
        String eventType = event.path("type").asText("");
        return switch (eventType) {
            case "content_block_delta", "message_delta" -> extractDeltaText(event.path("delta"));
            case "content_block_start" -> extractContentBlockText(event.path("content_block"));
            default -> "";
        };
    }

    private String extractDeltaText(JsonNode delta) {
        if (delta == null || delta.isMissingNode() || delta.isNull()) {
            return "";
        }
        String deltaType = delta.path("type").asText("");
        if ("thinking_delta".equals(deltaType)) {
            return extractFirstText(delta.path("thinking"));
        }
        if ("text_delta".equals(deltaType)) {
            return extractFirstText(delta.path("text"));
        }
        return extractFirstText(
                delta.path("text"),
                delta.path("thinking"),
                delta.path("content"),
                delta.path("output_text"),
                delta.path("result")
        );
    }

    private String extractContentBlockText(JsonNode contentBlock) {
        if (contentBlock == null || contentBlock.isMissingNode() || contentBlock.isNull()) {
            return "";
        }
        String blockType = contentBlock.path("type").asText("");
        if ("thinking".equals(blockType)) {
            return extractFirstText(contentBlock.path("thinking"));
        }
        if ("text".equals(blockType)) {
            return extractFirstText(contentBlock.path("text"));
        }
        return extractFirstText(
                contentBlock.path("text"),
                contentBlock.path("thinking"),
                contentBlock.path("content")
        );
    }

    private String extractFirstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            String value = extractText(node);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String extractText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : node) {
                sb.append(extractText(item));
            }
            return sb.toString();
        }
        if (node.isObject()) {
            return extractFirstText(node.path("text"), node.path("content"), node.path("message"), node.path("output_text"));
        }
        return "";
    }

    private RunEntity getRunEntity(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new NotFoundException("Run not found: " + runId));
    }

    private Path resolveRunWorkspaceRoot(String workspaceRoot, UUID runId) {
        return Path.of(workspaceRoot).resolve(runId.toString()).toAbsolutePath().normalize();
    }

    private Path resolveRunWorkspaceRoot(RunEntity run) {
        String storedRoot = trimToNull(run.getWorkspaceRoot());
        if (storedRoot != null) {
            return Path.of(storedRoot).toAbsolutePath().normalize();
        }
        return resolveRunWorkspaceRoot(settingsService.getWorkspaceRoot(), run.getId());
    }

    private Path resolveRunScopeRoot(Path runWorkspaceRoot) {
        return runWorkspaceRoot.resolve(".hgsdlc").toAbsolutePath().normalize();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

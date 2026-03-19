package ru.hgd.sdlc.runtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.NodeExecutionStatus;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.infrastructure.NodeExecutionRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NodeLogEndpointTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private NodeExecutionRepository nodeExecutionRepository;

    @TempDir
    Path tempDir;

    private String authToken;

    @BeforeEach
    void setUp() throws Exception {
        String loginPayload = objectMapper.writeValueAsString(new LoginPayload("test", "test"));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        authToken = objectMapper.readTree(response).get("token").asText();
    }

    @Test
    void aiNodeLogStreamsParsedDeltasByOffset() throws Exception {
        RunEntity run = createRun();
        NodeExecutionEntity execution = createNodeExecution(run.getId(), "ai-writer", "ai", 1, NodeExecutionStatus.RUNNING);
        Path nodeDir = tempDir.resolve("runtime").resolve("nodes").resolve("ai-writer-attempt-1");
        Files.createDirectories(nodeDir);

        String logContent = """
                {"type":"system","subtype":"init"}
                {"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"П"}}}
                {"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"ривет"}}}
                {"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"text_delta","text":" мир"}}}
                {"type":"result","result":"!"}
                """;
        String tailWithoutNewline = "{\"type\":\"stream_event\",\"event\":{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"?\"}}}";
        Files.writeString(nodeDir.resolve("agent.stdout.log"), logContent + tailWithoutNewline, StandardCharsets.UTF_8);

        String firstResponseBody = mockMvc.perform(get("/api/runs/{runId}/nodes/{nodeExecutionId}/log", run.getId(), execution.getId())
                        .header("Authorization", "Bearer " + authToken)
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Привет мир!"))
                .andExpect(jsonPath("$.running").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstResponse = objectMapper.readTree(firstResponseBody);
        long firstOffset = firstResponse.get("offset").asLong();
        long expectedFirstOffset = (logContent).getBytes(StandardCharsets.UTF_8).length;
        org.junit.jupiter.api.Assertions.assertEquals(expectedFirstOffset, firstOffset);

        mockMvc.perform(get("/api/runs/{runId}/nodes/{nodeExecutionId}/log", run.getId(), execution.getId())
                        .header("Authorization", "Bearer " + authToken)
                        .param("offset", String.valueOf(firstOffset)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("?"))
                .andExpect(jsonPath("$.offset").value((int) (expectedFirstOffset + tailWithoutNewline.getBytes(StandardCharsets.UTF_8).length)))
                .andExpect(jsonPath("$.running").value(true));
    }

    @Test
    void aiNodeLogIncludesNonJsonLines() throws Exception {
        RunEntity run = createRun();
        NodeExecutionEntity execution = createNodeExecution(run.getId(), "ai-writer", "ai", 1, NodeExecutionStatus.SUCCEEDED);
        Path nodeDir = tempDir.resolve("runtime").resolve("nodes").resolve("ai-writer-attempt-1");
        Files.createDirectories(nodeDir);
        Files.writeString(nodeDir.resolve("agent.stdout.log"), "plain stderr-like warning\n", StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/runs/{runId}/nodes/{nodeExecutionId}/log", run.getId(), execution.getId())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("plain stderr-like warning\n"))
                .andExpect(jsonPath("$.running").value(false));
    }

    private RunEntity createRun() {
        RunEntity run = RunEntity.builder()
                .id(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .targetBranch("main")
                .flowCanonicalName("test-flow")
                .flowSnapshotJson("{}")
                .status(RunStatus.RUNNING)
                .currentNodeId("node-1")
                .featureRequest("test feature request")
                .workspaceRoot(tempDir.toString())
                .createdBy("test")
                .createdAt(Instant.now())
                .build();
        return runRepository.save(run);
    }

    private NodeExecutionEntity createNodeExecution(
            UUID runId,
            String nodeId,
            String nodeKind,
            int attemptNo,
            NodeExecutionStatus status
    ) {
        NodeExecutionEntity execution = NodeExecutionEntity.builder()
                .id(UUID.randomUUID())
                .runId(runId)
                .nodeId(nodeId)
                .nodeKind(nodeKind)
                .attemptNo(attemptNo)
                .status(status)
                .startedAt(Instant.now())
                .build();
        return nodeExecutionRepository.save(execution);
    }

    private record LoginPayload(String username, String password) {}
}

package ru.hgd.sdlc.runtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import ru.hgd.sdlc.runtime.domain.ArtifactKind;
import ru.hgd.sdlc.runtime.domain.ArtifactScope;
import ru.hgd.sdlc.runtime.domain.ArtifactVersionEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.domain.RunStatus;
import ru.hgd.sdlc.runtime.infrastructure.ArtifactVersionRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArtifactContentEndpointTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private ArtifactVersionRepository artifactVersionRepository;

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
    void returnsArtifactContent() throws Exception {
        Path artifactFile = createArtifactFile("# Questions\n\n## Q1: What database?\n");
        RunEntity run = createRun();
        ArtifactVersionEntity artifact = createArtifact(run.getId(), "questions", artifactFile.toString());

        mockMvc.perform(get("/api/runs/{runId}/artifacts/{artifactVersionId}/content",
                        run.getId(), artifact.getId())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifact_version_id").value(artifact.getId().toString()))
                .andExpect(jsonPath("$.artifact_key").value("questions"))
                .andExpect(jsonPath("$.content").value("# Questions\n\n## Q1: What database?\n"));
    }

    @Test
    void returns404ForNonExistentArtifact() throws Exception {
        RunEntity run = createRun();
        UUID fakeArtifactId = UUID.randomUUID();

        mockMvc.perform(get("/api/runs/{runId}/artifacts/{artifactVersionId}/content",
                        run.getId(), fakeArtifactId)
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns404ForArtifactFromDifferentRun() throws Exception {
        Path artifactFile = createArtifactFile("content");
        RunEntity run1 = createRun();
        RunEntity run2 = createRun();
        ArtifactVersionEntity artifact = createArtifact(run1.getId(), "doc", artifactFile.toString());

        mockMvc.perform(get("/api/runs/{runId}/artifacts/{artifactVersionId}/content",
                        run2.getId(), artifact.getId())
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isNotFound());
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

    private ArtifactVersionEntity createArtifact(UUID runId, String artifactKey, String filePath) {
        ArtifactVersionEntity artifact = ArtifactVersionEntity.builder()
                .id(UUID.randomUUID())
                .runId(runId)
                .nodeId("node-1")
                .artifactKey(artifactKey)
                .path(filePath)
                .scope(ArtifactScope.RUN)
                .kind(ArtifactKind.PRODUCED)
                .sizeBytes(0L)
                .createdAt(Instant.now())
                .build();
        return artifactVersionRepository.save(artifact);
    }

    private Path createArtifactFile(String content) throws IOException {
        Path file = tempDir.resolve(UUID.randomUUID() + ".md");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private record LoginPayload(String username, String password) {}
}

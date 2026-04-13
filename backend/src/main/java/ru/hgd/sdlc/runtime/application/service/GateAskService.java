package ru.hgd.sdlc.runtime.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hgd.sdlc.common.NotFoundException;
import ru.hgd.sdlc.common.UnprocessableEntityException;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.runtime.application.dto.GateAskResult;
import ru.hgd.sdlc.runtime.application.port.ProcessExecutionPort;
import ru.hgd.sdlc.runtime.application.port.WorkspacePort;
import ru.hgd.sdlc.runtime.domain.GateChatMessageEntity;
import ru.hgd.sdlc.runtime.domain.GateInstanceEntity;
import ru.hgd.sdlc.runtime.domain.NodeExecutionEntity;
import ru.hgd.sdlc.runtime.domain.RunEntity;
import ru.hgd.sdlc.runtime.infrastructure.GateChatMessageRepository;
import ru.hgd.sdlc.runtime.infrastructure.GateInstanceRepository;
import ru.hgd.sdlc.runtime.infrastructure.NodeExecutionRepository;
import ru.hgd.sdlc.runtime.infrastructure.RunRepository;
import ru.hgd.sdlc.settings.application.SettingsService;

@Service
public class GateAskService {

    private static final Logger log = LoggerFactory.getLogger(GateAskService.class);

    private final GateInstanceRepository gateInstanceRepository;
    private final NodeExecutionRepository nodeExecutionRepository;
    private final RunRepository runRepository;
    private final GateChatMessageRepository chatMessageRepository;
    private final ProcessExecutionPort processExecutionPort;
    private final WorkspacePort workspacePort;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final String enTemplate;
    private final String ruTemplate;

    public GateAskService(
            GateInstanceRepository gateInstanceRepository,
            NodeExecutionRepository nodeExecutionRepository,
            RunRepository runRepository,
            GateChatMessageRepository chatMessageRepository,
            ProcessExecutionPort processExecutionPort,
            WorkspacePort workspacePort,
            @Lazy SettingsService settingsService,
            ObjectMapper objectMapper,
            @Value("classpath:runtime/ask-prompt-template.en.md") Resource enTemplateResource,
            @Value("classpath:runtime/ask-prompt-template.ru.md") Resource ruTemplateResource
    ) {
        this.gateInstanceRepository = gateInstanceRepository;
        this.nodeExecutionRepository = nodeExecutionRepository;
        this.runRepository = runRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.processExecutionPort = processExecutionPort;
        this.workspacePort = workspacePort;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.enTemplate = readResource(enTemplateResource);
        this.ruTemplate = readResource(ruTemplateResource);
    }

    public GateAskResult ask(UUID gateId, String question, String selectedDiff) {
        GateInstanceEntity gate = gateInstanceRepository.findById(gateId)
                .orElseThrow(() -> new NotFoundException("Gate not found: " + gateId));

        NodeExecutionEntity nodeExecution = nodeExecutionRepository.findById(gate.getNodeExecutionId())
                .orElseThrow(() -> new NotFoundException("NodeExecution not found: " + gate.getNodeExecutionId()));

        RunEntity run = runRepository.findById(gate.getRunId())
                .orElseThrow(() -> new NotFoundException("Run not found: " + gate.getRunId()));

        String sessionId = nodeExecution.getAgentSessionId();

        List<GateChatMessageEntity> history = chatMessageRepository.findByGateIdOrderByCreatedAtAsc(gateId);

        String nodeInstruction = resolveNodeInstruction(run, nodeExecution.getNodeId());
        String prompt = buildPrompt(nodeInstruction, history, question, selectedDiff);

        Path askRoot = resolveAskRoot(run, gateId);
        UUID askId = UUID.randomUUID();
        Path promptPath = askRoot.resolve(askId + ".prompt.md");
        Path stdoutPath = askRoot.resolve(askId + ".stdout.log");
        Path stderrPath = askRoot.resolve(askId + ".stderr.log");

        try {
            workspacePort.createDirectories(askRoot);
            workspacePort.writeString(promptPath, prompt, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UnprocessableEntityException("Failed to write ask prompt: " + ex.getMessage());
        }

        String codingAgent = resolveCodingAgent(run);
        String command = buildCommand(codingAgent, promptPath, sessionId);
        ProcessExecutionPort.ProcessExecutionResult result;
        try {
            result = processExecutionPort.execute(new ProcessExecutionPort.ProcessExecutionRequest(
                    run.getId(),
                    List.of("bash", "-lc", command),
                    resolveProjectRoot(run),
                    settingsService.getAiTimeoutSeconds(),
                    stdoutPath,
                    stderrPath,
                    false
            ));
        } catch (IOException ex) {
            throw new UnprocessableEntityException("Ask agent execution failed: " + ex.getMessage());
        }

        if (result.exitCode() != 0) {
            log.warn("Ask agent exited with code {} for gate {}", result.exitCode(), gateId);
            throw new UnprocessableEntityException("Ask agent failed with exit code " + result.exitCode());
        }

        String answer = result.stdout() == null ? "" : result.stdout().trim();
        saveMessages(gateId, question, answer);
        return new GateAskResult(answer);
    }

    @Transactional
    public List<GateChatMessageEntity> getChatHistory(UUID gateId) {
        return chatMessageRepository.findByGateIdOrderByCreatedAtAsc(gateId);
    }

    @Transactional
    protected void saveMessages(UUID gateId, String question, String answer) {
        Instant now = Instant.now();
        chatMessageRepository.save(GateChatMessageEntity.builder()
                .id(UUID.randomUUID())
                .gateId(gateId)
                .role("user")
                .content(question)
                .createdAt(now)
                .build());
        chatMessageRepository.save(GateChatMessageEntity.builder()
                .id(UUID.randomUUID())
                .gateId(gateId)
                .role("agent")
                .content(answer)
                .createdAt(now.plusMillis(1))
                .build());
    }

    private String buildPrompt(
            String nodeInstruction,
            List<GateChatMessageEntity> history,
            String question,
            String selectedDiff
    ) {
        String lang = settingsService.getPromptLanguage();
        String template = "ru".equals(lang) ? ruTemplate : enTemplate;

        String chatHistorySection = buildChatHistorySection(history, "ru".equals(lang));
        String selectedDiffSection = buildSelectedDiffSection(selectedDiff, "ru".equals(lang));

        return template
                .replace("{NODE_INSTRUCTION}", nodeInstruction == null ? "" : nodeInstruction)
                .replace("{CHAT_HISTORY_SECTION}", chatHistorySection)
                .replace("{QUESTION}", question == null ? "" : question)
                .replace("{SELECTED_DIFF_SECTION}", selectedDiffSection);
    }

    private String buildChatHistorySection(List<GateChatMessageEntity> history, boolean russian) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(russian ? "## ИСТОРИЯ ДИАЛОГА\n" : "## CONVERSATION SO FAR\n");
        for (GateChatMessageEntity msg : history) {
            String roleLabel = "user".equals(msg.getRole())
                    ? (russian ? "Пользователь" : "User")
                    : (russian ? "Агент" : "Agent");
            sb.append(roleLabel).append(": ").append(msg.getContent()).append("\n\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String buildSelectedDiffSection(String selectedDiff, boolean russian) {
        if (selectedDiff == null || selectedDiff.isBlank()) {
            return "";
        }
        String header = russian
                ? "## ВЫДЕЛЕННЫЙ ФРАГМЕНТ (пользователь спрашивает именно об этом)\n"
                : "## SELECTED CODE FRAGMENT (user is asking about this specifically)\n";
        return header + "```\n" + selectedDiff + "\n```\n\n";
    }

    private String resolveCodingAgent(RunEntity run) {
        try {
            FlowModel flowModel = objectMapper.readValue(run.getFlowSnapshotJson(), FlowModel.class);
            String agent = flowModel.getCodingAgent();
            if (agent != null && !agent.isBlank()) {
                return agent.trim().toLowerCase(java.util.Locale.ROOT);
            }
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse flow snapshot for coding agent resolution, run {}", run.getId());
        }
        return settingsService.getRuntimeCodingAgent();
    }

    private String buildCommand(String codingAgent, Path promptPath, String sessionId) {
        String cmd;
        if ("gigacode".equals(codingAgent)) {
            cmd = "gigacode --output-format text -p " + shellQuote(promptPath.toString());
        } else {
            cmd = "claude --dangerously-skip-permissions --output-format text -p "
                    + shellQuote(promptPath.toString());
        }
        if (sessionId != null && !sessionId.isBlank()) {
            cmd += " --resume " + shellQuote(sessionId);
        }
        return cmd;
    }

    private String resolveNodeInstruction(RunEntity run, String nodeId) {
        try {
            FlowModel flowModel = objectMapper.readValue(run.getFlowSnapshotJson(), FlowModel.class);
            if (flowModel.getNodes() == null) {
                return "";
            }
            return flowModel.getNodes().stream()
                    .filter(n -> nodeId != null && nodeId.equals(n.getId()))
                    .findFirst()
                    .map(NodeModel::getInstruction)
                    .orElse("");
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse flow snapshot for run {}", run.getId());
            return "";
        }
    }

    private Path resolveAskRoot(RunEntity run, UUID gateId) {
        Path runWorkspaceRoot = resolveRunWorkspaceRoot(run);
        return runWorkspaceRoot.resolve(".hgsdlc").resolve("ask").resolve(gateId.toString());
    }

    private Path resolveRunWorkspaceRoot(RunEntity run) {
        String storedRoot = run.getWorkspaceRoot();
        if (storedRoot != null && !storedRoot.isBlank()) {
            return Path.of(storedRoot).toAbsolutePath().normalize();
        }
        return Path.of(settingsService.getWorkspaceRoot())
                .resolve(run.getId().toString())
                .toAbsolutePath()
                .normalize();
    }

    private Path resolveProjectRoot(RunEntity run) {
        return resolveRunWorkspaceRoot(run);
    }

    private String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String readResource(Resource resource) {
        try (var stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load ask prompt template: " + resource.getFilename(), ex);
        }
    }
}

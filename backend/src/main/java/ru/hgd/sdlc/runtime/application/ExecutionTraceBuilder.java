package ru.hgd.sdlc.runtime.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import ru.hgd.sdlc.flow.domain.FlowModel;
import ru.hgd.sdlc.flow.domain.NodeModel;
import ru.hgd.sdlc.runtime.application.dto.CommandResult;

@Service
public class ExecutionTraceBuilder {
    public Map<String, Object> promptPackageBuiltPayload(
            String flowCanonicalName,
            NodeModel node,
            int attemptNo,
            AgentPromptBuilder.AgentPromptPackage promptPackage,
            List<Map<String, Object>> resolvedContext,
            FlowModel flowModel,
            String promptPath,
            String rulesPath,
            String skillsRoot
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("flow_canonical_name", flowCanonicalName);
        payload.put("node_id", node.getId());
        payload.put("attempt_no", attemptNo);
        payload.put("is_start_node", promptPackage.agentInput().startNode());
        payload.put("prompt_checksum", promptPackage.promptChecksum());
        payload.put("agent_input", promptPackage.agentInput());
        payload.put("rendered_prompt", promptPackage.prompt());
        payload.put("resolved_context", resolvedContext);
        payload.put("rule_refs", flowModel.getRuleRefs() == null ? List.of() : flowModel.getRuleRefs());
        payload.put("skill_refs", node.getSkillRefs() == null ? List.of() : node.getSkillRefs());
        payload.put("runtime_files", Map.of(
                "prompt_path", promptPath,
                "rules_path", rulesPath,
                "skills_root", skillsRoot
        ));
        return payload;
    }

    public Map<String, Object> agentInvocationStartedPayload(
            NodeModel node,
            String promptChecksum,
            String workingDirectory,
            List<String> command
    ) {
        return Map.of(
                "node_id", node.getId(),
                "prompt_checksum", promptChecksum,
                "working_directory", workingDirectory,
                "command", command
        );
    }

    public Map<String, Object> agentInvocationFinishedPayload(
            NodeModel node,
            String promptChecksum,
            CommandResult agentResult
    ) {
        return Map.of(
                "node_id", node.getId(),
                "prompt_checksum", promptChecksum,
                "status", "ok",
                "exit_code", agentResult.exitCode(),
                "stdout_path", agentResult.stdoutPath(),
                "stderr_path", agentResult.stderrPath(),
                "stdout", truncate(agentResult.stdout(), 12000),
                "stderr", truncate(agentResult.stderr(), 12000)
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

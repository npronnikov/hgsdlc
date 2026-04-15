package ru.hgd.sdlc.runtime.application;

import org.springframework.stereotype.Component;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;
import ru.hgd.sdlc.runtime.application.port.WorkspacePort;
import ru.hgd.sdlc.settings.application.SettingsService;
import ru.hgd.sdlc.skill.infrastructure.SkillFileRepository;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

@Component
class QwenCodingAgentStrategy extends CodingAgentStrategy {

    public QwenCodingAgentStrategy(RuleVersionRepository ruleVersionRepository, SkillVersionRepository skillVersionRepository, SkillFileRepository skillFileRepository, RuntimeStepTxService runtimeStepTxService, AgentPromptBuilder agentPromptBuilder, CatalogContentResolver catalogContentResolver, WorkspacePort workspacePort, SettingsService settingsService) {
        super(ruleVersionRepository, skillVersionRepository, skillFileRepository, runtimeStepTxService, agentPromptBuilder, catalogContentResolver, workspacePort, settingsService);
    }

    @Override
    public String codingAgent() {
        return "qwen";
    }

}

package ru.hgd.sdlc.platform.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.hgd.sdlc.common.ChecksumUtil;
import ru.hgd.sdlc.flow.domain.FlowStatus;
import ru.hgd.sdlc.flow.domain.FlowVersion;
import ru.hgd.sdlc.flow.infrastructure.FlowVersionRepository;
import ru.hgd.sdlc.project.domain.Project;
import ru.hgd.sdlc.project.domain.ProjectStatus;
import ru.hgd.sdlc.project.infrastructure.ProjectRepository;
import ru.hgd.sdlc.rule.domain.RuleProvider;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;
import ru.hgd.sdlc.skill.domain.SkillProvider;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

@Component
public class SeedCatalogInitializer implements ApplicationRunner {
    private final RuleVersionRepository ruleRepository;
    private final SkillVersionRepository skillRepository;
    private final FlowVersionRepository flowRepository;
    private final ProjectRepository projectRepository;

    public SeedCatalogInitializer(
            RuleVersionRepository ruleRepository,
            SkillVersionRepository skillRepository,
            FlowVersionRepository flowRepository,
            ProjectRepository projectRepository
    ) {
        this.ruleRepository = ruleRepository;
        this.skillRepository = skillRepository;
        this.flowRepository = flowRepository;
        this.projectRepository = projectRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedProjects();
        seedRules();
        seedSkills();
        seedFlows();
    }

    private void seedProjects() {
        String repoUrl = "https://github.com/npronnikov/Logos";
        if (projectRepository.findFirstByRepoUrl(repoUrl).isPresent()) {
            return;
        }
        Instant now = Instant.now();
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName("Logos");
        project.setRepoUrl(repoUrl);
        project.setDefaultBranch("logos");
        project.setStatus(ProjectStatus.ACTIVE);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        projectRepository.save(project);
    }

    private void seedRules() {
        seedRule(
                "project-architecture-rule",
                "1.0",
                RuleStatus.PUBLISHED,
                RuleProvider.QWEN,
                "Project architecture guidance",
                "Describe where to find documentation and architecture materials.",
                """
---
id: project-architecture-rule
version: 1.0
canonical_name: project-architecture-rule@1.0
title: Project architecture guidance
description: Describe where to find documentation and architecture materials.
allowed_paths:
  - docs
  - architecture
  - src
forbidden_paths: []
allowed_commands: []
require_structured_response: true
---

Use `docs/` for product and engineering documentation. Architecture references live in `docs/architecture/` and
`docs/adr/` when present. Use `docs/spec/` for requirements specifications. For source structure, use `src/`.
"""
        );
    }

    private void seedSkills() {
        seedSkill(
                "restore-c4-architecture",
                "1.0",
                SkillStatus.PUBLISHED,
                SkillProvider.QWEN,
                "Restore C4 architecture",
                "Reconstruct C4 architecture view from the codebase.",
                """
---
id: restore-c4-architecture
version: 1.0
canonical_name: restore-c4-architecture@1.0
name: Restore C4 architecture
description: Reconstruct C4 architecture view from the codebase.
---

Analyze the codebase and produce a C4 summary in `docs/c-4.md`.
Include context, containers, and key components with responsibilities.
"""
        );

        seedSkill(
                "restore-sequence-diagrams",
                "1.0",
                SkillStatus.PUBLISHED,
                SkillProvider.QWEN,
                "Restore sequence diagrams",
                "Reconstruct UML sequence diagrams for integrations from the codebase.",
                """
---
id: restore-sequence-diagrams
version: 1.0
canonical_name: restore-sequence-diagrams@1.0
name: Restore sequence diagrams
description: Reconstruct UML sequence diagrams for integrations from the codebase.
---

Analyze integration flows and produce UML sequence diagrams in `docs/sequence.md`.
Focus on external systems, message flows, and error handling.
"""
        );
    }

    private void seedFlows() {
        seedFlow(
                "restore-architecture-flow",
                "1.0",
                FlowStatus.PUBLISHED,
                "Restore architecture flow",
                "Flow to reconstruct architecture artifacts from code.",
                "analyze-architecture",
                "qwen",
                List.of("project-architecture-rule@1.0"),
                """
id: restore-architecture-flow
version: "1.0"
canonical_name: restore-architecture-flow@1.0
title: Restore architecture flow
description: Flow to reconstruct architecture artifacts from code.
status: published
start_node_id: analyze-architecture
rule_refs:
  - project-architecture-rule@1.0
fail_on_missing_declared_output: true
fail_on_missing_expected_mutation: true

nodes:
  - id: analyze-architecture
    title: Analyze codebase architecture
    type: ai
    execution_context:
      - type: user_request
        required: true
    instruction: |
      Reconstruct architecture from the codebase.
      Produce C4 and sequence diagram summaries.
    skill_refs:
      - restore-c4-architecture@1.0
      - restore-sequence-diagrams@1.0
    produced_artifacts:
      - scope: project
        path: docs/c-4.md
        required: true
      - scope: project
        path: docs/sequence.md
        required: true
    expected_mutations: []
    on_success: check-artefacts
    on_failure: complete
  - id: check-artefacts
    title: Check architecture artefacts
    type: human_approval
    execution_context: []
    produced_artifacts: []
    expected_mutations: []
    on_approve: complete
    on_rework:
      keep_changes: true
      next_node: analyze-architecture
  - id: complete
    title: Complete flow
    type: terminal
    execution_context: []
    produced_artifacts: []
    expected_mutations: []
"""
        );
    }

    private void seedRule(
            String ruleId,
            String version,
            RuleStatus status,
            RuleProvider provider,
            String title,
            String description,
            String markdown
    ) {
        if (!ruleRepository.findByRuleIdOrderBySavedAtDesc(ruleId).isEmpty()) {
            return;
        }
        String canonicalName = ruleId + "@" + version;
        RuleVersion entity = new RuleVersion();
        entity.setId(deterministicId("rule", ruleId, version));
        entity.setRuleId(ruleId);
        entity.setVersion(version);
        entity.setCanonicalName(canonicalName);
        entity.setStatus(status);
        entity.setTitle(title);
        entity.setDescription(description);
        entity.setCodingAgent(provider);
        entity.setRuleMarkdown(markdown.trim());
        entity.setChecksum(status == RuleStatus.PUBLISHED ? ChecksumUtil.sha256(markdown) : null);
        entity.setSavedBy("seed");
        entity.setSavedAt(Instant.now());
        entity.setResourceVersion(0L);
        ruleRepository.save(entity);
    }

    private void seedSkill(
            String skillId,
            String version,
            SkillStatus status,
            SkillProvider provider,
            String name,
            String description,
            String markdown
    ) {
        if (!skillRepository.findBySkillIdOrderBySavedAtDesc(skillId).isEmpty()) {
            return;
        }
        String canonicalName = skillId + "@" + version;
        SkillVersion entity = new SkillVersion();
        entity.setId(deterministicId("skill", skillId, version));
        entity.setSkillId(skillId);
        entity.setVersion(version);
        entity.setCanonicalName(canonicalName);
        entity.setStatus(status);
        entity.setName(name);
        entity.setDescription(description);
        entity.setCodingAgent(provider);
        entity.setSkillMarkdown(markdown.trim());
        entity.setChecksum(status == SkillStatus.PUBLISHED ? ChecksumUtil.sha256(markdown) : null);
        entity.setSavedBy("seed");
        entity.setSavedAt(Instant.now());
        entity.setResourceVersion(0L);
        skillRepository.save(entity);
    }

    private void seedFlow(
            String flowId,
            String version,
            FlowStatus status,
            String title,
            String description,
            String startNodeId,
            String codingAgent,
            List<String> ruleRefs,
            String flowYaml
    ) {
        if (!flowRepository.findByFlowIdOrderBySavedAtDesc(flowId).isEmpty()) {
            return;
        }
        String canonicalName = flowId + "@" + version;
        FlowVersion entity = new FlowVersion();
        entity.setId(deterministicId("flow", flowId, version));
        entity.setFlowId(flowId);
        entity.setVersion(version);
        entity.setCanonicalName(canonicalName);
        entity.setStatus(status);
        entity.setTitle(title);
        entity.setDescription(description);
        entity.setStartNodeId(startNodeId);
        entity.setRuleRefs(ruleRefs);
        entity.setCodingAgent(codingAgent);
        entity.setFlowYaml(flowYaml.trim());
        entity.setChecksum(status == FlowStatus.PUBLISHED ? ChecksumUtil.sha256(flowYaml) : null);
        entity.setSavedBy("seed");
        entity.setSavedAt(Instant.now());
        entity.setResourceVersion(0L);
        flowRepository.save(entity);
    }

    private UUID deterministicId(String prefix, String id, String version) {
        String source = prefix + ":" + id + ":" + version;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }
}

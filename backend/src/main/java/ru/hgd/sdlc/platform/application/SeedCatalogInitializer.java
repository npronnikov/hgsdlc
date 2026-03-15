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

    public SeedCatalogInitializer(
            RuleVersionRepository ruleRepository,
            SkillVersionRepository skillRepository,
            FlowVersionRepository flowRepository
    ) {
        this.ruleRepository = ruleRepository;
        this.skillRepository = skillRepository;
        this.flowRepository = flowRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedRules();
        seedSkills();
        seedFlows();
    }

    private void seedRules() {
        seedRule(
                "project-rule",
                "1.0.0",
                RuleStatus.PUBLISHED,
                RuleProvider.PLATFORM_NATIVE,
                "Project baseline rule",
                "Baseline execution constraints for project work.",
                """
---
id: project-rule
version: 1.0.0
canonical_name: project-rule@1.0.0
title: Project baseline rule
description: Baseline execution constraints for project work.
response_schema_id: agent-response-v1
allowed_paths:
  - src
forbidden_paths: []
allowed_commands:
  - mvn test
require_structured_response: true
---

Follow the repository conventions and validate before delivery.
"""
        );

        seedRule(
                "audit-rule",
                "1.0.0",
                RuleStatus.PUBLISHED,
                RuleProvider.CLAUDE,
                "Audit readiness rule",
                "Ensure responses are auditable and assumptions are explicit.",
                """
---
id: audit-rule
version: 1.0.0
canonical_name: audit-rule@1.0.0
title: Audit readiness rule
description: Ensure responses are auditable and assumptions are explicit.
response_schema_id: agent-response-v1
allowed_paths:
  - src
forbidden_paths: []
allowed_commands: []
require_structured_response: true
---

Document assumptions and provide a clear audit trail.
"""
        );

        seedRule(
                "sandbox-rule",
                "0.2",
                RuleStatus.DRAFT,
                RuleProvider.QWEN,
                "Sandbox rule (draft)",
                "Draft rule for sandbox experiments and trials.",
                """
---
id: sandbox-rule
version: 0.2
canonical_name: sandbox-rule@0.2
title: Sandbox rule (draft)
description: Draft rule for sandbox experiments and trials.
response_schema_id: agent-response-v1
allowed_paths:
  - src
forbidden_paths:
  - src/prod
allowed_commands: []
require_structured_response: true
---

Draft rule for sandbox experiments.
"""
        );
    }

    private void seedSkills() {
        seedSkill(
                "feature-intake",
                "1.0.0",
                SkillStatus.PUBLISHED,
                SkillProvider.PLATFORM_NATIVE,
                "Feature intake",
                "Convert requests into structured requirements.",
                """
---
id: feature-intake
version: 1.0.0
canonical_name: feature-intake@1.0.0
name: Feature intake
description: Convert requests into structured requirements.
---

Clarify scope and identify missing details.
"""
        );

        seedSkill(
                "update-requirements",
                "1.0.0",
                SkillStatus.PUBLISHED,
                SkillProvider.CURSOR,
                "Update requirements",
                "Update requirements based on new answers.",
                """
---
id: update-requirements
version: 1.0.0
canonical_name: update-requirements@1.0.0
name: Update requirements
description: Update requirements based on new answers.
---

Rewrite the requirements and list deltas.
"""
        );

        seedSkill(
                "java-spring-coding",
                "0.4",
                SkillStatus.DRAFT,
                SkillProvider.QWEN,
                "Java Spring coding",
                "Draft skill for Java Spring implementations.",
                """
---
id: java-spring-coding
version: 0.4
canonical_name: java-spring-coding@0.4
name: Java Spring coding
description: Draft skill for Java Spring implementations.
---

Use Spring Boot best practices.
"""
        );
    }

    private void seedFlows() {
        seedFlow(
                "feature-change-flow",
                "1.0.0",
                FlowStatus.PUBLISHED,
                "Feature change flow",
                "Flow for feature changes with review.",
                "intake-analysis",
                List.of("project-rule@1.0.0", "audit-rule@1.0.0"),
                """
id: feature-change-flow
version: "1.0.0"
canonical_name: feature-change-flow@1.0.0
title: Feature change flow
description: Flow for feature changes with review.
status: published
start_node_id: intake-analysis
coding_agent: qwen
rule_refs:
  - project-rule@1.0.0
  - audit-rule@1.0.0
fail_on_missing_declared_output: true
fail_on_missing_expected_mutation: true

nodes:
  - id: intake-analysis
    title: Intake analysis
    type: executor
    node_kind: ai
    execution_context:
      - type: user_request
        required: true
    skill_refs:
      - feature-intake@1.0.0
    produced_artifacts: []
    expected_mutations: []
    on_success: collect-answers
    on_failure: close-run
  - id: collect-answers
    title: Collect answers
    type: gate
    node_kind: human_input
    execution_context:
      - type: artifact_ref
        path: .hgwork/{runId}/intake-analysis/artifacts/questions.md
        required: true
    produced_artifacts:
      - path: .hgwork/{runId}/{nodeId}/artifacts/answers.md
        required: true
    expected_mutations: []
    on_submit: process-answers
  - id: process-answers
    title: Process answers
    type: executor
    node_kind: ai
    execution_context:
      - type: user_request
        required: true
    skill_refs:
      - update-requirements@1.0.0
    produced_artifacts: []
    expected_mutations:
      - path: docs/requirements/**
        required: true
    on_success: approve-requirements
    on_failure: close-run
  - id: approve-requirements
    title: Approve requirements
    type: gate
    node_kind: human_approval
    execution_context:
      - type: directory_ref
        path: docs/requirements
        required: true
    produced_artifacts:
      - path: .hgwork/{runId}/{nodeId}/artifacts/approval-comment.md
        required: false
    expected_mutations: []
    on_approve: publish-summary
    on_rework_routes:
      keep_current_changes: process-answers
      discard_current_changes: intake-analysis
  - id: publish-summary
    title: Publish summary
    type: executor
    node_kind: command
    execution_context: []
    produced_artifacts: []
    expected_mutations: []
    on_success: close-run
  - id: close-run
    title: Close run
    type: executor
    node_kind: command
    execution_context: []
    produced_artifacts: []
    expected_mutations: []
    on_success: close-run
"""
        );

        seedFlow(
                "audit-check-flow",
                "1.0.0",
                FlowStatus.PUBLISHED,
                "Audit check flow",
                "Flow for audit readiness checks.",
                "audit-start",
                List.of("audit-rule@1.0.0"),
                """
id: audit-check-flow
version: "1.0.0"
canonical_name: audit-check-flow@1.0.0
title: Audit check flow
description: Flow for audit readiness checks.
status: published
start_node_id: audit-start
coding_agent: qwen
rule_refs:
  - audit-rule@1.0.0

nodes:
  - id: audit-start
    title: Audit start
    type: executor
    node_kind: ai
    execution_context:
      - type: user_request
        required: true
    skill_refs:
      - feature-intake@1.0.0
    produced_artifacts: []
    expected_mutations: []
    on_success: approve-audit
    on_failure: finalize
  - id: approve-audit
    title: Approve audit
    type: gate
    node_kind: human_approval
    execution_context:
      - type: user_request
        required: true
    produced_artifacts: []
    expected_mutations: []
    on_approve: finalize
    on_rework_routes:
      keep_current_changes: audit-start
  - id: finalize
    title: Finalize
    type: executor
    node_kind: command
    execution_context: []
    produced_artifacts: []
    expected_mutations: []
    on_success: finalize
"""
        );

        seedFlow(
                "sandbox-flow",
                "0.2",
                FlowStatus.DRAFT,
                "Sandbox flow",
                "Draft flow for sandbox experiments.",
                "sandbox-start",
                List.of("sandbox-rule@0.2"),
                """
id: sandbox-flow
version: "0.2"
canonical_name: sandbox-flow@0.2
title: Sandbox flow
description: Draft flow for sandbox experiments.
status: draft
start_node_id: sandbox-start
coding_agent: qwen
rule_refs:
  - sandbox-rule@0.2

nodes:
  - id: sandbox-start
    title: Sandbox start
    type: executor
    node_kind: ai
    execution_context:
      - type: user_request
        required: true
    produced_artifacts: []
    expected_mutations: []
    on_success: sandbox-finish
    on_failure: sandbox-finish
  - id: sandbox-finish
    title: Sandbox finish
    type: executor
    node_kind: command
    execution_context: []
    produced_artifacts: []
    expected_mutations: []
    on_success: sandbox-finish
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

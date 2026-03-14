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
                """
---
id: project-rule
version: 1.0.0
canonical_name: project-rule@1.0.0
title: Project baseline rule
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
                """
---
id: audit-rule
version: 1.0.0
canonical_name: audit-rule@1.0.0
title: Audit readiness rule
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
                """
---
id: sandbox-rule
version: 0.2
canonical_name: sandbox-rule@0.2
title: Sandbox rule (draft)
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
                "PRODUCT_OWNER",
                "TECH_APPROVER",
                "intake-analysis",
                List.of("project-rule@1.0.0", "audit-rule@1.0.0"),
                """
id: feature-change-flow
version: 1.0.0
canonical_name: feature-change-flow@1.0.0
title: Feature change flow
description: Flow for feature changes with review.
start_role: PRODUCT_OWNER
approver_role: TECH_APPROVER
start_node_id: intake-analysis
rule_refs:
  - project-rule@1.0.0
  - audit-rule@1.0.0

nodes:
  - id: intake-analysis
    type: executor
    executor_kind: AI
    skill_refs:
      - feature-intake@1.0.0
    on_success: collect-answers
  - id: collect-answers
    type: gate
    gate_kind: human_input
    on_submit: process-answers
  - id: process-answers
    type: executor
    executor_kind: AI
    skill_refs:
      - update-requirements@1.0.0
    on_success: approve-requirements
  - id: approve-requirements
    type: gate
    gate_kind: human_approval
    on_approve: publish-summary
    on_reject: close-run
    on_rework_routes:
      keep_workspace: process-answers
      discard_uncommitted: intake-analysis
  - id: publish-summary
    type: executor
    executor_kind: External Command
    on_success: close-run
  - id: close-run
    type: executor
    executor_kind: External Command
    on_success: close-run
"""
        );

        seedFlow(
                "audit-check-flow",
                "1.0.0",
                FlowStatus.PUBLISHED,
                "Audit check flow",
                "Flow for audit readiness checks.",
                "PRODUCT_OWNER",
                "TECH_APPROVER",
                "audit-start",
                List.of("audit-rule@1.0.0"),
                """
id: audit-check-flow
version: 1.0.0
canonical_name: audit-check-flow@1.0.0
title: Audit check flow
description: Flow for audit readiness checks.
start_role: PRODUCT_OWNER
approver_role: TECH_APPROVER
start_node_id: audit-start
rule_refs:
  - audit-rule@1.0.0

nodes:
  - id: audit-start
    type: executor
    executor_kind: AI
    skill_refs:
      - feature-intake@1.0.0
    on_success: approve-audit
  - id: approve-audit
    type: gate
    gate_kind: human_approval
    on_approve: finalize
    on_reject: finalize
    on_rework_routes:
      keep_workspace: audit-start
  - id: finalize
    type: executor
    executor_kind: External Command
    on_success: finalize
"""
        );

        seedFlow(
                "sandbox-flow",
                "0.2",
                FlowStatus.DRAFT,
                "Sandbox flow",
                "Draft flow for sandbox experiments.",
                "PRODUCT_OWNER",
                "TECH_APPROVER",
                "sandbox-start",
                List.of("sandbox-rule@0.2"),
                """
id: sandbox-flow
version: 0.2
canonical_name: sandbox-flow@0.2
title: Sandbox flow
description: Draft flow for sandbox experiments.
start_role: PRODUCT_OWNER
approver_role: TECH_APPROVER
start_node_id: sandbox-start
rule_refs:
  - sandbox-rule@0.2

nodes:
  - id: sandbox-start
    type: executor
    executor_kind: AI
    on_success: sandbox-finish
  - id: sandbox-finish
    type: executor
    executor_kind: External Command
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
            String startRole,
            String approverRole,
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
        entity.setStartRole(startRole);
        entity.setApproverRole(approverRole);
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

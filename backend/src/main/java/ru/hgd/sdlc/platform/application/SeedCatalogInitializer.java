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
import ru.hgd.sdlc.flow.domain.FlowApprovalStatus;
import ru.hgd.sdlc.flow.domain.FlowContentSource;
import ru.hgd.sdlc.flow.domain.FlowEnvironment;
import ru.hgd.sdlc.flow.domain.FlowLifecycleStatus;
import ru.hgd.sdlc.flow.domain.FlowVisibility;
import ru.hgd.sdlc.flow.infrastructure.FlowVersionRepository;
import ru.hgd.sdlc.project.domain.Project;
import ru.hgd.sdlc.project.domain.ProjectStatus;
import ru.hgd.sdlc.project.infrastructure.ProjectRepository;
import ru.hgd.sdlc.rule.domain.RuleApprovalStatus;
import ru.hgd.sdlc.rule.domain.RuleContentSource;
import ru.hgd.sdlc.rule.domain.RuleEnvironment;
import ru.hgd.sdlc.rule.domain.RuleLifecycleStatus;
import ru.hgd.sdlc.rule.domain.RuleProvider;
import ru.hgd.sdlc.rule.domain.RuleStatus;
import ru.hgd.sdlc.rule.domain.RuleVersion;
import ru.hgd.sdlc.rule.domain.RuleVisibility;
import ru.hgd.sdlc.rule.infrastructure.RuleVersionRepository;
import ru.hgd.sdlc.skill.domain.SkillApprovalStatus;
import ru.hgd.sdlc.skill.domain.SkillContentSource;
import ru.hgd.sdlc.skill.domain.SkillEnvironment;
import ru.hgd.sdlc.skill.domain.SkillLifecycleStatus;
import ru.hgd.sdlc.skill.domain.SkillProvider;
import ru.hgd.sdlc.skill.domain.SkillStatus;
import ru.hgd.sdlc.skill.domain.SkillVersion;
import ru.hgd.sdlc.skill.domain.SkillVisibility;
import ru.hgd.sdlc.skill.infrastructure.SkillVersionRepository;

//@Component
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

        seedSkill(
                "clarify-user-request-questions",
                "1.0",
                SkillStatus.PUBLISHED,
                SkillProvider.QWEN,
                "Clarify request with questions",
                "Ask 5 clarifying questions about the user request with answer options.",
                """
---
id: clarify-user-request-questions
version: 1.0
canonical_name: clarify-user-request-questions@1.0
name: Clarify request with questions
description: Ask 3 clarifying questions about the user request with answer options.
---

You are an assistant that helps clarify a user request for the development system.
Your task is to use the original user request and project analysis to ask EXACTLY 5
clarifying questions that help better understand:
- the goal of the change;
- constraints and context;
- priorities and success criteria;
- quality and UX requirements;
- possible risks and dependencies.

Each question must be specific and useful for refining requirements.
For EACH question, provide 3 plausible answer options, but the user
can enter their own answer in the "User answer:" field.

Output format must be STRICTLY the following (no additional comments, preambles, or explanations):

Question 1: {text of the first question}
Option 1: {first answer option}
Option 2: {second answer option}
Option 3: {third answer option}
User answer:

Question 2: {text of the second question}
Option 1: {first answer option}
Option 2: {second answer option}
Option 3: {third answer option}
User answer:

Question 3: {text of the third question}
Option 1: {first answer option}
Option 2: {second answer option}
Option 3: {third answer option}
User answer:

Follow these rules:
- exactly 3 questions;
- exactly 3 answer options for each question;
- one empty line after each "User answer:" block;
- no additional text outside the required format.
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
    execution_context: []
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

        seedFlow(
                "requirements-from-questions-flow",
                "1.0",
                FlowStatus.PUBLISHED,
                "Clarify request with questions",
                "Test flow: clarify user request with questions and generate requirements.",
                "analyze-request-and-generate-questions",
                "qwen",
                List.of(),
                """
id: requirements-from-questions-flow
version: "1.0"
canonical_name: requirements-from-questions-flow@1.0
title: Clarify request with questions
description: Test flow that clarifies the user request with questions and generates requirements.
status: published
start_node_id: analyze-request-and-generate-questions
rule_refs: []
fail_on_missing_declared_output: true
fail_on_missing_expected_mutation: true

nodes:
  - id: analyze-request-and-generate-questions
    title: Analyze request and generate questions
    type: ai
    execution_context: []
    instruction: |
      Study the project and the original user request.
      Ask clarifying questions relevant to the request and create the `questions.md` artifact in the run-scope working directory.
      Questions should help clarify change goals, constraints, priorities, and success criteria.
      Use the attached skill to generate questions and STRICTLY follow its output format.
    skill_refs:
      - clarify-user-request-questions@1.0
    produced_artifacts:
      - scope: run
        path: questions.md
        required: true
        modifiable: true
    expected_mutations: []
    on_success: answer-questions
    on_failure: complete-requirements-flow

  - id: answer-questions
    title: User answers to questions
    type: human_input
    execution_context:
      - type: artifact_ref
        node_id: analyze-request-and-generate-questions
        path: questions.md
        scope: run
        required: true
    instruction: |
      Open the `questions.md` artifact copy in the current human_input node working directory.
      Fill in your answers directly in this file.
      After you complete your answers and save the file, click "Reply" to continue flow execution.
    produced_artifacts:
      - scope: run
        path: questions.md
        required: true
    expected_mutations: []
    on_submit: execute-request

  - id: execute-request
    title: Generate requirements from request
    type: ai
    execution_context:
      - type: artifact_ref
        node_id: answer-questions
        path: questions.md
        scope: run
        required: true
    instruction: |
      You have the original user request and the updated `questions.md` file with user answers.
      Based on them, produce new detailed requirements for the user-specified topic.
      Requirements must be:
      - specific and verifiable;
      - grouped by themes (functional, non-functional, UX, constraints, and risks);
      - suitable for decomposition into implementation tasks.
      Explicitly account for user answers and infer conclusions even if answers are brief or incomplete.
    skill_refs: []
    produced_artifacts:
      - scope: run
        path: requirements.md
        required: true
    expected_mutations: []
    on_success: complete-requirements-flow
    on_failure: complete-requirements-flow

  - id: complete-requirements-flow
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
        Instant now = Instant.now();
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
        entity.setTeamCode("platform-architecture");
        entity.setPlatformCode("BACK");
        entity.setTags(List.of("architecture", "docs", "java"));
        entity.setRuleKind("architecture");
        entity.setScope("project");
        entity.setEnvironment(RuleEnvironment.PROD);
        entity.setApprovalStatus(RuleApprovalStatus.PUBLISHED);
        entity.setApprovedBy("seed-approver");
        entity.setApprovedAt(now);
        entity.setPublishedAt(now);
        entity.setSourceRef(null);
        entity.setSourcePath(null);
        entity.setContentSource(RuleContentSource.DB);
        entity.setVisibility(RuleVisibility.INTERNAL);
        entity.setLifecycleStatus(RuleLifecycleStatus.ACTIVE);
        entity.setSavedBy("seed");
        entity.setSavedAt(now);
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
        Instant now = Instant.now();
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
        entity.setTeamCode("platform-runtime");
        entity.setPlatformCode("FRONT");
        entity.setTags(List.of("analysis", "architecture", "seed"));
        entity.setSkillKind("analysis");
        entity.setEnvironment(SkillEnvironment.DEV);
        entity.setApprovalStatus(SkillApprovalStatus.PUBLISHED);
        entity.setApprovedBy("seed-approver");
        entity.setApprovedAt(now);
        entity.setPublishedAt(now);
        entity.setSourceRef(null);
        entity.setSourcePath(null);
        entity.setContentSource(SkillContentSource.DB);
        entity.setVisibility(SkillVisibility.INTERNAL);
        entity.setLifecycleStatus(SkillLifecycleStatus.ACTIVE);
        entity.setSavedBy("seed");
        entity.setSavedAt(now);
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
        Instant now = Instant.now();
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
        entity.setTeamCode("platform-runtime");
        entity.setPlatformCode("DATA");
        entity.setTags(List.of("architecture", "orchestration", "seed"));
        entity.setFlowKind("orchestration");
        entity.setRiskLevel("medium");
        entity.setEnvironment(FlowEnvironment.PROD);
        entity.setApprovalStatus(FlowApprovalStatus.PUBLISHED);
        entity.setApprovedBy("seed-approver");
        entity.setApprovedAt(now);
        entity.setPublishedAt(now);
        entity.setSourceRef(null);
        entity.setSourcePath(null);
        entity.setContentSource(FlowContentSource.DB);
        entity.setVisibility(FlowVisibility.INTERNAL);
        entity.setLifecycleStatus(FlowLifecycleStatus.ACTIVE);
        entity.setSavedBy("seed");
        entity.setSavedAt(now);
        entity.setResourceVersion(0L);
        flowRepository.save(entity);
    }

    private UUID deterministicId(String prefix, String id, String version) {
        String source = prefix + ":" + id + ":" + version;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }
}

package ru.hgd.sdlc.compiler.testing;

import ru.hgd.sdlc.compiler.domain.model.authored.ArtifactTemplateDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.ArtifactTemplateId;
import ru.hgd.sdlc.compiler.domain.model.authored.ExecutorKind;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.FlowId;
import ru.hgd.sdlc.compiler.domain.model.authored.GateKind;
import ru.hgd.sdlc.compiler.domain.model.authored.HandlerRef;
import ru.hgd.sdlc.compiler.domain.model.authored.MarkdownBody;
import ru.hgd.sdlc.compiler.domain.model.authored.NodeDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.NodeId;
import ru.hgd.sdlc.compiler.domain.model.authored.NodeType;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseDocument;
import ru.hgd.sdlc.compiler.domain.model.authored.PhaseId;
import ru.hgd.sdlc.compiler.domain.model.authored.Role;
import ru.hgd.sdlc.compiler.domain.model.authored.SemanticVersion;
import ru.hgd.sdlc.compiler.domain.model.authored.Transition;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Factory for creating test FlowDocument instances.
 * Provides pre-configured flow documents for various testing scenarios.
 */
public final class FlowDocumentFixtures implements TestFixture {

    private FlowDocumentFixtures() {
        // Utility class - no instantiation
    }

    /**
     * Creates a minimal valid flow with a single phase and single executor node.
     */
    public static FlowDocument simpleFlow() {
        return FlowDocument.builder()
            .id(FlowId.of("simple-flow"))
            .name("Simple Flow")
            .version(SemanticVersion.of("1.0.0"))
            .description(MarkdownBody.of("A simple flow for testing"))
            .phaseOrder(List.of(PhaseId.of("main")))
            .phases(Map.of(
                PhaseId.of("main"), PhaseDocument.builder()
                    .id(PhaseId.of("main"))
                    .name("Main Phase")
                    .order(0)
                    .nodeOrder(List.of(NodeId.of("execute")))
                    .build()
            ))
            .nodes(Map.of(
                NodeId.of("execute"), NodeDocument.builder()
                    .id(NodeId.of("execute"))
                    .type(NodeType.EXECUTOR)
                    .name("Execute Task")
                    .phaseId(PhaseId.of("main"))
                    .executorKind(ExecutorKind.SKILL)
                    .handler(HandlerRef.builtin("execute"))
                    .build()
            ))
            .startRoles(Set.of(Role.of("developer")))
            .build();
    }

    /**
     * Creates a flow with multiple phases and transitions between them.
     */
    public static FlowDocument multiPhaseFlow() {
        PhaseId devPhase = PhaseId.of("development");
        PhaseId reviewPhase = PhaseId.of("review");
        PhaseId deployPhase = PhaseId.of("deployment");

        NodeId devNode = NodeId.of("develop");
        NodeId reviewNode = NodeId.of("review");
        NodeId deployNode = NodeId.of("deploy");

        return FlowDocument.builder()
            .id(FlowId.of("multi-phase-flow"))
            .name("Multi-Phase Flow")
            .version(SemanticVersion.of("1.0.0"))
            .description(MarkdownBody.of("A flow with multiple sequential phases"))
            .phaseOrder(List.of(devPhase, reviewPhase, deployPhase))
            .phases(Map.of(
                devPhase, PhaseDocument.builder()
                    .id(devPhase)
                    .name("Development")
                    .order(0)
                    .nodeOrder(List.of(devNode))
                    .build(),
                reviewPhase, PhaseDocument.builder()
                    .id(reviewPhase)
                    .name("Code Review")
                    .order(1)
                    .nodeOrder(List.of(reviewNode))
                    .build(),
                deployPhase, PhaseDocument.builder()
                    .id(deployPhase)
                    .name("Deployment")
                    .order(2)
                    .nodeOrder(List.of(deployNode))
                    .build()
            ))
            .nodes(Map.of(
                devNode, NodeDocument.builder()
                    .id(devNode)
                    .type(NodeType.EXECUTOR)
                    .name("Develop Feature")
                    .phaseId(devPhase)
                    .executorKind(ExecutorKind.SKILL)
                    .handler(HandlerRef.builtin("code-generator"))
                    .transitions(List.of(Transition.forward(devNode, reviewNode)))
                    .build(),
                reviewNode, NodeDocument.builder()
                    .id(reviewNode)
                    .type(NodeType.EXECUTOR)
                    .name("Review Code")
                    .phaseId(reviewPhase)
                    .executorKind(ExecutorKind.BUILTIN)
                    .handler(HandlerRef.builtin("lint"))
                    .transitions(List.of(
                        Transition.forward(reviewNode, deployNode),
                        Transition.rework(reviewNode, devNode)
                    ))
                    .build(),
                deployNode, NodeDocument.builder()
                    .id(deployNode)
                    .type(NodeType.EXECUTOR)
                    .name("Deploy")
                    .phaseId(deployPhase)
                    .executorKind(ExecutorKind.BUILTIN)
                    .handler(HandlerRef.builtin("deploy"))
                    .build()
            ))
            .startRoles(Set.of(Role.of("developer"), Role.of("tech_lead")))
            .authoredAt(Instant.parse("2024-01-15T10:00:00Z"))
            .author("test-author")
            .build();
    }

    /**
     * Creates a flow with approval gates.
     */
    public static FlowDocument flowWithGates() {
        PhaseId planPhase = PhaseId.of("planning");
        PhaseId execPhase = PhaseId.of("execution");

        NodeId planNode = NodeId.of("plan");
        NodeId approvalGate = NodeId.of("approval");
        NodeId execNode = NodeId.of("execute");

        return FlowDocument.builder()
            .id(FlowId.of("flow-with-gates"))
            .name("Flow With Gates")
            .version(SemanticVersion.of("1.0.0"))
            .description(MarkdownBody.of("A flow with approval gates"))
            .phaseOrder(List.of(planPhase, execPhase))
            .phases(Map.of(
                planPhase, PhaseDocument.builder()
                    .id(planPhase)
                    .name("Planning Phase")
                    .order(0)
                    .nodeOrder(List.of(planNode))
                    .gateOrder(List.of(approvalGate))
                    .build(),
                execPhase, PhaseDocument.builder()
                    .id(execPhase)
                    .name("Execution Phase")
                    .order(1)
                    .nodeOrder(List.of(execNode))
                    .build()
            ))
            .nodes(Map.of(
                planNode, NodeDocument.builder()
                    .id(planNode)
                    .type(NodeType.EXECUTOR)
                    .name("Create Plan")
                    .phaseId(planPhase)
                    .executorKind(ExecutorKind.BUILTIN)
                    .handler(HandlerRef.builtin("planner"))
                    .transitions(List.of(Transition.forward(planNode, approvalGate)))
                    .build(),
                approvalGate, NodeDocument.builder()
                    .id(approvalGate)
                    .type(NodeType.GATE)
                    .name("Approval Required")
                    .phaseId(planPhase)
                    .gateKind(GateKind.APPROVAL)
                    .requiredApprovers(Set.of(Role.of("tech_lead"), Role.of("product_owner")))
                    .transitions(List.of(Transition.forward(approvalGate, execNode)))
                    .build(),
                execNode, NodeDocument.builder()
                    .id(execNode)
                    .type(NodeType.EXECUTOR)
                    .name("Execute Plan")
                    .phaseId(execPhase)
                    .executorKind(ExecutorKind.SKILL)
                    .handler(HandlerRef.builtin("executor"))
                    .build()
            ))
            .startRoles(Set.of(Role.of("developer")))
            .build();
    }

    /**
     * Creates a flow with various step types (executor, gate, conditional).
     */
    public static FlowDocument flowWithSteps() {
        PhaseId mainPhase = PhaseId.of("main");

        NodeId startNode = NodeId.of("start");
        NodeId conditionalGate = NodeId.of("condition-check");
        NodeId skillStep = NodeId.of("skill-execution");
        NodeId scriptStep = NodeId.of("script-execution");

        return FlowDocument.builder()
            .id(FlowId.of("flow-with-steps"))
            .name("Flow With Various Steps")
            .version(SemanticVersion.of("2.0.0"))
            .description(MarkdownBody.of("A flow demonstrating different step types"))
            .phaseOrder(List.of(mainPhase))
            .phases(Map.of(
                mainPhase, PhaseDocument.builder()
                    .id(mainPhase)
                    .name("Main Phase")
                    .order(0)
                    .nodeOrder(List.of(startNode, skillStep, scriptStep))
                    .gateOrder(List.of(conditionalGate))
                    .build()
            ))
            .nodes(Map.of(
                startNode, NodeDocument.builder()
                    .id(startNode)
                    .type(NodeType.EXECUTOR)
                    .name("Initialize")
                    .phaseId(mainPhase)
                    .executorKind(ExecutorKind.BUILTIN)
                    .handler(HandlerRef.builtin("init"))
                    .transitions(List.of(Transition.forward(startNode, conditionalGate)))
                    .build(),
                conditionalGate, NodeDocument.builder()
                    .id(conditionalGate)
                    .type(NodeType.GATE)
                    .name("Check Conditions")
                    .phaseId(mainPhase)
                    .gateKind(GateKind.CONDITIONAL)
                    .transitions(List.of(
                        Transition.forward(conditionalGate, skillStep, "env == 'production'"),
                        Transition.skip(conditionalGate, scriptStep, "env == 'development'")
                    ))
                    .build(),
                skillStep, NodeDocument.builder()
                    .id(skillStep)
                    .type(NodeType.EXECUTOR)
                    .name("Run Skill")
                    .instructions(MarkdownBody.of("Execute the production deployment skill"))
                    .phaseId(mainPhase)
                    .executorKind(ExecutorKind.SKILL)
                    .handler(HandlerRef.of("skill://deploy-production"))
                    .transitions(List.of(Transition.forward(skillStep, scriptStep)))
                    .build(),
                scriptStep, NodeDocument.builder()
                    .id(scriptStep)
                    .type(NodeType.EXECUTOR)
                    .name("Finalize Script")
                    .phaseId(mainPhase)
                    .executorKind(ExecutorKind.SCRIPT)
                    .handler(HandlerRef.script("scripts/finalize.sh"))
                    .build()
            ))
            .artifacts(Map.of(
                "config", ArtifactTemplateDocument.builder()
                    .id(ArtifactTemplateId.of("config"))
                    .name("Configuration File")
                    .required(true)
                    .build()
            ))
            .startRoles(Set.of(Role.of("developer")))
            .build();
    }

    /**
     * Creates a flow that is intentionally invalid for testing error handling.
     * This flow has:
     * - No phases defined
     * - References to non-existent nodes
     */
    public static FlowDocument invalidFlow() {
        return FlowDocument.builder()
            .id(FlowId.of("invalid-flow"))
            .name("Invalid Flow")
            .version(SemanticVersion.of("0.0.1"))
            .description(MarkdownBody.of("An intentionally invalid flow"))
            .phaseOrder(List.of(PhaseId.of("nonexistent-phase")))
            .phases(Map.of())
            .nodes(Map.of(
                NodeId.of("orphan-node"), NodeDocument.builder()
                    .id(NodeId.of("orphan-node"))
                    .type(NodeType.EXECUTOR)
                    .name("Orphan Node")
                    .phaseId(PhaseId.of("missing-phase"))
                    .executorKind(ExecutorKind.SKILL)
                    .handler(HandlerRef.builtin("test"))
                    .build()
            ))
            .startRoles(Set.of())
            .build();
    }

    /**
     * Creates a flow with artifact bindings.
     */
    public static FlowDocument flowWithArtifacts() {
        PhaseId phase = PhaseId.of("main");
        NodeId inputNode = NodeId.of("input-handler");
        NodeId outputNode = NodeId.of("output-handler");

        return FlowDocument.builder()
            .id(FlowId.of("flow-with-artifacts"))
            .name("Flow With Artifacts")
            .version(SemanticVersion.of("1.0.0"))
            .description(MarkdownBody.of("A flow demonstrating artifact bindings"))
            .phaseOrder(List.of(phase))
            .phases(Map.of(
                phase, PhaseDocument.builder()
                    .id(phase)
                    .name("Main Phase")
                    .order(0)
                    .nodeOrder(List.of(inputNode, outputNode))
                    .build()
            ))
            .nodes(Map.of(
                inputNode, NodeDocument.builder()
                    .id(inputNode)
                    .type(NodeType.EXECUTOR)
                    .name("Process Input")
                    .phaseId(phase)
                    .executorKind(ExecutorKind.BUILTIN)
                    .handler(HandlerRef.builtin("process"))
                    .transitions(List.of(Transition.forward(inputNode, outputNode)))
                    .build(),
                outputNode, NodeDocument.builder()
                    .id(outputNode)
                    .type(NodeType.EXECUTOR)
                    .name("Generate Output")
                    .phaseId(phase)
                    .executorKind(ExecutorKind.BUILTIN)
                    .handler(HandlerRef.builtin("generate"))
                    .build()
            ))
            .startRoles(Set.of(Role.of("developer")))
            .build();
    }

    /**
     * Creates a basic flow builder for custom flow construction.
     */
    public static FlowDocument.FlowDocumentBuilder builder() {
        return FlowDocument.builder()
            .id(FlowId.of("custom-flow"))
            .name("Custom Flow")
            .version(SemanticVersion.of("1.0.0"));
    }
}
